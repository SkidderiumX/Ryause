package omg.depchaivl.ryause.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import omg.depchaivl.ryause.gui.Console;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Reads the official profile repository (a static GitHub repo) and installs profiles from it.
 *
 * <p>The repository only has to serve two kinds of files over plain HTTPS:</p>
 * <pre>
 * index.json
 * profiles/&lt;name&gt;.json
 * </pre>
 *
 * <p>Everything network related happens on a daemon thread. Anything that touches the
 * game state (profiles, chat) is bounced back onto the main thread.</p>
 */
public final class ProfileRepo {
    public enum State {
        IDLE, LOADING, READY, ERROR
    }

    public enum Install {
        NOT_INSTALLED, INSTALLED, UPDATE, DOWNLOADING
    }

    public static final String DEFAULT_REPO = "SkidderiumX/Ryause-Official-Profiles";
    /** Old default before the repository was renamed; migrated on load. */
    private static final String LEGACY_REPO = "SkidderiumX/Ryause-Official-Profile";
    public static final String BRANCH = "main";

    private static final long MAX_INDEX_BYTES = 1024L * 1024L;
    private static final long MAX_PROFILE_BYTES = 256L * 1024L;
    private static final int CONNECT_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 12000;

    public static String repo = DEFAULT_REPO;
    public static State state = State.IDLE;
    public static String error;
    public static String repoName = "";
    public static String updated = "";
    public static boolean stale;
    public static long fetchedAt;
    public static List<RemoteProfile> profiles = new ArrayList<RemoteProfile>();

    private static boolean busy;

    private ProfileRepo() {
    }

    public static File dir() {
        return new File(ClientConfig.DIR, "profiles");
    }

    private static File cacheFile() {
        return new File(ClientConfig.DIR, "profiles-index.json");
    }

    /** Normalises {@code owner/name}, {@code github.com/owner/name} and full URLs. */
    public static String normalize(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        if (v.startsWith("https://github.com/")) {
            v = v.substring("https://github.com/".length());
        } else if (v.startsWith("http://github.com/")) {
            v = v.substring("http://github.com/".length());
        } else if (v.startsWith("github.com/")) {
            v = v.substring("github.com/".length());
        }
        if (v.equalsIgnoreCase(LEGACY_REPO)) {
            v = DEFAULT_REPO;
        }
        return v;
    }

    public static void setRepo(String value) {
        String v = normalize(value);
        if (!v.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            announce("&cRepo must look like owner/name.");
            return;
        }
        repo = v;
        ClientConfig.profilesRepo = v;
        ClientConfig.save();
        state = State.IDLE;
        stale = false;
        error = null;
        profiles = new ArrayList<RemoteProfile>();
        announce("&aProfile repo set to " + v);
    }

    /** Fetches the index. No-op when already loaded unless {@code force} is set. */
    public static void fetch(boolean force) {
        if (busy) {
            return;
        }
        if (!force && state == State.READY) {
            return;
        }
        if (profiles.isEmpty() && loadCache()) {
            stale = true;
        }
        busy = true;
        state = State.LOADING;
        error = null;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String text = downloadText("index.json", MAX_INDEX_BYTES);
                    applyIndex(text);
                    writeCache(text);
                    stale = false;
                    fetchedAt = System.currentTimeMillis();
                    state = State.READY;
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            refreshStates();
                        }
                    });
                } catch (Exception e) {
                    error = describe(e);
                    state = State.ERROR;
                } finally {
                    busy = false;
                }
            }
        }, "Ryause-Profile-Index");
        thread.setDaemon(true);
        thread.start();
    }

    /** Recomputes installed/update state for every catalogue entry. Cheap, main thread only. */
    public static void refreshStates() {
        for (RemoteProfile p : profiles) {
            if (p.downloading) {
                p.install = Install.DOWNLOADING;
                continue;
            }
            File file = new File(dir(), p.fileName());
            if (!file.isFile()) {
                p.install = Install.NOT_INSTALLED;
                continue;
            }
            if (p.sha1.isEmpty()) {
                p.install = Install.INSTALLED;
                continue;
            }
            String hash = sha1(file);
            p.install = hash != null && hash.equalsIgnoreCase(p.sha1) ? Install.INSTALLED : Install.UPDATE;
        }
    }

    public static RemoteProfile find(String query) {
        if (query == null) {
            return null;
        }
        String q = query.trim();
        for (RemoteProfile p : profiles) {
            if (p.file.equalsIgnoreCase(q) || p.title.equalsIgnoreCase(q)) {
                return p;
            }
        }
        for (RemoteProfile p : profiles) {
            if (p.file.toLowerCase().contains(q.toLowerCase()) || p.title.toLowerCase().contains(q.toLowerCase())) {
                return p;
            }
        }
        return null;
    }

    public static Profile local(String name) {
        for (Profile p : Profiles.profiles.keySet()) {
            if (p.name.equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    /** Applies an already installed profile. */
    public static void apply(RemoteProfile p) {
        Profile local = local(p.file);
        if (local == null) {
            announce("&cNot installed yet: " + p.displayTitle());
            return;
        }
        Profiles.enable(local, false);
    }

    /** Downloads (and optionally applies) a profile. */
    public static void install(final RemoteProfile p, final boolean apply) {
        if (p == null || p.downloading) {
            return;
        }
        if (p.file.length() > 14 || !Profiles.validName(p.file)) {
            announce("&cInvalid profile name: " + p.file);
            return;
        }
        p.downloading = true;
        p.install = Install.DOWNLOADING;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                String reason = null;
                try {
                    byte[] data = download("profiles/" + p.file + Profiles.EXT, MAX_PROFILE_BYTES);
                    String expected = p.sha1;
                    if (!expected.isEmpty()) {
                        String hash = sha1(data);
                        if (!hash.equalsIgnoreCase(expected)) {
                            // the CDNs can serve a stale index for a few minutes after a push,
                            // so ask the uncached API for the current hash before giving up
                            String fresh = resolveSha1(p.file);
                            if (fresh != null) {
                                p.sha1 = fresh;
                                expected = fresh;
                            }
                            if (!hash.equalsIgnoreCase(expected)) {
                                throw new IOException("checksum mismatch");
                            }
                        }
                    }
                    File folder = dir();
                    if (!folder.exists() && !folder.mkdirs()) {
                        throw new IOException("could not create profiles folder");
                    }
                    File target = new File(folder, p.fileName());
                    File temp = new File(folder, p.file + ".part");
                    write(temp, data);
                    if (target.exists() && !target.delete()) {
                        throw new IOException("could not replace " + target.getName());
                    }
                    if (!temp.renameTo(target)) {
                        throw new IOException("could not write " + target.getName());
                    }
                    success = true;
                } catch (Exception e) {
                    reason = describe(e);
                }
                final boolean ok = success;
                final String why = reason;
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        p.downloading = false;
                        if (!ok) {
                            p.install = Install.NOT_INSTALLED;
                            refreshStates();
                            announce("&cDownload failed: " + p.displayTitle() + " (" + why + ")");
                            return;
                        }
                        Profiles.loadAll();
                        refreshStates();
                        if (apply) {
                            Profile local = local(p.file);
                            if (local == null) {
                                announce("&cInstalled, but the profile could not be found.");
                            } else {
                                Profiles.enable(local, false);
                            }
                        } else {
                            announce("&aInstalled " + p.displayTitle() + ". Click it again to apply.");
                        }
                    }
                });
            }
        }, "Ryause-Profile-Download");
        thread.setDaemon(true);
        thread.start();
    }

    public static void list() {
        if (state == State.LOADING) {
            Console.print("Fetching profile list...", 1);
            return;
        }
        if (profiles.isEmpty()) {
            fetch(true);
            Console.print("Fetching profile list...", 1);
            return;
        }
        Console.print("&a" + (repoName.isEmpty() ? repo : repoName) + " (" + profiles.size() + ")", 1);
        int n = 0;
        for (RemoteProfile p : profiles) {
            String line = (++n) + ". " + p.displayTitle() + " - " + label(p.install);
            if (!p.author.isEmpty()) {
                line += " by " + p.author;
            }
            Console.print(line, 0);
        }
        Console.print("Use profiles online get <name>.", 0);
    }

    public static String label(Install install) {
        switch (install) {
            case INSTALLED:
                return "installed";
            case UPDATE:
                return "update available";
            case DOWNLOADING:
                return "downloading";
            default:
                return "not installed";
        }
    }

    private static void applyIndex(String text) {
        JsonObject root = new JsonParser().parse(text).getAsJsonObject();
        List<RemoteProfile> list = new ArrayList<RemoteProfile>();
        JsonElement element = root.get("profiles");
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement e : array) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getAsJsonObject();
                String file = string(o, "file").trim();
                if (file.toLowerCase().endsWith(Profiles.EXT)) {
                    file = file.substring(0, file.length() - Profiles.EXT.length());
                }
                // the local name is the file name, so it has to satisfy the same rules
                if (file.isEmpty() || file.length() > 14 || !Profiles.validName(file)) {
                    continue;
                }
                list.add(new RemoteProfile(file, string(o, "title"), string(o, "author"),
                        string(o, "description"), string(o, "mc"), string(o, "sha1")));
            }
        }
        profiles = list;
        repoName = string(root, "name");
        updated = string(root, "updated");
    }

    private static boolean loadCache() {
        File file = cacheFile();
        if (!file.isFile()) {
            return false;
        }
        try {
            applyIndex(new String(read(file, MAX_INDEX_BYTES), StandardCharsets.UTF_8));
            fetchedAt = file.lastModified();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeCache(String text) {
        try {
            write(cacheFile(), text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static String downloadText(String path, long max) throws IOException {
        return new String(download(path, max), StandardCharsets.UTF_8);
    }

    /**
     * Reads the current SHA-1 of a profile straight from the GitHub contents API, which is
     * not behind the raw/jsDelivr CDN cache. Only used after a checksum mismatch, so the
     * unauthenticated rate limit is not a concern.
     */
    private static String resolveSha1(String file) {
        try {
            byte[] raw = get("https://api.github.com/repos/" + repo + "/contents/index.json?ref=" + BRANCH, MAX_INDEX_BYTES);
            JsonObject root = new JsonParser().parse(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            String content = string(root, "content");
            if (content.isEmpty()) {
                return null;
            }
            String json = new String(Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8);
            JsonElement profiles = new JsonParser().parse(json).getAsJsonObject().get("profiles");
            if (profiles == null || !profiles.isJsonArray()) {
                return null;
            }
            for (JsonElement e : profiles.getAsJsonArray()) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getAsJsonObject();
                if (file.equalsIgnoreCase(string(o, "file"))) {
                    String hash = string(o, "sha1").trim();
                    return hash.isEmpty() ? null : hash;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] download(String path, long max) throws IOException {
        IOException first = null;
        try {
            return get("https://raw.githubusercontent.com/" + repo + "/" + BRANCH + "/" + path, max);
        } catch (IOException e) {
            first = e;
        }
        try {
            return get("https://cdn.jsdelivr.net/gh/" + repo + "@" + BRANCH + "/" + path, max);
        } catch (IOException e) {
            throw new IOException(first.getMessage() + " / " + e.getMessage());
        }
    }

    private static byte[] get(String address, long max) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Ryause/b1");
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                long total = 0L;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > max) {
                        throw new IOException("response too large");
                    }
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] read(File file, long max) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0L;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > max) {
                    throw new IOException("file too large");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static void write(File file, byte[] data) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("could not create " + parent.getName());
        }
        try (OutputStream out = new FileOutputStream(file, false)) {
            out.write(data);
        }
    }

    private static String sha1(File file) {
        try {
            return sha1(read(file, MAX_PROFILE_BYTES));
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha1(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit(b >> 4 & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String string(JsonObject o, String key) {
        try {
            JsonElement e = o.get(key);
            return e == null || e.isJsonNull() ? "" : e.getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private static void announce(final String message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                Chat.print(message);
                Console.print(message, 0);
            }
        });
    }
}

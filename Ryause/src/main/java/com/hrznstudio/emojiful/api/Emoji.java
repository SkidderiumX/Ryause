package com.hrznstudio.emojiful.api;

import com.hrznstudio.emojiful.Constants;
import com.hrznstudio.emojiful.config.EmojifulConfig;
import com.hrznstudio.emojiful.util.EmojiUtil;
import net.minecraft.client.Minecraft;
import com.hrznstudio.emojiful.render.SmoothDynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class Emoji {

    public static final ResourceLocation LOADING_TEXTURE = new ResourceLocation(Constants.MOD_ID, "textures/gui/26a0.png");
    public static final ResourceLocation NO_SIGNAL_TEXTURE = new ResourceLocation(Constants.MOD_ID, "textures/gui/26d4.png");
    public static final ResourceLocation ERROR_TEXTURE = new ResourceLocation(Constants.MOD_ID, "textures/gui/26d4.png");

    public static final AtomicInteger threadDownloadCounter = new AtomicInteger(0);

    public String name;
    public List<String> strings = new ArrayList<>();
    public List<String> texts = new ArrayList<>();
    public String location;
    public int version = 1;
    public int sort = 0;
    public boolean worldBased = false;
    public boolean deleteOldTexture;

    // Texture state
    public List<ResourceLocation> frames = new ArrayList<>();
    public boolean finishedLoading = false;
    public boolean loadedTextures = false;

    private String shortString;
    private String regex;
    private Pattern regexPattern;
    private String textRegex;
    private Thread imageThread;
    private Thread gifLoaderThread;

    public void checkLoad() {
        if (imageThread == null && !finishedLoading) {
            loadImage();
        } else if (!loadedTextures) {
            loadedTextures = true;
        }
    }

    public ResourceLocation getResourceLocationForBinding() {
        checkLoad();
        if (deleteOldTexture) {
            // In 1.8.9 we delete the texture from the texture manager
            for (ResourceLocation rl : frames) {
                Minecraft.getMinecraft().getTextureManager().deleteTexture(rl);
            }
            deleteOldTexture = false;
        }
        return finishedLoading && !frames.isEmpty()
                ? frames.get((int) (System.currentTimeMillis() / 10D % frames.size()))
                : LOADING_TEXTURE;
    }

    public boolean test(String s) {
        for (String text : strings) {
            if (s.equalsIgnoreCase(text)) return true;
        }
        return false;
    }

    public boolean worldBased() {
        return worldBased;
    }

    public String getShorterString() {
        if (shortString != null) return shortString;
        shortString = strings.get(0);
        for (String string : strings) {
            if (string.length() < shortString.length()) {
                shortString = string;
            }
        }
        return shortString;
    }

    public Pattern getRegex() {
        if (regexPattern != null) return regexPattern;
        regexPattern = Pattern.compile(getRegexString());
        return regexPattern;
    }

    public String getRegexString() {
        if (regex != null) return regex;
        List<String> processed = new ArrayList<>();
        for (String string : strings) {
            char last = string.toLowerCase().charAt(string.length() - 1);
            String s = string;
            if (last >= 97 && last <= 122) {
                s = string + "\\b";
            }
            char first = string.toLowerCase().charAt(0);
            if (first >= 97 && first <= 122) {
                s = "\\b" + s;
            }
            processed.add(EmojiUtil.cleanStringForRegex(s));
        }
        regex = join(processed, "|");
        return regex;
    }

    public String getTextRegex() {
        if (textRegex != null) return textRegex;
        List<String> processed = new ArrayList<>();
        for (String string : texts) {
            processed.add(EmojiUtil.cleanStringForRegex(string));
        }
        textRegex = "(?<=^|\\s)(" + join(processed, "|") + ")(?=$|\\s)";
        return textRegex;
    }

    private static String join(List<String> list, String delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private void loadImage() {
        File cache = getCache();
        if (cache.exists()) {
            if (getUrl().endsWith(".gif") && EmojifulConfig.loadGifEmojis) {
                if (gifLoaderThread == null) {
                    gifLoaderThread = new Thread("Emojiful GIF Loader #" + threadDownloadCounter.incrementAndGet()) {
                        @Override
                        public void run() {
                            try {
                                loadTextureFrames(EmojiUtil.splitGif(cache));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    gifLoaderThread.setDaemon(true);
                    gifLoaderThread.start();
                }
            } else {
                try {
                    final BufferedImage image = ImageIO.read(cache);
                    final ResourceLocation rl = new ResourceLocation(Constants.MOD_ID,
                            "textures/emoji/" + name.toLowerCase().replaceAll("[^a-z0-9/._-]", "") + "_" + version);
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            SmoothDynamicTexture dynTex = new SmoothDynamicTexture(image);
                            Minecraft.getMinecraft().getTextureManager().loadTexture(rl, dynTex);
                            dynTex.applyFilter();
                        }
                    });
                    frames.add(rl);
                    finishedLoading = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if (imageThread == null) {
            loadTextureFromServer();
        }
    }

    public String getUrl() {
        return "https://raw.githubusercontent.com/InnovativeOnlineIndustries/emojiful-assets/1.20-plus/" + location;
    }

    public File getCache() {
        return new File("emojiful/cache/" + name + "-" + version);
    }

    public void loadTextureFrames(final List<Pair<BufferedImage, Integer>> framesPair) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                int i = 0;
                for (Pair<BufferedImage, Integer> entry : framesPair) {
                    final ResourceLocation rl = new ResourceLocation(Constants.MOD_ID,
                            "textures/emoji/" + name.toLowerCase().replaceAll("[^a-z0-9/._-]", "") + "_" + version + "_frame" + i);
                    SmoothDynamicTexture dynTex = new SmoothDynamicTexture(entry.getKey());
                    Minecraft.getMinecraft().getTextureManager().loadTexture(rl, dynTex);
                    dynTex.applyFilter();
                    for (int j = 0; j < entry.getValue(); j++) {
                        frames.add(rl);
                    }
                    i++;
                }
                finishedLoading = true;
            }
        });
    }

    protected void loadTextureFromServer() {
        imageThread = new Thread("Emojiful Texture Downloader #" + threadDownloadCounter.incrementAndGet()) {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    File cache = getCache();
                    if (cache.getParentFile() != null) cache.getParentFile().mkdirs();
                    conn = (HttpURLConnection) new URL(getUrl()).openConnection(Minecraft.getMinecraft().getProxy());
                    conn.setRequestProperty("User-Agent",
                            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
                    conn.setDoInput(true);
                    conn.setDoOutput(false);
                    conn.connect();
                    if (conn.getResponseCode() / 100 == 2) {
                        FileUtils.copyInputStreamToFile(conn.getInputStream(), cache);
                        finishedLoading = true;
                        loadImage();
                    } else {
                        frames.clear();
                        frames.add(NO_SIGNAL_TEXTURE);
                        deleteOldTexture = true;
                        finishedLoading = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    frames.clear();
                    frames.add(ERROR_TEXTURE);
                    deleteOldTexture = true;
                    finishedLoading = true;
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        };
        imageThread.setDaemon(true);
        imageThread.start();
    }
}

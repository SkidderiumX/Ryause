package com.hrznstudio.emojiful;

import com.google.gson.JsonElement;
import com.hrznstudio.emojiful.api.Emoji;
import com.hrznstudio.emojiful.api.EmojiCategory;
import com.hrznstudio.emojiful.api.EmojiFromTwitmoji;
import com.hrznstudio.emojiful.config.EmojifulConfig;
import com.hrznstudio.emojiful.render.EmojiFontHelper;

import java.util.*;

public class ClientEmojiHandler {

    public static final List<EmojiCategory> CATEGORIES = new ArrayList<>();
    public static List<String> ALL_EMOJIS = new ArrayList<>();
    public static Map<EmojiCategory, List<Emoji[]>> SORTED_EMOJIS_FOR_SELECTION = new LinkedHashMap<>();
    public static List<Emoji> EMOJI_WITH_TEXTS = new ArrayList<>();
    public static int lineAmount;
    private static volatile boolean loading;

    public static synchronized void setup() {
        if (loading || !Constants.EMOJI_LIST.isEmpty()) {
            return;
        }
        loading = true;
        reload();
    }

    /**
     * (Re)load all emoji data from scratch, respecting current config.
     * Safe to call multiple times — clears existing data first.
     * Called at startup and whenever the config GUI is closed.
     */
    public static void reload() {
        // Clear existing data so config changes take effect cleanly
        CATEGORIES.clear();
        ALL_EMOJIS.clear();
        SORTED_EMOJIS_FOR_SELECTION.clear();
        EMOJI_WITH_TEXTS.clear();
        lineAmount = 0;
        Constants.EMOJI_LIST.clear();
        Constants.EMOJI_MAP.clear();
        Constants.error = true;
        // Invalidate the render cache so old emoji positions don't linger
        EmojiFontHelper.RECENT_STRINGS.invalidateAll();

        new Thread("Ryause Emoji Init") {
            @Override
            public void run() {
                try {
                    preInitEmojis();
                    indexEmojis();
                    if (!Constants.EMOJI_LIST.isEmpty()) {
                        Constants.error = false;
                        EmojiFontHelper.RECENT_STRINGS.invalidateAll();
                        Constants.LOG.info("Loaded " + Constants.EMOJI_LIST.size() + " emojis");
                    } else {
                        Constants.LOG.error("No emojis were loaded; check network access.");
                    }
                } finally {
                    loading = false;
                }
            }
        }.start();
    }

    public static void indexEmojis() {
        ALL_EMOJIS = new ArrayList<>();
        for (Emoji emoji : Constants.EMOJI_LIST) {
            ALL_EMOJIS.addAll(emoji.strings);
        }

        SORTED_EMOJIS_FOR_SELECTION = new LinkedHashMap<>();
        lineAmount = 0;
        for (EmojiCategory category : CATEGORIES) {
            ++lineAmount;
            Emoji[] array = new Emoji[9];
            int i = 0;
            List<Emoji> emojisForCat = Constants.EMOJI_MAP.containsKey(category.name())
                    ? Constants.EMOJI_MAP.get(category.name())
                    : new ArrayList<Emoji>();
            for (Emoji emoji : emojisForCat) {
                array[i] = emoji;
                ++i;
                if (i >= array.length) {
                    if (!SORTED_EMOJIS_FOR_SELECTION.containsKey(category)) {
                        SORTED_EMOJIS_FOR_SELECTION.put(category, new ArrayList<Emoji[]>());
                    }
                    SORTED_EMOJIS_FOR_SELECTION.get(category).add(array);
                    array = new Emoji[9];
                    i = 0;
                    ++lineAmount;
                }
            }
            if (i > 0) {
                if (!SORTED_EMOJIS_FOR_SELECTION.containsKey(category)) {
                    SORTED_EMOJIS_FOR_SELECTION.put(category, new ArrayList<Emoji[]>());
                }
                SORTED_EMOJIS_FOR_SELECTION.get(category).add(array);
                ++lineAmount;
            }
        }
    }

    private static void preInitEmojis() {
        if (EmojifulConfig.loadTwemoji) {
            String[] cats = {"Smileys & Emotion", "Animals & Nature", "Food & Drink",
                    "Activities", "Travel & Places", "Objects", "Symbols", "Flags"};
            for (String cat : cats) {
                CATEGORIES.add(new EmojiCategory(cat, false));
            }
            loadTwemojis();
        }
    }

    public static void loadTwemojis() {
        try {
            JsonElement json = CommonClass.readJsonFromUrl(
                    "https://raw.githubusercontent.com/iamcal/emoji-data/master/emoji.json");
            for (JsonElement element : json.getAsJsonArray()) {
                if (element.getAsJsonObject().get("has_img_twitter").getAsBoolean()) {
                    EmojiFromTwitmoji emoji = new EmojiFromTwitmoji();
                    emoji.name = "twemojis_" + element.getAsJsonObject().get("short_name").getAsString();
                    emoji.location = element.getAsJsonObject().get("image").getAsString();
                    emoji.sort = element.getAsJsonObject().get("sort_order").getAsInt();
                    for (JsonElement je : element.getAsJsonObject().get("short_names").getAsJsonArray()) {
                        emoji.strings.add(":" + je.getAsString() + ":");
                    }
                    if (emoji.strings.contains(":face_with_symbols_on_mouth:")) {
                        emoji.strings.add(":swear:");
                    }
                    if (!element.getAsJsonObject().get("texts").isJsonNull()) {
                        for (JsonElement je : element.getAsJsonObject().get("texts").getAsJsonArray()) {
                            emoji.texts.add(je.getAsString());
                        }
                    }
                    String category = element.getAsJsonObject().get("category").getAsString();
                    if (!Constants.EMOJI_MAP.containsKey(category)) {
                        Constants.EMOJI_MAP.put(category, new ArrayList<Emoji>());
                    }
                    Constants.EMOJI_MAP.get(category).add(emoji);
                    Constants.EMOJI_LIST.add(emoji);
                    if (!emoji.texts.isEmpty()) {
                        EMOJI_WITH_TEXTS.add(emoji);
                    }
                }
            }
            Collections.sort(EMOJI_WITH_TEXTS, new Comparator<Emoji>() {
                @Override
                public int compare(Emoji a, Emoji b) {
                    return Integer.compare(a.sort, b.sort);
                }
            });
            for (List<Emoji> emojis : Constants.EMOJI_MAP.values()) {
                Collections.sort(emojis, new Comparator<Emoji>() {
                    @Override
                    public int compare(Emoji a, Emoji b) {
                        return Integer.compare(a.sort, b.sort);
                    }
                });
            }
        } catch (Exception e) {
            Constants.error = true;
            Constants.LOG.error("Error loading Twemojis", e);
        }
    }
}

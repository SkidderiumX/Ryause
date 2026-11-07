package com.hrznstudio.emojiful.config;

import com.hrznstudio.emojiful.ClientEmojiHandler;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class EmojifulConfig {

    public static boolean renderEmoji = true;
    public static boolean showEmojiSelector = true;
    public static boolean showEmojiAutocomplete = true;
    public static boolean loadGifEmojis = true;
    public static boolean shortEmojiReplacement = true;
    public static boolean loadTwemoji = true;
    public static boolean loadCustom = false;
    public static boolean profanityFilter = false;
    public static String profanityFilterReplacement = ":swear:";
    /** If true, use linear (smooth) filtering when rendering emoji textures. */
    public static boolean smoothEmoji = true;

    private static Configuration config;

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    public static void load() {
        config.load();

        renderEmoji = config.getBoolean("enabled", "Emojiful", true,
                "Enable emoji rendering in chat.");
        showEmojiSelector = config.getBoolean("emoji_selector", "Emojiful", true,
                "Enable the emoji selection GUI button in the chat bar.");
        showEmojiAutocomplete = config.getBoolean("emoji_autocomplete", "Emojiful", true,
                "Enable emoji autocomplete suggestions in chat.");
        loadGifEmojis = config.getBoolean("gifs", "Emojiful", true,
                "Load animated emoji (GIFs). If disabled, a static frame is shown.");
        shortEmojiReplacement = config.getBoolean("short_emoji_replacement", "Emojiful", true,
                "Replace :) with :smile: etc. so they render as emoji.");
        smoothEmoji = config.getBoolean("smooth_emoji", "Emojiful", false,
                "Use smooth (linear) filtering when rendering emoji textures. " +
                "This looks bad, I wouldn't use it.");
        loadTwemoji = config.getBoolean("twemoji", "EmojiTypes", true,
                "Load Twemojis (used by Twitter/Discord).");
        loadCustom = config.getBoolean("custom", "EmojiTypes", true,
                "Load custom emojis from the Emojiful asset repo.");
        profanityFilter = config.getBoolean("enabled", "ProfanityFilter", false,
                "Replaces swear words in outgoing messages with :swear:");
        profanityFilterReplacement = config.getString("replacement", "ProfanityFilter", ":swear:",
                "Word used to replace filtered profanity.");

        if (config.hasChanged()) config.save();
    }

    /** True after the mod has fully initialized — prevents reload() being called during preInit. */
    public static boolean initialized = false;

    /** Expose the underlying Configuration for the GUI factory. */
    public static Configuration getConfig() {
        return config;
    }

    /**
     * Re-read all values from the Configuration object after the GUI has written
     * them. Call this from the config GUI's onGuiClosed.
     */
    public static void reload() {
        renderEmoji = config.getBoolean("enabled", "Emojiful", true, "");
        showEmojiSelector = config.getBoolean("emoji_selector", "Emojiful", true, "");
        showEmojiAutocomplete = config.getBoolean("emoji_autocomplete", "Emojiful", true, "");
        loadGifEmojis = config.getBoolean("gifs", "Emojiful", true, "");
        shortEmojiReplacement = config.getBoolean("short_emoji_replacement", "Emojiful", true, "");
        smoothEmoji = config.getBoolean("smooth_emoji", "Emojiful", true, "");
        loadTwemoji = config.getBoolean("twemoji", "EmojiTypes", false, "");
        loadCustom = config.getBoolean("custom", "EmojiTypes", true, "");
        profanityFilter = config.getBoolean("enabled", "ProfanityFilter", false, "");
        profanityFilterReplacement = config.getString("replacement", "ProfanityFilter", ":swear:", "");
        if (config.hasChanged()) config.save();
        // Reload emoji data so twemoji/custom/profanity changes take effect immediately.
        // Guard: don't call during preInit before ClientEmojiHandler.setup() has run.
        if (initialized) ClientEmojiHandler.reload();
    }
}

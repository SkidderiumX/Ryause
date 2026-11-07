package com.hrznstudio.emojiful.render;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.hrznstudio.emojiful.Constants;
import com.hrznstudio.emojiful.api.Emoji;
import com.hrznstudio.emojiful.config.EmojifulConfig;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmojiFontHelper {

    /**
     * Re-entrant guard sentinel — a private-use Unicode char that can NEVER appear
     * in real Minecraft text. We prepend this to segment strings when we call
     * drawString recursively from within the mixin, so we can detect and skip them.
     *
     * Using a single private-use char (U+E000) instead of backslash sequences
     * avoids the "\\\\\" leaking into visible text entirely.
     */
    public static final char GUARD_CHAR = '\uE000';
    public static final String GUARD_PREFIX = String.valueOf(GUARD_CHAR);

    public static LoadingCache<String, Pair<String, HashMap<Integer, Emoji>>> RECENT_STRINGS =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(5, TimeUnit.SECONDS) // short TTL so pre-load misses re-check quickly
                    .build(new CacheLoader<String, Pair<String, HashMap<Integer, Emoji>>>() {
                        @Override
                        public Pair<String, HashMap<Integer, Emoji>> load(String key) {
                            return getEmojiFormattedString(key);
                        }
                    });

    public static Pair<String, HashMap<Integer, Emoji>> getEmojiFormattedString(String text) {
        HashMap<Integer, Emoji> emojis = new LinkedHashMap<>();
        if (!EmojifulConfig.renderEmoji || Constants.error || text == null || text.isEmpty()) {
            return Pair.of(text, emojis);
        }
        // Never process our own recursive guard-prefixed strings
        if (text.charAt(0) == GUARD_CHAR) return Pair.of(text, emojis);

        // Strip MC formatting codes (§X) for matching purposes only
        String unformatted = stripFormatting(text);
        if (unformatted == null || unformatted.isEmpty()) return Pair.of(text, emojis);

        for (Emoji emoji : Constants.EMOJI_LIST) {
            Pattern pattern = emoji.getRegex();
            Matcher matcher = pattern.matcher(unformatted);
            while (matcher.find()) {
                if (!matcher.group().isEmpty()) {
                    String emojiText = matcher.group();
                    int index = text.indexOf(emojiText);
                    emojis.put(index, emoji);
                    // Shift indices of any later emojis left
                    HashMap<Integer, Emoji> shifted = new LinkedHashMap<>();
                    for (Integer key : new ArrayList<>(emojis.keySet())) {
                        if (key > index) {
                            Emoji e = emojis.remove(key);
                            shifted.put(key - emojiText.length() + 1, e);
                        }
                    }
                    emojis.putAll(shifted);
                    unformatted = unformatted.replaceFirst(Pattern.quote(emojiText), "\u2603");
                    text = text.replaceFirst("(?i)" + Pattern.quote(emojiText), "\u2603");
                }
            }
        }
        return Pair.of(text, emojis);
    }

    public static String stripFormatting(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++; // skip the format char that follows §
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

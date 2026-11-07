package com.hrznstudio.emojiful.api;

/**
 * Represents a category of emojis. In 1.8.9 we can't use Java 16 records,
 * so this is a plain class.
 */
public class EmojiCategory {

    private final String name;
    private final boolean worldBased;

    public EmojiCategory(String name, boolean worldBased) {
        this.name = name;
        this.worldBased = worldBased;
    }

    public String name() {
        return name;
    }

    public boolean worldBased() {
        return worldBased;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmojiCategory)) return false;
        EmojiCategory that = (EmojiCategory) o;
        return worldBased == that.worldBased && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (worldBased ? 1 : 0);
        return result;
    }
}

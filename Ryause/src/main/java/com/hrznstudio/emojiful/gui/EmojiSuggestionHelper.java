package com.hrznstudio.emojiful.gui;

import com.google.common.base.Strings;
import com.hrznstudio.emojiful.ClientEmojiHandler;
import com.hrznstudio.emojiful.render.EmojiFontHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emoji autocomplete suggestion helper for 1.8.9.
 *
 * Uses EmojifulChatScreen.getInputField() to avoid reflection.
 */
public class EmojiSuggestionHelper extends IDrawableGuiListener {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("(\\s+)");

    private final EmojifulChatScreen chatScreen;

    private List<String> suggestions = new ArrayList<>();
    private int selectedIndex = 0;
    private String lastInputText = "";

    public EmojiSuggestionHelper(EmojifulChatScreen screen) {
        this.chatScreen = screen;
    }

    private GuiTextField inputField() {
        return chatScreen.getInputField();
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        GuiTextField field = inputField();
        if (field == null) return;

        String current = field.getText();
        if (!current.equals(lastInputText)) {
            lastInputText = current;
            updateSuggestions(current);
        }

        if (suggestions.isEmpty()) return;

        // Use chatScreen.height — the scaled GUI height, updated on every resize
        int screenH = chatScreen.height;
        int height = Math.min(suggestions.size(), 10) * 12;
        int yBase = screenH - 12 - 3 - height;
        int cursorX = Minecraft.getMinecraft().fontRendererObj.getStringWidth(current) + 2;

        for (int i = 0; i < Math.min(suggestions.size(), 10); i++) {
            int pos = (selectedIndex + i) % suggestions.size();
            String suggestion = suggestions.get(pos);
            // Layout: [2px pad] [11px emoji] [2px gap] [text label] [2px pad]
            // Measure text width using GUARD_PREFIX so our mixin measures raw char widths
            int textWidth = Minecraft.getMinecraft().fontRendererObj.getStringWidth(
                    EmojiFontHelper.GUARD_PREFIX + suggestion);
            int totalWidth = 2 + 11 + 2 + textWidth + 2;
            int sx = cursorX;
            int sy = yBase + 12 * i;

            Gui.drawRect(sx, sy, sx + totalWidth, sy + 12, 0xD0000000);
            int textColor = pos == selectedIndex ? 0xFFFFFF00 : 0xFFAAAAAA;
            // Emoji preview at left (rendered by mixin since no GUARD_PREFIX)
            Minecraft.getMinecraft().fontRendererObj.drawString(
                    suggestion, sx + 2, sy + 2, textColor, true);
            // Plain text label to the right of the emoji
            Minecraft.getMinecraft().fontRendererObj.drawString(
                    EmojiFontHelper.GUARD_PREFIX + suggestion, sx + 2 + 11 + 2, sy + 2, textColor, true);
        }
    }

    private void updateSuggestions(String input) {
        suggestions.clear();
        selectedIndex = 0;
        if (Strings.isNullOrEmpty(input)) return;

        int lastWordStart = getLastWordIndex(input);
        String partial;
        if (lastWordStart < input.length()) {
            partial = input.substring(lastWordStart);
        } else {
            partial = input.isEmpty() ? "" : input.substring(0);
        }
        if (!partial.startsWith(":") || partial.length() < 2) return;

        String lowerPartial = partial.toLowerCase(Locale.ROOT);
        for (String emoji : ClientEmojiHandler.ALL_EMOJIS) {
            if (emoji.toLowerCase(Locale.ROOT).startsWith(lowerPartial)) {
                suggestions.add(emoji);
                if (suggestions.size() >= 10) break;
            }
        }
    }

    private static int getLastWordIndex(String s) {
        if (Strings.isNullOrEmpty(s)) return 0;
        int i = 0;
        Matcher m = WHITESPACE_PATTERN.matcher(s);
        while (m.find()) i = m.end();
        return i;
    }

    @Override
    public boolean keyPressed(int keyCode, char typedChar) {
        if (suggestions.isEmpty()) return false;
        // LWJGL2 key codes: UP=200, DOWN=208, TAB=15, ENTER=28, numpad ENTER=156, ESC=1
        if (keyCode == 200) { offsetIndex(-1); return true; }
        if (keyCode == 208) { offsetIndex(1); return true; }
        if (keyCode == 15) { applySuggestion(); return true; }
        if (keyCode == 28 || keyCode == 156) { applySuggestion(); return false; } // false: let Enter send the message
        if (keyCode == 1) { suggestions.clear(); return false; }
        return false;
    }

    private void offsetIndex(int delta) {
        selectedIndex = (selectedIndex + delta + suggestions.size()) % suggestions.size();
    }

    private void applySuggestion() {
        GuiTextField field = inputField();
        if (field == null || suggestions.isEmpty()) return;
        String suggestion = suggestions.get(selectedIndex);
        String current = field.getText();
        int lastWordStart = getLastWordIndex(current);
        field.setText(current.substring(0, lastWordStart) + suggestion);
        field.setCursorPositionEnd();
        suggestions.clear();
    }
}

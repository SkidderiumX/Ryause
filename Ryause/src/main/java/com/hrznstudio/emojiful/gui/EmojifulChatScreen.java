package com.hrznstudio.emojiful.gui;

import com.hrznstudio.emojiful.ClientEmojiHandler;
import com.hrznstudio.emojiful.Constants;
import com.hrznstudio.emojiful.api.Emoji;
import com.hrznstudio.emojiful.config.EmojifulConfig;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom chat screen wrapping GuiChat with emoji selection, autocomplete,
 * short-emoji-replacement on send.
 */
public class EmojifulChatScreen extends GuiChat {

    private EmojiSelectionGui emojiSelectionGui;
    private EmojiSuggestionHelper emojiSuggestionHelper;

    public EmojifulChatScreen(String initialText) {
        super(initialText);
    }

    @Override
    public void initGui() {
        super.initGui();
        emojiSuggestionHelper = null;
        emojiSelectionGui = null;
        if (!Constants.error && EmojifulConfig.renderEmoji) {
            if (EmojifulConfig.showEmojiAutocomplete) {
                emojiSuggestionHelper = new EmojiSuggestionHelper(this);
            }
            if (EmojifulConfig.showEmojiSelector) {
                emojiSelectionGui = new EmojiSelectionGui(this, this.width, this.height);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (emojiSuggestionHelper != null) emojiSuggestionHelper.render(mouseX, mouseY, partialTicks);
        if (emojiSelectionGui != null) {
            emojiSelectionGui.mouseMoved(mouseX, mouseY);
            emojiSelectionGui.render(mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // ENTER (28) or numpad ENTER (156) — intercept to process text before sending
        if ((keyCode == 28 || keyCode == 156) && inputField != null) {
            String text = inputField.getText().trim();
            if (!text.isEmpty()) {
                text = processOutgoingMessage(text);
                inputField.setText(text);
            }
        }
        if (emojiSuggestionHelper != null && emojiSuggestionHelper.keyPressed(keyCode, typedChar)) return;
        if (emojiSelectionGui != null && emojiSelectionGui.keyPressed(keyCode, typedChar)) return;
        super.keyTyped(typedChar, keyCode);
    }

    /**
     * Apply short emoji replacement to outgoing chat text.
     * Called just before the message is sent.
     */
    private String processOutgoingMessage(String text) {
        // Short emoji replacement: e.g. :) → :smile:
        if (EmojifulConfig.shortEmojiReplacement) {
            for (Emoji emoji : ClientEmojiHandler.EMOJI_WITH_TEXTS) {
                if (emoji.texts.isEmpty()) continue;
                try {
                    Pattern p = Pattern.compile(emoji.getTextRegex());
                    Matcher m = p.matcher(text);
                    if (m.find()) {
                        text = m.replaceAll(emoji.getShorterString());
                    }
                } catch (Exception ignored) {}
            }
        }
        return text;
    }

    @Override
    public void handleMouseInput() throws IOException {
        int scroll = org.lwjgl.input.Mouse.getEventDWheel();
        if (scroll != 0 && emojiSelectionGui != null) {
            int mouseX = org.lwjgl.input.Mouse.getEventX() * this.width / mc.displayWidth;
            int mouseY = this.height - org.lwjgl.input.Mouse.getEventY() * this.height / mc.displayHeight - 1;
            int delta = scroll > 0 ? 1 : -1;
            if (emojiSelectionGui.mouseScrolled(mouseX, mouseY, delta)) return;
        }
        super.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (emojiSelectionGui != null && emojiSelectionGui.mouseClicked(mouseX, mouseY, mouseButton)) return;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public GuiTextField getInputField() {
        return inputField;
    }
}

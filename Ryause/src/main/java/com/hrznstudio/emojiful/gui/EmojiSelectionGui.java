package com.hrznstudio.emojiful.gui;

import com.hrznstudio.emojiful.ClientEmojiHandler;
import com.hrznstudio.emojiful.Constants;
import com.hrznstudio.emojiful.api.Emoji;
import com.hrznstudio.emojiful.api.EmojiCategory;
import com.hrznstudio.emojiful.render.EmojiFontHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Emoji selection popup GUI, ported from 1.21 to 1.8.9.
 *
 * Key differences from 1.21:
 * - No GuiGraphics; use Gui.drawRect / Minecraft.getMinecraft().fontRendererObj.drawString directly
 * - No Rect2i; use manual x/y/w/h
 * - Tooltip via GuiScreen.drawHoveringText equivalent
 */
public class EmojiSelectionGui extends IDrawableGuiListener {

    private final GuiChat chatScreen;
    private final GuiTextField fieldWidget;

    // Layout (mirrors 1.21 positions, adapted for 1.8.9 screen)
    private final int openX, openY, openW, openH;           // open button
    private final int selX, selY, selW, selH;               // overall selection panel
    private final int catX, catY, catW, catH;               // category strip (left side)
    private final int infoX, infoY, infoW, infoH;           // info strip (bottom)
    private final int tfX, tfY, tfW, tfH;                   // search text field

    private final int screenW, screenH;
    private final int categoriesPerScroll = 7;

    private int selectionPointer = 1;
    private int categoryPointer = 0;
    private int openSelectionAreaEmoji = -1;
    private boolean showingSelectionArea = false;
    private int lastMouseX, lastMouseY;
    private Emoji lastEmoji;
    private List<Emoji[]> filteredEmojis = new ArrayList<>();

    public EmojiSelectionGui(GuiChat screen, int scaledWidth, int scaledHeight) {
        this.chatScreen = screen;
        this.screenW = scaledWidth;
        this.screenH = scaledHeight;

        if (Constants.EMOJI_MAP.containsKey("Smileys & Emotion")) {
            openSelectionAreaEmoji = new Random().nextInt(Constants.EMOJI_MAP.get("Smileys & Emotion").size());
        }

        openX = screenW - 14;  openY = screenH - 12;  openW = 12;  openH = 12;

        selW = 11 * 12 + 4;    selH = 10 * 11 + 4;
        selX = screenW - 14 - selW;  selY = screenH - 16 - selH;

        catX = selX;  catY = selY + 20;  catW = 22;  catH = selH - 20;
        infoX = selX + catW;  infoY = selY + selH - 20;  infoW = selW - catW;  infoH = 20;

        tfX = selX + 6;  tfY = selY + 6;  tfW = selW - 12;  tfH = 10;

        fieldWidget = new GuiTextField(0, Minecraft.getMinecraft().fontRendererObj,
                tfX, tfY, tfW, tfH);
        fieldWidget.setEnabled(true);
        fieldWidget.setVisible(true);
        fieldWidget.setMaxStringLength(256);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        // Draw open button emoji
        if (openSelectionAreaEmoji != -1 && Constants.EMOJI_MAP.containsKey("Smileys & Emotion")) {
            Emoji btnEmoji = Constants.EMOJI_MAP.get("Smileys & Emotion").get(openSelectionAreaEmoji);
            Minecraft.getMinecraft().fontRendererObj.drawString(
                    btnEmoji.strings.get(0), openX, openY, 0xFFFFFF);
        }

        if (!showingSelectionArea) return;
        if (ClientEmojiHandler.CATEGORIES.isEmpty() || Constants.EMOJI_MAP.isEmpty()) return;

        // Background panels
        drawRect(selX, selY, selX + selW, selY + selH, Integer.MIN_VALUE);
        drawRect(catX, catY, catX + catW, catY + catH, Integer.MIN_VALUE);
        drawRect(infoX, infoY, infoX + infoW, infoY + infoH, Integer.MIN_VALUE);

        // Scrollbar for emoji list
        int emojiScrollY = (int) (((infoY - catY - 5) / (double) getLineAmount()) * selectionPointer);
        drawRect(selX + selW - 2, catY + emojiScrollY, selX + selW - 1, catY + emojiScrollY + 5, 0xff525252);

        // Info area: show hovered emoji name as plain text (GUARD_PREFIX prevents emoji rendering)
        if (lastEmoji != null) {
            Minecraft.getMinecraft().fontRendererObj.drawString(
                    EmojiFontHelper.GUARD_PREFIX + lastEmoji.strings.get(0), infoX + 2, infoY + 6, 0xFFFFFF);
        }

        // Scrollbar for categories
        int maxCatScroll = ClientEmojiHandler.CATEGORIES.size() - Math.min(categoriesPerScroll, Constants.EMOJI_MAP.size());
        if (maxCatScroll > 0) {
            int catScrollY = (int) (((catH - 10) / (double) maxCatScroll) * categoryPointer);
            drawRect(catX + catW - 2, catY + catScrollY + 2, catX + catW - 1, catY + catScrollY + 7, 0xff525252);
        }

        // Category icons (left strip)
        EmojiCategory firstCategory = getCategory(selectionPointer);
        for (int i = 0; i < Math.min(categoriesPerScroll, ClientEmojiHandler.CATEGORIES.size()); i++) {
            int selCat = i + categoryPointer;
            if (selCat < ClientEmojiHandler.CATEGORIES.size()) {
                EmojiCategory category = ClientEmojiHandler.CATEGORIES.get(selCat);
                int rx = catX + 6, ry = catY + 6 + i * 12;

                // Highlight selected category
                if (category.equals(firstCategory)) {
                    drawRect(rx - 1, ry - 2, rx + 11, ry + 10, -2130706433);
                }

                // Draw first emoji of this category as icon
                if (ClientEmojiHandler.SORTED_EMOJIS_FOR_SELECTION.containsKey(category)) {
                    List<Emoji[]> rows = ClientEmojiHandler.SORTED_EMOJIS_FOR_SELECTION.get(category);
                    if (!rows.isEmpty() && rows.get(0)[0] != null) {
                        Minecraft.getMinecraft().fontRendererObj.drawString(
                                rows.get(0)[0].strings.get(0), rx, ry, 0xFFFFFF);
                    }
                }
            }
        }

        // Emoji grid (right side of panel)
        for (int line = 0; line < Math.min(categoriesPerScroll, Constants.EMOJI_MAP.size()) - 1; line++) {
            drawLine(mouseX, mouseY, line * 12f, line + selectionPointer);
        }

        // Search field
        fieldWidget.drawTextBox();
    }

    private void drawLine(int mouseX, int mouseY, float height, int line) {
        Object obj = getLineToDraw(line);
        if (obj == null) return;

        if (obj instanceof EmojiCategory) {
            EmojiCategory cat = (EmojiCategory) obj;
            Minecraft.getMinecraft().fontRendererObj.drawString(
                    cat.name(), catX + catW + 2, (int) (catY + height + 2), 0x969696);
        } else {
            Emoji[] emojis = (Emoji[]) obj;
            for (int i = 0; i < emojis.length; i++) {
                if (emojis[i] != null) {
                    int ex = (int) (catX + catW + 2 + 12f * i);
                    int ey = (int) (catY + height + 2);
                    boolean hovered = mouseX >= ex - 1 && mouseX <= ex + 10 && mouseY >= ey - 1 && mouseY <= ey + 10;
                    if (hovered) {
                        lastEmoji = emojis[i];
                        drawRect(ex - 1, ey - 1, ex + 10, ey + 10, -2130706433);
                    }
                    Minecraft.getMinecraft().fontRendererObj.drawString(
                            emojis[i].strings.get(0), ex, ey, 0x969696);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        // Toggle button
        if (inBounds(mouseX, mouseY, openX, openY, openW, openH)) {
            toggleSelectionArea();
            return true;
        }
        if (!showingSelectionArea) return false;

        // Click in text field area
        if (inBounds(mouseX, mouseY, tfX, tfY, tfW, tfH)) {
            fieldWidget.setFocused(true);
            fieldWidget.mouseClicked(mouseX, mouseY, button);
            return true;
        } else {
            fieldWidget.setFocused(false);
        }

        // Click on category icon
        if (inBounds(mouseX, mouseY, catX, catY, catW, catH)) {
            for (int i = 0; i < Math.min(categoriesPerScroll, ClientEmojiHandler.CATEGORIES.size()); i++) {
                int selCat = i + categoryPointer;
                if (selCat < ClientEmojiHandler.CATEGORIES.size()) {
                    int rx = catX + 6, ry = catY + 6 + i * 12;
                    if (inBounds(mouseX, mouseY, rx, ry, 11, 11)) {
                        EmojiCategory name = ClientEmojiHandler.CATEGORIES.get(selCat);
                        for (int i1 = 0; i1 < getLineAmount(); i1++) {
                            if (name.equals(getCategory(i1))) {
                                selectionPointer = i1;
                                break;
                            }
                        }
                        return true;
                    }
                }
            }
        }

        // Click on emoji
        if (inBounds(mouseX, mouseY, selX, selY, selW, selH)) {
            for (int line = 0; line < Math.min(categoriesPerScroll, Constants.EMOJI_MAP.size()) - 1; line++) {
                Object obj = getLineToDraw(line + selectionPointer);
                if (obj instanceof Emoji[]) {
                    Emoji[] emojis = (Emoji[]) obj;
                    for (int i = 0; i < emojis.length; i++) {
                        if (emojis[i] != null) {
                            int ex = (int) (catX + catW + 2 + 12f * i);
                            int ey = (int) (catY + line * 12 + 2);
                            if (inBounds(mouseX, mouseY, ex, ey - 1, 11, 11)) {
                                appendToChat(emojis[i].getShorterString());
                            }
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, int delta) {
        if (inBounds(mouseX, mouseY, catX, catY, catW, catH)) {
            categoryPointer -= delta;
            categoryPointer = MathHelper.clamp_int(categoryPointer, 0,
                    Math.max(0, ClientEmojiHandler.CATEGORIES.size() - Math.min(categoriesPerScroll, Constants.EMOJI_MAP.size())));
            return true;
        }
        if (inBounds(mouseX, mouseY, selX, selY, selW, selH)) {
            selectionPointer -= delta;
            selectionPointer = MathHelper.clamp_int(selectionPointer, 1, Math.max(1, getLineAmount() - 5));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, char typedChar) {
        if (fieldWidget.isFocused()) {
            fieldWidget.textboxKeyTyped(typedChar, keyCode);
            updateFilter();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char c) {
        if (fieldWidget.isFocused()) {
            fieldWidget.textboxKeyTyped(c, 0);
            updateFilter();
            return true;
        }
        return false;
    }

    private void toggleSelectionArea() {
        // "random.click" is the 1.8.9 button click sound name
        Minecraft.getMinecraft().getSoundHandler().playSound(
                PositionedSoundRecord.create(new ResourceLocation("random.click"), 1.0f));
        showingSelectionArea = !showingSelectionArea;
    }

    private void appendToChat(String text) {
        // EmojifulChatScreen exposes inputField via getInputField()
        if (chatScreen instanceof EmojifulChatScreen) {
            GuiTextField input = ((EmojifulChatScreen) chatScreen).getInputField();
            if (input != null) input.setText(input.getText() + text);
        }
    }

    public void updateFilter() {
        String query = fieldWidget.getText();
        if (!query.isEmpty()) {
            selectionPointer = 1;
            filteredEmojis = new ArrayList<>();
            List<Emoji> matched = new ArrayList<>();
            for (Emoji emoji : Constants.EMOJI_LIST) {
                for (String s : emoji.strings) {
                    if (s.toLowerCase().contains(query.toLowerCase())) {
                        matched.add(emoji);
                        break;
                    }
                }
            }
            Emoji[] array = new Emoji[9];
            int i = 0;
            for (Emoji emoji : matched) {
                array[i++] = emoji;
                if (i >= array.length) {
                    filteredEmojis.add(array);
                    array = new Emoji[9];
                    i = 0;
                }
            }
            if (i > 0) filteredEmojis.add(array);
        }
    }

    public int getLineAmount() {
        return fieldWidget.getText().isEmpty() ? ClientEmojiHandler.lineAmount : filteredEmojis.size();
    }

    public Object getLineToDraw(int line) {
        if (fieldWidget.getText().isEmpty()) {
            for (EmojiCategory category : ClientEmojiHandler.SORTED_EMOJIS_FOR_SELECTION.keySet()) {
                if (--line == 0) return category;
                for (Emoji[] emojis : ClientEmojiHandler.SORTED_EMOJIS_FOR_SELECTION.get(category)) {
                    if (--line == 0) return emojis;
                }
            }
        } else {
            if (line - 1 >= 0 && line - 1 < filteredEmojis.size()) {
                return filteredEmojis.get(line - 1);
            }
        }
        return null;
    }

    public EmojiCategory getCategory(int line) {
        for (EmojiCategory category : ClientEmojiHandler.SORTED_EMOJIS_FOR_SELECTION.keySet()) {
            if (--line == 0) return category;
            for (Emoji[] emojis : ClientEmojiHandler.SORTED_EMOJIS_FOR_SELECTION.get(category)) {
                if (--line == 0) return category;
            }
        }
        return null;
    }

    // --- helpers ---

    private static void drawRect(int x1, int y1, int x2, int y2, int color) {
        net.minecraft.client.gui.Gui.drawRect(x1, y1, x2, y2, color);
    }

    private static boolean inBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}

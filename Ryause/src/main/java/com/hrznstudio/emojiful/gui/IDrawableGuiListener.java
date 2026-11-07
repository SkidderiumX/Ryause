package com.hrznstudio.emojiful.gui;

/**
 * Base class for GUI components that can render, handle key presses, mouse events, etc.
 * In 1.8.9 there is no GuiEventListener interface, so we define our own thin equivalent.
 */
public abstract class IDrawableGuiListener {

    public abstract void render(int mouseX, int mouseY, float partialTicks);

    public boolean keyPressed(int keyCode, char typedChar) {
        return false;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    public void mouseMoved(int mouseX, int mouseY) {
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int delta) {
        return false;
    }

    public boolean charTyped(char c) {
        return false;
    }
}

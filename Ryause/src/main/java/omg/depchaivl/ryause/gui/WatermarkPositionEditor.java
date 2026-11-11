package omg.depchaivl.ryause.gui;

import omg.depchaivl.ryause.module.render.WatermarkModule;
import omg.depchaivl.ryause.util.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.fml.client.config.GuiButtonExt;

public class WatermarkPositionEditor extends HudEditor {
    private static final String HELP = "Edit the watermark position by dragging.";

    private int x = 5;
    private int y = 5;
    private int right;
    private int bottom;
    private boolean dragging;
    private int dragStartX;
    private int dragStartY;
    private int dragFromX;
    private int dragFromY;
    private GuiButtonExt resetButton;

    @Override
    public void initGui() {
        super.initGui();
        this.resetButton = new GuiButtonExt(1, this.width - 90, this.height - 25, 85, 20, "Reset position");
        this.buttonList.add(this.resetButton);
        this.x = WatermarkModule.posX;
        this.y = WatermarkModule.posY;
        this.initPos(this.x, this.y);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.x = WatermarkModule.posX;
        this.y = WatermarkModule.posY;
        WatermarkModule.render();
        float scale = (float) WatermarkModule.scale.getInput();
        this.right = this.x + Math.round(WatermarkModule.contentWidth() * scale);
        this.bottom = this.y + Math.round(WatermarkModule.contentHeight() * scale);
        int helpX = RenderUtils.scaledWidth / 2 - 84;
        int helpY = RenderUtils.scaledHeight / 2 - 20;
        InfoHudEditor.rainbowText(HELP, '-', helpX, helpY, 2L, 0L, true, this.mc.fontRendererObj);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        super.mouseClickMove(mouseX, mouseY, button, time);
        if (button != 0) {
            return;
        }
        if (this.dragging) {
            WatermarkModule.posX = this.dragFromX + (mouseX - this.dragStartX);
            WatermarkModule.posY = this.dragFromY + (mouseY - this.dragStartY);
        } else if (mouseX >= this.x && mouseX <= this.right && mouseY >= this.y && mouseY <= this.bottom) {
            this.dragging = true;
            this.dragStartX = mouseX;
            this.dragStartY = mouseY;
            this.dragFromX = WatermarkModule.posX;
            this.dragFromY = WatermarkModule.posY;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        if (button == 0) {
            this.dragging = false;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == this.resetButton) {
            WatermarkModule.posX = this.x = 5;
            WatermarkModule.posY = this.y = 5;
        }
    }

    @Override
    public void onGuiClosed() {
        this.closed(WatermarkModule.posX, WatermarkModule.posY);
    }
}

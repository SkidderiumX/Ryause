package omg.depchaivl.ryause.module.render;

import com.hrznstudio.emojiful.Constants;
import omg.depchaivl.ryause.client.Overlay;
import omg.depchaivl.ryause.gui.WatermarkPositionEditor;
import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.setting.ButtonSetting;
import omg.depchaivl.ryause.module.setting.SliderSetting;
import omg.depchaivl.ryause.util.RenderUtils;
import omg.depchaivl.ryause.util.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class WatermarkModule extends Module {
    private static final float LOGO_WIDTH = 128.0F;
    private static DynamicTexture logoTexture;
    private static ResourceLocation logoLocation;
    private static int logoWidth;
    private static int logoHeight;
    private static boolean logoLoadAttempted;

    public static int posX = 5, posY = 5;
    public static Theme theme = Theme.DEFAULT;
    public static SliderSetting themeSetting, scale, style;
    public static ButtonSetting editPosition;

    public WatermarkModule(int key) {
        super("Watermark", Category.render, key);
    }

    @Override
    public void link() {
        themeSetting = (SliderSetting) ModuleManager.setting(this, "Theme");
        scale = (SliderSetting) ModuleManager.setting(this, "Scale");
        style = (SliderSetting) ModuleManager.setting(this, "Style");
        editPosition = (ButtonSetting) ModuleManager.setting(this, "Edit position");
    }

    @Override
    public void guiUpdate() {
        theme = Theme.values()[(int) themeSetting.getInput()];
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == editPosition) {
            mc.displayGuiScreen(new WatermarkPositionEditor());
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Overlay.canRender()) {
            render();
        }
    }

    public static void render() {
        float size = (float) scale.getInput();
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) posX, (float) posY, 0.0F);
        GlStateManager.scale(size, size, 1.0F);
        if (style.getInput() == 1.0) {
            renderLogo();
        } else {
            mc.fontRendererObj.drawStringWithShadow("Ryause", 0.0F, 0.0F, theme.getRGB(0.0));
        }
        GlStateManager.popMatrix();
        RenderUtils.resetColor();
    }

    public static float contentWidth() {
        return style.getInput() == 1.0 ? LOGO_WIDTH
                : mc.fontRendererObj.getStringWidth("Ryause");
    }

    public static float contentHeight() {
        return style.getInput() == 1.0 ? logoHeight
                : mc.fontRendererObj.FONT_HEIGHT;
    }

    private static void renderLogo() {
        if (!loadLogo()) {
            return;
        }
        float height = LOGO_WIDTH * logoHeight / logoWidth;
        mc.getTextureManager().bindTexture(logoLocation);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        renderer.pos(0.0, height, 0.0).tex(0.0, 1.0).endVertex();
        renderer.pos(LOGO_WIDTH, height, 0.0).tex(1.0, 1.0).endVertex();
        renderer.pos(LOGO_WIDTH, 0.0, 0.0).tex(1.0, 0.0).endVertex();
        renderer.pos(0.0, 0.0, 0.0).tex(0.0, 0.0).endVertex();
        tessellator.draw();
        GlStateManager.disableBlend();
    }

    private static boolean loadLogo() {
        if (logoLocation != null) {
            return true;
        }
        if (logoLoadAttempted) {
            return false;
        }
        logoLoadAttempted = true;
        try (InputStream input = WatermarkModule.class.getResourceAsStream(
                "/assets/clickgui/watermark-logo.png")) {
            if (input == null) {
                throw new IOException("Missing /assets/clickgui/watermark-logo.png");
            }
            BufferedImage source = ImageIO.read(input);
            if (source == null) {
                throw new IOException("Could not decode /assets/clickgui/watermark-logo.png");
            }
            int left = source.getWidth();
            int top = source.getHeight();
            int right = -1;
            int bottom = -1;
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    if ((source.getRGB(x, y) >>> 24) == 0) {
                        continue;
                    }
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
            if (right < left || bottom < top) {
                throw new IOException("Watermark logo is fully transparent");
            }
            logoWidth = right - left + 1;
            logoHeight = bottom - top + 1;
            BufferedImage cropped = new BufferedImage(logoWidth, logoHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = cropped.createGraphics();
            try {
                graphics.drawImage(source, 0, 0, logoWidth, logoHeight,
                        left, top, right + 1, bottom + 1, null);
            } finally {
                graphics.dispose();
                source.flush();
            }
            logoTexture = new DynamicTexture(cropped);
            logoLocation = mc.getTextureManager().getDynamicTextureLocation("ryause_watermark", logoTexture);
            cropped.flush();
            return true;
        } catch (IOException e) {
            Constants.LOG.error("Could not load the Ryause watermark logo", e);
            return false;
        }
    }
}

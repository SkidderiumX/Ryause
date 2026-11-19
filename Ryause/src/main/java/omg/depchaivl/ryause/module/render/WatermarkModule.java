package omg.depchaivl.ryause.module.render;

import com.hrznstudio.emojiful.Constants;
import omg.depchaivl.ryause.client.Overlay;
import omg.depchaivl.ryause.gui.WatermarkPositionEditor;
import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.combat.KillAuraModule;
import omg.depchaivl.ryause.module.setting.ButtonSetting;
import omg.depchaivl.ryause.module.setting.SliderSetting;
import omg.depchaivl.ryause.client.RenderDispatcher;
import omg.depchaivl.ryause.util.RenderUtils;
import omg.depchaivl.ryause.util.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class WatermarkModule extends Module {
    private static final float LOGO_WIDTH = 128.0F;
    private static final long[] tpsTickSamples = new long[5];
    private static final long[] tpsTimeSamples = new long[5];
    private static final LogoImage CLASSIC_LOGO = new LogoImage(
            "/assets/clickgui/watermark-logo.png", "ryause_watermark_classic");
    private static final LogoImage NGOT_LOGO = new LogoImage(
            "/assets/clickgui/watermark-logo-ngot.png", "ryause_watermark_ngot");
    private static int tpsSampleIndex;
    private static int tpsSampleCount;
    private static long lastWorldTime = -1L;
    private static long lastTimeUpdateAt;

    public static int posX = 5, posY = 5;
    public static Theme theme = Theme.DEFAULT;
    public static SliderSetting themeSetting, scale, style, logoMode;
    public static ButtonSetting editPosition, showFps, showPing, showBps, showTps, showTarget;

    public WatermarkModule(int key) {
        super("Watermark", Category.render, key);
    }

    @Override
    public void link() {
        themeSetting = (SliderSetting) ModuleManager.setting(this, "Theme");
        scale = (SliderSetting) ModuleManager.setting(this, "Scale");
        style = (SliderSetting) ModuleManager.setting(this, "Style");
        logoMode = (SliderSetting) ModuleManager.setting(this, "Logo mode");
        editPosition = (ButtonSetting) ModuleManager.setting(this, "Edit position");
        showFps = (ButtonSetting) ModuleManager.setting(this, "Show FPS");
        showPing = (ButtonSetting) ModuleManager.setting(this, "Show ping");
        showBps = (ButtonSetting) ModuleManager.setting(this, "Show BPS");
        showTps = (ButtonSetting) ModuleManager.setting(this, "Show TPS");
        showTarget = (ButtonSetting) ModuleManager.setting(this, "Show target");
        showFps.visibility = () -> style.getInput() == 0.0;
        showPing.visibility = () -> style.getInput() == 0.0;
        showBps.visibility = () -> style.getInput() == 0.0;
        showTps.visibility = () -> style.getInput() == 0.0;
        showTarget.visibility = () -> style.getInput() == 0.0;
        logoMode.visibility = () -> style.getInput() == 1.0;
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
            mc.fontRendererObj.drawStringWithShadow(displayText(), 0.0F, 0.0F, theme.getRGB(0.0));
        }
        GlStateManager.popMatrix();
        RenderUtils.resetColor();
    }

    public static float contentWidth() {
        if (style.getInput() != 1.0) {
            return mc.fontRendererObj.getStringWidth(displayText());
        }
        return selectedLogo().load() ? LOGO_WIDTH : 0.0F;
    }

    public static float contentHeight() {
        if (style.getInput() != 1.0) {
            return mc.fontRendererObj.FONT_HEIGHT;
        }
        LogoImage logo = selectedLogo();
        return logo.load() ? LOGO_WIDTH * logo.height / logo.width : 0.0F;
    }

    public static String displayText() {
        StringBuilder text = new StringBuilder("Ryause");
        if (showFps.isToggled()) {
            append(text, "Fps: " + Minecraft.getDebugFPS());
        }
        if (showPing.isToggled()) {
            int ping = ping();
            append(text, "Ping: " + (ping < 0 ? "--" : ping + "ms"));
        }
        if (showBps.isToggled()) {
            append(text, "Bps: " + (mc.thePlayer == null ? "--" : RenderDispatcher.bps(mc.thePlayer, 2)));
        }
        if (showTps.isToggled()) {
            append(text, "Tps: " + tpsDisplay());
        }
        if (showTarget.isToggled()) {
            append(text, "Target: " + targetCount());
        }
        return text.toString();
    }

    public static synchronized void recordTimeUpdate(S03PacketTimeUpdate packet) {
        long now = System.currentTimeMillis();
        long worldTime = packet.getTotalWorldTime();
        if (lastWorldTime >= 0L && now > lastTimeUpdateAt && worldTime >= lastWorldTime) {
            tpsTickSamples[tpsSampleIndex] = worldTime - lastWorldTime;
            tpsTimeSamples[tpsSampleIndex] = now - lastTimeUpdateAt;
            tpsSampleIndex = (tpsSampleIndex + 1) % tpsTickSamples.length;
            tpsSampleCount = Math.min(tpsSampleCount + 1, tpsTickSamples.length);
        } else if (lastWorldTime >= 0L) {
            tpsSampleCount = 0;
            tpsSampleIndex = 0;
        }
        lastWorldTime = worldTime;
        lastTimeUpdateAt = now;
    }

    private static void append(StringBuilder text, String value) {
        text.append(" | ").append(value);
    }

    private static int ping() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            return -1;
        }
        net.minecraft.client.network.NetworkPlayerInfo info =
                mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return info == null ? -1 : info.getResponseTime();
    }

    private static int targetCount() {
        return KillAuraModule.instance != null && KillAuraModule.instance.isEnabled()
                && KillAuraModule.hasTarget() ? 1 : 0;
    }

    private static synchronized String tpsDisplay() {
        if (tpsSampleCount == 0 || System.currentTimeMillis() - lastTimeUpdateAt > 3000L) {
            return "--";
        }
        long ticks = 0L;
        long time = 0L;
        for (int i = 0; i < tpsSampleCount; i++) {
            ticks += tpsTickSamples[i];
            time += tpsTimeSamples[i];
        }
        if (time == 0L) {
            return "--";
        }
        return BigDecimal.valueOf(Math.min(20.0, ticks * 1000.0 / time))
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static void renderLogo() {
        LogoImage logo = selectedLogo();
        if (!logo.load()) {
            return;
        }
        float height = LOGO_WIDTH * logo.height / logo.width;
        mc.getTextureManager().bindTexture(logo.location);
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

    private static LogoImage selectedLogo() {
        return logoMode.getInput() == 1.0 ? NGOT_LOGO : CLASSIC_LOGO;
    }

    private static final class LogoImage {
        private final String resourcePath;
        private final String textureName;
        private DynamicTexture texture;
        private ResourceLocation location;
        private int width;
        private int height;
        private boolean loadAttempted;

        private LogoImage(String resourcePath, String textureName) {
            this.resourcePath = resourcePath;
            this.textureName = textureName;
        }

        private boolean load() {
            if (this.location != null) {
                return true;
            }
            if (this.loadAttempted) {
                return false;
            }
            this.loadAttempted = true;
            try (InputStream input = WatermarkModule.class.getResourceAsStream(this.resourcePath)) {
                if (input == null) {
                    throw new IOException("Missing " + this.resourcePath);
                }
                BufferedImage source = ImageIO.read(input);
                if (source == null) {
                    throw new IOException("Could not decode " + this.resourcePath);
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
                    source.flush();
                    throw new IOException("Watermark logo is fully transparent: " + this.resourcePath);
                }
                this.width = right - left + 1;
                this.height = bottom - top + 1;
                BufferedImage cropped = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = cropped.createGraphics();
                try {
                    graphics.drawImage(source, 0, 0, this.width, this.height,
                            left, top, right + 1, bottom + 1, null);
                } finally {
                    graphics.dispose();
                    source.flush();
                }
                this.texture = new DynamicTexture(cropped);
                this.location = mc.getTextureManager().getDynamicTextureLocation(this.textureName, this.texture);
                cropped.flush();
                return true;
            } catch (IOException e) {
                Constants.LOG.error("Could not load the Ryause watermark logo from " + this.resourcePath, e);
                return false;
            }
        }
    }
}

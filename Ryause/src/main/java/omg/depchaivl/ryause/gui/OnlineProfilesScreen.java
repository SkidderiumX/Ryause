package omg.depchaivl.ryause.gui;

import omg.depchaivl.ryause.client.ProfileRepo;
import omg.depchaivl.ryause.client.Profiles;
import omg.depchaivl.ryause.client.RemoteProfile;
import omg.depchaivl.ryause.module.GuiModule;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.util.Blur;
import omg.depchaivl.ryause.util.RenderUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.List;

/**
 * Browsable list of the profiles published in the official repository.
 * Rows install on the first click and apply on the second one.
 */
public class OnlineProfilesScreen extends GuiScreen {
    private static final int PAD = 10;
    private static final int HEADER = 34;
    private static final int FOOTER = 22;
    private static final int ROW = 34;
    private static final int BADGE_W = 54;
    private static final int BADGE_H = 14;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private float scroll;
    private long openedAt;
    private int scaleFactor = 1;

    @Override
    public void initGui() {
        super.initGui();
        this.openedAt = System.currentTimeMillis();
        this.panelW = Math.min(340, this.width - 40);
        this.panelH = Math.min(250, this.height - 40);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;
        this.scaleFactor = new ScaledResolution(this.mc).getScaleFactor();
        if (ProfileRepo.profiles.isEmpty()) {
            ProfileRepo.fetch(false);
        } else {
            ProfileRepo.refreshStates();
        }
        this.clampScroll();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawBackdrop();
        long now = System.currentTimeMillis();
        float fade = clamp((float) (now - this.openedAt) / 180.0f);

        RenderUtils.roundedRect(this.panelX, this.panelY, this.panelX + this.panelW,
                this.panelY + this.panelH, 6.0f, withAlpha(0x101010, (int) (232.0f * fade)));
        RenderUtils.roundedRectBorder(this.panelX, this.panelY, this.panelX + this.panelW,
                this.panelY + this.panelH, 6.0f, 0, withAlpha(0xFFFFFF, (int) (48.0f * fade)),
                withAlpha(0xFFFFFF, (int) (48.0f * fade)));

        this.drawHeader(mouseX, mouseY, fade, now);
        this.drawList(mouseX, mouseY, fade, now);
        this.drawFooter(fade);

        int wy = this.height + 18 - 30;
        Watermark.render(4, wy, false);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawBackdrop() {
        if (GuiModule.darkBackground.isToggled()) {
            drawRect(0, 0, this.width, this.height, -1089466352);
        }
        if (GuiModule.backgroundBlur.getInput() > 0.0) {
            Blur.get().startStencil();
            Gui.drawRect(0, 0, RenderUtils.scaledWidth, RenderUtils.scaledHeight, -1);
            Blur.get().render(2, GuiModule.blurStrength);
        }
    }

    private void drawHeader(int mouseX, int mouseY, float fade, long now) {
        FontRenderer fr = this.fontRendererObj;
        String title = ProfileRepo.repoName.isEmpty() ? "Ryause Official Profiles" : ProfileRepo.repoName;
        fr.drawStringWithShadow(title, this.panelX + PAD, this.panelY + 9,
                withAlpha(0xF2F2F2, (int) (255.0f * fade)));

        int count = ProfileRepo.profiles.size();
        String subtitle = count + (count == 1 ? " profile" : " profiles");
        if (!ProfileRepo.updated.isEmpty()) {
            subtitle += " - updated " + ProfileRepo.updated;
        }
        if (ProfileRepo.stale) {
            subtitle += " - cached";
        }
        fr.drawStringWithShadow(subtitle, this.panelX + PAD, this.panelY + 21,
                withAlpha(0x8A8A8A, (int) (240.0f * fade)));

        int x = this.panelX + this.panelW - PAD - BADGE_W;
        int y = this.panelY + 9;
        boolean hovered = inside(mouseX, mouseY, x, y, BADGE_W, BADGE_H);
        RenderUtils.rect(x, y, x + BADGE_W, y + BADGE_H,
                hovered ? withAlpha(0xFFFFFF, (int) (90.0f * fade)) : withAlpha(0xFFFFFF, (int) (40.0f * fade)));
        String label = ProfileRepo.state == ProfileRepo.State.LOADING ? "..." : "Refresh";
        this.centered(label, x + BADGE_W / 2.0f, y + 3,
                withAlpha(0xE6E6E6, (int) (255.0f * fade)));
    }

    private void drawList(int mouseX, int mouseY, float fade, long now) {
        int left = this.panelX + 8;
        int right = this.panelX + this.panelW - 8;
        int top = this.panelY + HEADER;
        int bottom = this.panelY + this.panelH - FOOTER;
        int height = bottom - top;

        if (ProfileRepo.profiles.isEmpty()) {
            String line;
            int color;
            if (ProfileRepo.state == ProfileRepo.State.ERROR) {
                line = "Could not reach the profile repository.";
                color = withAlpha(0xFF6666, (int) (255.0f * fade));
            } else if (ProfileRepo.state == ProfileRepo.State.LOADING) {
                line = "Fetching profiles" + RenderUtils.dots(280L);
                color = withAlpha(0xC8C8C8, (int) (255.0f * fade));
            } else {
                line = "No profiles published yet.";
                color = withAlpha(0xC8C8C8, (int) (255.0f * fade));
            }
            this.centered(line, (left + right) / 2.0f, top + height / 2.0f - 4, color);
            if (ProfileRepo.state == ProfileRepo.State.ERROR && ProfileRepo.error != null) {
                this.centered(trim(ProfileRepo.error, right - left), (left + right) / 2.0f,
                        top + height / 2.0f + 8, withAlpha(0x8A8A8A, (int) (255.0f * fade)));
            }
            return;
        }

        this.enableScissor(left, top, right - left, height);
        float y = top - this.scroll;
        List<RemoteProfile> list = ProfileRepo.profiles;
        for (int i = 0; i < list.size(); ++i) {
            int rowY = Math.round(y);
            if (rowY + ROW >= top && rowY <= bottom) {
                float rowFade = clamp((float) (now - this.openedAt - i * 22L) / 160.0f);
                this.drawRow(list.get(i), rowY, left, right, mouseX, mouseY, rowFade * fade, i);
            }
            y += ROW;
        }
        this.disableScissor();
    }

    private void drawRow(RemoteProfile p, int y, int left, int right, int mouseX, int mouseY, float fade, int index) {
        FontRenderer fr = this.fontRendererObj;
        boolean hovered = inside(mouseX, mouseY, left, y, right - left, ROW - 1);
        if (hovered) {
            RenderUtils.rect(left, y, right, y + ROW - 1, withAlpha(0xFFFFFF, (int) (34.0f * fade)));
        } else if ((index & 1) == 0) {
            RenderUtils.rect(left, y, right, y + ROW - 1, withAlpha(0xFFFFFF, (int) (10.0f * fade)));
        }

        int bx = right - BADGE_W - 6;
        int by = y + (ROW - BADGE_H) / 2;

        String subtitle = p.author.isEmpty() ? p.description : "by " + p.author + (p.description.isEmpty() ? "" : " - " + p.description);
        if (subtitle.isEmpty()) {
            subtitle = p.file + Profiles.EXT;
        }
        fr.drawStringWithShadow(p.displayTitle(), left + 6, y + 5,
                withAlpha(0xF2F2F2, (int) (255.0f * fade)));
        fr.drawStringWithShadow(trim(subtitle, bx - (left + 6) - 6), left + 6, y + 17,
                withAlpha(0x9A9A9A, (int) (245.0f * fade)));

        this.drawBadge(p, bx, by, hovered, fade);
    }

    private void drawBadge(RemoteProfile p, int x, int y, boolean rowHovered, float fade) {
        String label;
        int accent;
        switch (p.install) {
            case INSTALLED:
                label = "Apply";
                accent = 0x4CAF50;
                break;
            case UPDATE:
                label = "Update";
                accent = 0xFF9800;
                break;
            case DOWNLOADING:
                label = "...";
                accent = 0x808080;
                break;
            default:
                label = "Install";
                accent = 0x2196F3;
                break;
        }
        int alpha = (int) ((rowHovered ? 110.0f : 64.0f) * fade);
        RenderUtils.roundedRect(x, y, x + BADGE_W, y + BADGE_H, 4.0f, withAlpha(accent, alpha));
        this.centered(label, x + BADGE_W / 2.0f, y + 3, withAlpha(accent, (int) (255.0f * fade)));
    }

    private void drawFooter(float fade) {
        int y = this.panelY + this.panelH - 16;
        String left = "ESC to close";
        if (ProfileRepo.state == ProfileRepo.State.LOADING) {
            left = "Fetching" + RenderUtils.dots(280L);
        } else if (ProfileRepo.state == ProfileRepo.State.ERROR && ProfileRepo.error != null) {
            left = trim(ProfileRepo.error, this.panelW - 40);
        }
        this.fontRendererObj.drawStringWithShadow(left, this.panelX + PAD, y,
                withAlpha(0x7A7A7A, (int) (255.0f * fade)));
        String right = ProfileRepo.repo;
        this.fontRendererObj.drawStringWithShadow(right,
                this.panelX + this.panelW - PAD - this.fontRendererObj.getStringWidth(right), y,
                withAlpha(0x6A6A6A, (int) (255.0f * fade)));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button != 0) {
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }
        int rx = this.panelX + this.panelW - PAD - BADGE_W;
        int ry = this.panelY + 9;
        if (inside(mouseX, mouseY, rx, ry, BADGE_W, BADGE_H)) {
            ProfileRepo.fetch(true);
            return;
        }
        int index = this.rowAt(mouseX, mouseY);
        if (index >= 0) {
            RemoteProfile p = ProfileRepo.profiles.get(index);
            if (p.install == ProfileRepo.Install.DOWNLOADING) {
                return;
            }
            if (p.install == ProfileRepo.Install.INSTALLED) {
                ProfileRepo.apply(p);
            } else {
                ProfileRepo.install(p, false);
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            this.scroll -= wheel > 0 ? 18.0f : -18.0f;
            this.clampScroll();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == ModuleManager.gui.getKeycode()) {
            this.mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int rowAt(int mouseX, int mouseY) {
        int left = this.panelX + 8;
        int right = this.panelX + this.panelW - 8;
        int top = this.panelY + HEADER;
        int bottom = this.panelY + this.panelH - FOOTER;
        if (mouseX < left || mouseX > right || mouseY < top || mouseY > bottom) {
            return -1;
        }
        int index = (int) ((mouseY - top + this.scroll) / ROW);
        return index >= 0 && index < ProfileRepo.profiles.size() ? index : -1;
    }

    private void clampScroll() {
        int height = this.panelH - HEADER - FOOTER;
        float max = ProfileRepo.profiles.size() * ROW - height;
        if (max < 0.0f) {
            max = 0.0f;
        }
        if (this.scroll > max) {
            this.scroll = max;
        }
        if (this.scroll < 0.0f) {
            this.scroll = 0.0f;
        }
    }

    private void enableScissor(int x, int y, int width, int height) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * this.scaleFactor, this.mc.displayHeight - (y + height) * this.scaleFactor,
                width * this.scaleFactor, height * this.scaleFactor);
    }

    private void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void centered(String text, float centerX, float y, int color) {
        this.fontRendererObj.drawStringWithShadow(text,
                centerX - this.fontRendererObj.getStringWidth(text) / 2.0f, y, color);
    }

    private String trim(String text, int width) {
        if (this.fontRendererObj.getStringWidth(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        int limit = width - this.fontRendererObj.getStringWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < text.length(); ++i) {
            int w = this.fontRendererObj.getCharWidth(text.charAt(i));
            if (used + w > limit) {
                break;
            }
            used += w;
            sb.append(text.charAt(i));
        }
        return sb.append(ellipsis).toString();
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static int withAlpha(int rgb, int alpha) {
        if (alpha < 0) {
            alpha = 0;
        }
        if (alpha > 255) {
            alpha = 255;
        }
        return rgb & 0xFFFFFF | alpha << 24;
    }

    private static float clamp(float value) {
        return value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
    }
}

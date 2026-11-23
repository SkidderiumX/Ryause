package omg.depchaivl.ryause.gui;

import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.GuiModule;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.util.Animation;
import omg.depchaivl.ryause.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

public class CategoryComponent {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public Category categoryName;
    public float iconScale = 0.5f;
    public float titleHeight;
    public float x;
    public boolean dragging;
    public float width;
    public float iconInverse = 1.0f / this.iconScale;
    public boolean closing;
    public float yOffset;
    public ArrayList<Component> modules = new ArrayList<Component>();
    public boolean opened;
    public float moduleY;
    public float targetScroll;
    public Animation scrollAnimation;
    public float scroll;
    public float xOffset;
    public float bigSettingsHeight;
    public float animatedHeight;
    public float maxHeightRatio = 0.8f;
    public float y;
    public float targetY;
    public boolean opening;
    private static final float OPEN_DURATION_MS = 320.0f;
    private float animationProgress;
    private float animationFrom;
    private long animationStartedAt;
    private boolean animating;

    public CategoryComponent(Category category) {
        this.categoryName = category;
        this.width = 92.0f;
        this.setX(5.0f);
        this.titleHeight = 13.0f;
        this.moduleY = this.titleHeight + 3.0f;
        this.xOffset = 0.0f;
        for (Module mod : ModuleManager.modules) {
            if (mod.moduleCategory() != category) continue;
            this.addModule(mod);
        }
    }

    public void addModule(Module mod) {
        ModuleComponent c = new ModuleComponent(mod, this, this.moduleY);
        this.modules.add(c);
        if (mod.isProfile() || mod.isScript()) {
            ModuleManager.add(mod);
        }
        this.moduleY += c.height;
        this.updateHeight();
    }

    public void removeModule(Module mod, ModuleComponent c) {
        this.modules.remove(c);
        ModuleManager.remove(mod);
        this.moduleY -= c.height;
        this.updateHeight();
    }

    public void setOpened(boolean on) {
        if (on == this.opened) {
            return;
        }
        this.opened = on;
        this.opening = on;
        this.closing = !on;
        this.animationFrom = this.animationProgress;
        this.animationStartedAt = System.currentTimeMillis();
        this.animating = true;
    }

    /**
     * Advances the open/close animation. Time based, so calling it more than once per frame is harmless.
     * Also keeps {@link #animatedHeight} in sync for the Miau column layout.
     */
    public void tickAnimation() {
        float goal = this.opened ? 1.0f : 0.0f;
        if (this.animating) {
            float t = (float) (System.currentTimeMillis() - this.animationStartedAt) / OPEN_DURATION_MS;
            if (t >= 1.0f) {
                this.animationProgress = goal;
                this.animating = false;
                this.opening = false;
                this.closing = false;
            } else {
                this.animationProgress = this.animationFrom + (goal - this.animationFrom) * easeInOutCubic(t);
            }
        } else {
            this.animationProgress = goal;
        }
        this.animatedHeight = this.animationProgress * this.contentHeight();
    }

    private float contentHeight() {
        if (this.modules.isEmpty()) {
            return 0.0f;
        }
        boolean lite = GuiModule.liteMode != null && GuiModule.liteMode.isToggled();
        float height = 2.0f;
        for (Component c : this.modules) {
            if (lite && !((ModuleComponent) c).mod.lite) continue;
            height += c.getHeight();
        }
        this.bigSettingsHeight = height;
        return Math.min(height, (float) RenderUtils.scaledHeight * this.maxHeightRatio);
    }

    private static float easeInOutCubic(float t) {
        if (t <= 0.0f) {
            return 0.0f;
        }
        if (t >= 1.0f) {
            return 1.0f;
        }
        return t < 0.5f ? 4.0f * t * t * t : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 3.0) / 2.0f;
    }

    public void mouseReleased() {
        this.overTitle(false);
        for (Component c : this.modules) {
            c.onGuiClosed();
        }
    }

    public float getY() {
        return this.y;
    }

    public void render(FontRenderer renderer, int mouseX, int mouseY) {
        this.tickAnimation();
        float animated = this.animatedHeight;
        this.width = 92.0f;
        boolean active = false;
        boolean lite = GuiModule.liteMode.isToggled();
        if ((this.opened || this.closing) && !this.modules.isEmpty()) {
            float top = this.y + this.titleHeight + 3.0f;
            int bottom = (int) ((double) top + Math.floor(animated));
            RenderUtils.roundedRectOutlined(this.x - 2.0f, this.y, this.x + this.width + 2.0f, top + animated, 10.0f,
                    GuiModule.categoryFill, GuiModule.outlineLeft, GuiModule.outlineRight);
            GL11.glPushMatrix();
            GL11.glEnable(3089);
            GL11.glScissor((int) ((this.x - 1.0f) * (float) RenderUtils.scaleFactor), mc.displayHeight - bottom * RenderUtils.scaleFactor,
                    (int) ((this.width + 2.0f) * (float) RenderUtils.scaleFactor), (int) (((float) bottom - top) * (float) RenderUtils.scaleFactor));
            GL11.glTranslatef(0.0f, -(1.0f - this.animationProgress) * 6.0f, 0.0f);
            float y = 2.0f;
            for (Component c : this.modules) {
                ModuleComponent m = (ModuleComponent) c;
                if (lite && !m.mod.lite) continue;
                m.render(mouseX, mouseY, y + m.height);
                y += m.getHeight();
            }
            GL11.glDisable(3089);
            GL11.glPopMatrix();
            float max = Math.min(this.bigSettingsHeight, (float) RenderUtils.scaledHeight * this.maxHeightRatio);
            this.targetScroll = Math.max(max - this.bigSettingsHeight, Math.min(0.0f, this.targetScroll));
            if (this.scrollAnimation != null && this.scroll != this.targetScroll) {
                this.scroll = this.scroll > this.targetScroll
                        ? (this.scroll -= this.scrollAnimation.getValue(0.0f, this.scroll - this.targetScroll, 0))
                        : (this.targetScroll == 0.0f
                        ? (this.scroll += this.scrollAnimation.getValue(this.targetScroll, -this.scroll, 0))
                        : this.scrollAnimation.getValue(this.scroll, this.targetScroll, 0));
            }
            this.updateHeight();
            active = true;
        } else if (this.overTitle(mouseX, mouseY)) {
            RenderUtils.roundedRectOutlined(this.x - 2.0f, this.y, this.x + this.width + 2.0f, this.y + this.titleHeight + 3.0f, 10.0f,
                    GuiModule.categoryFill, GuiModule.outlineLeft, GuiModule.outlineRight);
            active = true;
        } else {
            RenderUtils.roundedRectOutlined(this.x - 2.0f, this.y, this.x + this.width + 2.0f, this.y + this.titleHeight + 3.0f, 10.0f,
                    GuiModule.categoryFill, GuiModule.closedOutlineLeft, GuiModule.closedOutlineRight);
        }
        GL11.glPushMatrix();
        float nameX = 13.5f;
        float center = this.getWidth() / 2.0f - (float) mc.fontRendererObj.getStringWidth(this.categoryName.name()) / 2.0f;
        nameX += (center - nameX) * this.animationProgress;
        renderer.drawString(this.categoryName.name(), this.x + nameX, this.y + 4.0f, GuiModule.categoryNameColor.rgb, false);
        RenderUtils.resetColor();
        GlStateManager.scale(this.iconScale, this.iconScale, this.iconScale);
        ItemStack icon = active ? this.categoryName.activeIcon : this.categoryName.icon;
        RenderUtils.drawItem(icon, (this.x + 2.0f) * this.iconInverse, (this.y + 4.0f) * this.iconInverse);
        GL11.glPopMatrix();
    }

    public boolean overCategoryIcon(int x, int y) {
        return (float) x >= this.x && (float) x <= this.x + 11.0f && (float) y >= this.y + 2.0f && (float) y <= this.y + this.titleHeight + 1.0f;
    }

    /** Top of the scrollable content area, right below the title bar. */
    public float contentTop() {
        return this.y + this.titleHeight + 3.0f;
    }

    /** Bottom of the currently visible (clipped) content area. */
    public float visibleBottom() {
        return this.contentTop() + Math.max(0.0f, this.animatedHeight);
    }

    /**
     * Whether a point is inside the part of this category that is actually drawn.
     * Hit tests have to respect the same clip as {@link #render}, otherwise a tall open
     * category keeps invisible module hitboxes over the categories stacked below it.
     */
    public boolean isPointVisible(float px, float py) {
        if (px < this.x - 1.0f || px > this.x + this.width + 1.0f) {
            return false;
        }
        if (py < this.y) {
            return false;
        }
        if (this.opened || this.closing) {
            return py <= this.visibleBottom();
        }
        return py <= this.y + this.titleHeight + 3.0f;
    }

    /** Whether a point is inside the clipped module area (excludes the title bar). */
    public boolean isContentVisible(float px, float py) {
        if (!(this.opened || this.closing)) {
            return false;
        }
        if (px < this.x - 1.0f || px > this.x + this.width + 1.0f) {
            return false;
        }
        return py >= this.contentTop() && py <= this.visibleBottom();
    }

    public float getX() {
        return this.x;
    }

    public void overTitle(boolean d) {
        this.dragging = d;
    }

    public void updateHeight() {
        boolean lite = GuiModule.liteMode != null && GuiModule.liteMode.isToggled();
        float y = this.titleHeight + this.scroll + 3.0f;
        this.yOffset = 2.0f;
        for (Component c : this.modules) {
            if (lite && !((ModuleComponent) c).mod.lite) continue;
            c.setOffset(y);
            y += c.getHeight();
            this.yOffset += c.getHeight();
        }
    }

    public void onScroll(int mouseX, int mouseY, int dir) {
        if (!this.isHovered(mouseX, mouseY)) {
            return;
        }
        if (this.scrollAnimation == null) {
            this.scrollAnimation = new Animation(1000.0f);
        }
        this.scrollAnimation.start();
        this.targetScroll = (float) ((double) this.targetScroll + (double) dir * GuiModule.scrollSpeed.getInput());
    }

    public void mousePosition(int mouseX, int mouseY) {
        if (this.dragging) {
            this.setX((float) mouseX - this.xOffset);
            this.setY((float) mouseY - this.dragY);
        }
    }

    public float dragY;

    public void setY(float y) {
        this.y = y;
        this.targetY = y;
    }

    public float getTargetY() {
        return this.targetY;
    }

    public void setTargetY(float y) {
        this.targetY = y;
    }

    /** Eases {@link #y} toward {@link #targetY}. {@code alpha} is 0..1, frame-time corrected. */
    public void applyLayoutY(float alpha) {
        if (this.dragging) {
            this.targetY = this.y;
            return;
        }
        this.y += (this.targetY - this.y) * alpha;
        if (Math.abs(this.targetY - this.y) < 0.05f) {
            this.y = this.targetY;
        }
    }

    public float getWidth() {
        return this.width;
    }

    public boolean isHovered(int x, int y) {
        return (float) y >= this.y + this.titleHeight && this.isPointVisible(x, y);
    }

    public void setX(float x) {
        this.x = x;
    }

    public boolean overTitle(int x, int y) {
        return (float) x >= this.x && (float) x <= this.x + this.width && (float) y >= this.y && (float) y <= this.y + this.titleHeight;
    }
}

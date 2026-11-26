package omg.depchaivl.ryause.gui;

import net.minecraft.client.gui.ScaledResolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MiauClickGui extends ClickGui {
    private static final float CATEGORY_WIDTH = 92.0f;
    private static final float CATEGORY_GAP = 10.0f;
    private static final float LEFT = 5.0f;
    private static final float TOP = 5.0f;

    public static MiauClickGui instance;
    private boolean layoutInitialized;
    private long lastLayoutUpdate;

    @Override
    public void initGui() {
        super.initGui();
        if (!this.layoutInitialized) {
            this.initializeLayout();
            this.layoutInitialized = true;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        for (CategoryComponent category : categories) {
            category.mousePosition(mouseX, mouseY);
        }
        this.updateLayout();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    public void resetLayout() {
        this.initializeLayout();
        this.layoutInitialized = true;
    }

    private void initializeLayout() {
        int columns = this.columnCount();
        for (int i = 0; i < categories.size(); i++) {
            CategoryComponent category = categories.get(i);
            int column = i % columns;
            category.setX(LEFT + column * (CATEGORY_WIDTH + CATEGORY_GAP));
            category.setY(TOP);
        }
        this.updateLayout(true);
    }

    private void updateLayout() {
        this.updateLayout(false);
    }

    private void updateLayout(boolean snap) {
        long now = System.currentTimeMillis();
        long elapsed = this.lastLayoutUpdate == 0L ? 16L : now - this.lastLayoutUpdate;
        this.lastLayoutUpdate = now;
        elapsed = Math.max(1L, Math.min(100L, elapsed));

        int columns = this.columnCount();
        List<List<CategoryComponent>> byColumn = new ArrayList<List<CategoryComponent>>(columns);
        for (int i = 0; i < columns; i++) {
            byColumn.add(new ArrayList<CategoryComponent>());
        }
        for (CategoryComponent category : categories) {
            category.tickAnimation();
            if (category.dragging) {
                category.setTargetY(category.getY());
            }
            float centerX = category.getX() + CATEGORY_WIDTH / 2.0f;
            int column = (int) Math.floor((centerX - LEFT) / (CATEGORY_WIDTH + CATEGORY_GAP));
            column = Math.max(0, Math.min(columns - 1, column));
            byColumn.get(column).add(category);
        }
        for (List<CategoryComponent> column : byColumn) {
            Collections.sort(column, Comparator.comparingDouble(c -> c.getTargetY() + this.categoryHeight(c) / 2.0f));
            float y = TOP;
            for (CategoryComponent category : column) {
                if (category.dragging) {
                    category.setTargetY(category.getY());
                    y = Math.max(y, category.getY()) + this.categoryHeight(category) + CATEGORY_GAP;
                } else {
                    category.setTargetY(y);
                    y += this.categoryHeight(category) + CATEGORY_GAP;
                }
            }
        }
        if (snap) {
            for (CategoryComponent category : categories) {
                category.setY(category.getTargetY());
            }
        } else {
            float alpha = 1.0f - (float) Math.exp(-(double) elapsed / 70.0);
            for (CategoryComponent category : categories) {
                category.applyLayoutY(alpha);
            }
        }
    }

    private float categoryHeight(CategoryComponent category) {
        return category.titleHeight + 3.0f + category.animatedHeight;
    }

    private int columnCount() {
        ScaledResolution resolution = new ScaledResolution(this.mc);
        return Math.max(1, (resolution.getScaledWidth() - 10) / (int) (CATEGORY_WIDTH + CATEGORY_GAP));
    }

    @Override
    protected String getTitle() {
        return "Ryause";
    }
}

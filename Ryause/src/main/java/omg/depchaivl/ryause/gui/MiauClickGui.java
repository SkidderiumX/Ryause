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
        this.updateLayout();
    }

    private void updateLayout() {
        int columns = this.columnCount();
        List<List<CategoryComponent>> byColumn = new ArrayList<List<CategoryComponent>>(columns);
        for (int i = 0; i < columns; i++) {
            byColumn.add(new ArrayList<CategoryComponent>());
        }
        for (CategoryComponent category : categories) {
            category.tickAnimation();
            int column = Math.round((category.getX() - LEFT) / (CATEGORY_WIDTH + CATEGORY_GAP));
            column = Math.max(0, Math.min(columns - 1, column));
            byColumn.get(column).add(category);
        }
        for (List<CategoryComponent> column : byColumn) {
            Collections.sort(column, Comparator.comparingDouble(CategoryComponent::getY));
            float y = TOP;
            for (CategoryComponent category : column) {
                if (!category.dragging) {
                    category.setY(y);
                }
                y = Math.max(y, category.getY()) + this.categoryHeight(category) + CATEGORY_GAP;
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

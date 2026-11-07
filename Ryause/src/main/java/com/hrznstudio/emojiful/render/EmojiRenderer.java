package com.hrznstudio.emojiful.render;

import com.hrznstudio.emojiful.api.Emoji;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Renders an emoji quad using 1.8.9 Tessellator / WorldRenderer APIs.
 */
public class EmojiRenderer {

    private static final float EMOJI_SIZE = 9f;

    /** Render at full opacity. */
    public static float renderEmoji(Emoji emoji, float x, float y) {
        return renderEmoji(emoji, x, y, 1.0f);
    }

    /**
     * Render an emoji quad at (x, y) with the given alpha (0.0–1.0).
     * alpha ~0.25 is used for drop-shadow passes.
     */
    public static float renderEmoji(Emoji emoji, float x, float y, float alpha) {
        ResourceLocation texture = emoji.getResourceLocationForBinding();
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);

        GlStateManager.color(alpha, alpha, alpha, alpha);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();

        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x,              y,              0).tex(0, 0).endVertex();
        wr.pos(x,              y + EMOJI_SIZE, 0).tex(0, 1).endVertex();
        wr.pos(x + EMOJI_SIZE, y + EMOJI_SIZE, 0).tex(1, 1).endVertex();
        wr.pos(x + EMOJI_SIZE, y,              0).tex(1, 0).endVertex();
        tessellator.draw();

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f); // reset color
        GlStateManager.disableBlend();

        return EMOJI_SIZE + 2f;
    }
}

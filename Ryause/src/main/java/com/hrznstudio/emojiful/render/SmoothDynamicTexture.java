package com.hrznstudio.emojiful.render;

import com.hrznstudio.emojiful.config.EmojifulConfig;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IResourceManager;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * A DynamicTexture that applies GL_LINEAR filtering after upload when
 * EmojifulConfig.smoothEmoji is true. This fixes the pixelated appearance
 * of Twemoji and other high-resolution source images scaled down to 9px.
 */
public class SmoothDynamicTexture extends DynamicTexture {

    public SmoothDynamicTexture(BufferedImage image) {
        super(image);
    }

    @Override
    public void loadTexture(IResourceManager resourceManager) throws IOException {
        super.loadTexture(resourceManager);
        applyFilter();
    }

    /** Call this after the texture is first registered with the TextureManager. */
    public void applyFilter() {
        if (EmojifulConfig.smoothEmoji) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        } else {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }
    }
}

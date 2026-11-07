package com.hrznstudio.emojiful.api;

import com.hrznstudio.emojiful.Constants;
import net.minecraft.client.Minecraft;
import com.hrznstudio.emojiful.render.SmoothDynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Twemoji emoji — images from Twitter's emoji CDN.
 * In 1.8.9 we don't have SimpleTexture infrastructure like 1.21,
 * so we load images as DynamicTexture on the main thread via scheduleTask.
 */
public class EmojiFromTwitmoji extends Emoji {

    private ResourceLocation cachedRL = null;

    @Override
    public void checkLoad() {
        if (cachedRL != null) return;

        cachedRL = LOADING_TEXTURE;
        final File cacheFile = new File("emojiful/cache/" + name + "-" + version);
        final String imageUrl = "https://raw.githubusercontent.com/iamcal/emoji-data/master/img-twitter-64/" + location;
        final ResourceLocation target = new ResourceLocation(Constants.MOD_ID,
                "textures/emoji/" + location.toLowerCase().replaceAll("[^a-z0-9/._-]", "") + "_" + version);

        new Thread("Emojiful Twemoji Loader #" + threadDownloadCounter.incrementAndGet()) {
            @Override
            public void run() {
                try {
                    if (!cacheFile.exists()) {
                        if (cacheFile.getParentFile() != null) cacheFile.getParentFile().mkdirs();
                        HttpURLConnection conn = (HttpURLConnection) new URL(imageUrl)
                                .openConnection(Minecraft.getMinecraft().getProxy());
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                        conn.connect();
                        if (conn.getResponseCode() / 100 == 2) {
                            FileUtils.copyInputStreamToFile(conn.getInputStream(), cacheFile);
                        } else {
                            cachedRL = NO_SIGNAL_TEXTURE;
                            finishedLoading = true;
                            return;
                        }
                        conn.disconnect();
                    }
                    final BufferedImage image = ImageIO.read(cacheFile);
                    if (image != null) {
                        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                            @Override
                            public void run() {
                                SmoothDynamicTexture dynTex = new SmoothDynamicTexture(image);
                                Minecraft.getMinecraft().getTextureManager().loadTexture(target, dynTex);
                                dynTex.applyFilter();
                                cachedRL = target;
                                frames.add(target);
                                finishedLoading = true;
                            }
                        });
                    } else {
                        cachedRL = ERROR_TEXTURE;
                        finishedLoading = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    cachedRL = ERROR_TEXTURE;
                    finishedLoading = true;
                }
            }
        }.start();
    }

    @Override
    public ResourceLocation getResourceLocationForBinding() {
        checkLoad();
        return cachedRL != null ? cachedRL : LOADING_TEXTURE;
    }
}

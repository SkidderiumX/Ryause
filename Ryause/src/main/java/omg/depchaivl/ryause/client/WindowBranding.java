package omg.depchaivl.ryause.client;

import com.hrznstudio.emojiful.Constants;
import org.lwjgl.opengl.Display;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public final class WindowBranding {
    private static boolean applied;

    private WindowBranding() {
    }

    public static void apply() {
        if (applied || !Display.isCreated()) {
            return;
        }

        Display.setTitle("Ryause");
        applied = true;
        try (InputStream input = WindowBranding.class.getResourceAsStream("/assets/clickgui/ryause-logo.png")) {
            if (input == null) {
                throw new IOException("Missing /assets/clickgui/ryause-logo.png");
            }
            BufferedImage logo = ImageIO.read(input);
            if (logo == null) {
                throw new IOException("Could not decode /assets/clickgui/ryause-logo.png");
            }
            Display.setIcon(new ByteBuffer[]{icon(logo, 32), icon(logo, 16)});
        } catch (IOException e) {
            Constants.LOG.error("Could not set the Ryause window icon", e);
        }
    }

    private static ByteBuffer icon(BufferedImage logo, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(logo, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }

        ByteBuffer buffer = ByteBuffer.allocateDirect(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int pixel = image.getRGB(x, y);
                buffer.put((byte) (pixel >> 16 & 0xFF));
                buffer.put((byte) (pixel >> 8 & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) (pixel >> 24 & 0xFF));
            }
        }
        buffer.flip();
        return buffer;
    }
}

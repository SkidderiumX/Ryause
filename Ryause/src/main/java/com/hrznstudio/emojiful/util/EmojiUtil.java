package com.hrznstudio.emojiful.util;

import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EmojiUtil {

    public static String cleanStringForRegex(String string) {
        return string
                .replaceAll("\\)", "\\\\)")
                .replaceAll("\\(", "\\\\(")
                .replaceAll("\\|", "\\\\|")
                .replaceAll("\\*", "\\\\*");
    }

    public static List<Pair<BufferedImage, Integer>> splitGif(File file) throws IOException {
        List<Pair<BufferedImage, Integer>> images = new ArrayList<>();
        ImageReader reader = ImageIO.getImageReadersBySuffix("gif").next();
        reader.setInput(ImageIO.createImageInputStream(new FileInputStream(file)), false);
        IIOMetadata metadata = reader.getImageMetadata(0);
        String metaFormatName = metadata.getNativeMetadataFormatName();

        for (int i = 0; i < reader.getNumImages(true); i++) {
            int frameLength = 1;
            BufferedImage image = reader.read(i);
            BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
            newImage.getGraphics().drawImage(image, 0, 0, null);
            IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(i).getAsTree(metaFormatName);
            int nNodes = root.getLength();
            for (int j = 0; j < nNodes; j++) {
                Node node = root.item(j);
                if (node.getNodeName().equalsIgnoreCase("GraphicControlExtension")) {
                    frameLength = Integer.parseInt(((IIOMetadataNode) node).getAttribute("delayTime"));
                    break;
                }
            }
            images.add(Pair.of(newImage, frameLength));
        }
        return images;
    }
}

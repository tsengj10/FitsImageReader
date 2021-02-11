package org.lsst.fits.imageio.speedtest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import org.lsst.fits.imageio.FITSImageReadParam;

/**
 *
 * @author tonyj
 */
public class SimpleReadTest {
    public static void main(String[] args) throws IOException {
        File file = new File(args[0]);
        Iterator<ImageReader> imageReadersByFormatName = ImageIO.getImageReadersBySuffix(".fp");
        ImageReader reader = imageReadersByFormatName.next();
        reader.setInput(ImageIO.createImageInputStream(file));
        FITSImageReadParam readParam = (FITSImageReadParam) reader.getDefaultReadParam();
        readParam.setSourceSubsampling(16, 16, 0, 0);
        BufferedImage image = reader.read(0, readParam);
        System.out.println("Got image "+image);
        ImageIO.write(image, "png", new File("test.png"));
        System.exit(0);
    }
}

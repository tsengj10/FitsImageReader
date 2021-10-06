package org.lsst.fits.imageio.test;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import javax.imageio.ImageIO;

/**
 *
 * @author tonyj
 */
public class CantaloupeClient {

    private final URL base;

    CantaloupeClient(URL base) {
        this.base = base;
    }

    BufferedImage read(String imageId, Rectangle region, Dimension size, String format) throws URISyntaxException, MalformedURLException, IOException {
        String regionString = String.format("%d,%d,%d,%d/", region.x, region.y, region.width, region.height);
        String sizeString = String.format("%d,%d/", size.width, size.height);
        URI fullURI = base.toURI().resolve(imageId + "/").resolve(regionString).resolve(sizeString).resolve("./0/default." + format);
        return ImageIO.read(fullURI.toURL());
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        CantaloupeClient client = new CantaloupeClient(new URL("https://lsst-camera.slac.stanford.edu/iiif/2/"));
        String imageId = "MC_C_20210131_000128_all$colorMap=b$biasCorrection=Simple%20Overscan%20Correction";
        Rectangle region = new Rectangle(4000, 4000, 2000, 2000);
        Dimension size = new Dimension(500, 500);
        BufferedImage image = client.read(imageId, region, size, "png");
        System.out.println("I got " + image);
    }

}

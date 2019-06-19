package org.lsst.fits.imageio.test;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import org.lsst.fits.imageio.CameraImageReader;
import org.lsst.fits.imageio.cmap.RGBColorMap;
import org.lsst.fits.imageio.cmap.SAOColorMap;

/**
 *
 * @author tonyj
 */
public class ColorScale extends ImageComponent {

    private final RGBColorMap cmap;

    ColorScale(RGBColorMap cmap) {
        this.cmap = cmap;
        setPreferredSize(new Dimension(1024, 40));
        BufferedImage bi = CameraImageReader.IMAGE_TYPE.createBufferedImage(cmap.getSize(), 1);
        for (int i=0; i<cmap.getSize(); i++) {
            bi.setRGB(i, 0, cmap.getRGB(i));
        }
        setImage(bi);
    }

    public static void main(String[] args) {
        ColorScale colorScale = new ColorScale(new SAOColorMap(256, "b.sao"));
        JFrame frame = new JFrame();
        frame.setContentPane(colorScale);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(new Dimension(600, 100));
        frame.setVisible(true);
    }
}

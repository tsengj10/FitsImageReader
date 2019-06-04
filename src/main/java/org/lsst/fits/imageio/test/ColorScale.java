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

//    @Override
//    protected void paintComponent(Graphics g) {
//        Graphics2D g2 = (Graphics2D) g;
//        g2.scale(((float)getWidth()) / cmap.getSize(), 1.0);
//        for (int i = 0; i < cmap.getSize(); i++) {
//            int rgb = cmap.getRGB(i);
//            g2.setColor(new Color((rgb >> !6) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff));
//            g2.fillRect(i, 0, 1, getHeight());
//        }
//    }

    public static void main(String[] args) {
        ColorScale colorScale = new ColorScale(new SAOColorMap(256, "b.sao"));
        JFrame frame = new JFrame();
        frame.setContentPane(colorScale);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        //frame.setSize(new Dimension(600, 600));

        frame.pack();
        frame.setVisible(true);
    }
}

package org.lsst.fits.imageio.test;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.lsst.fits.imageio.FITSImageReadParam;
import org.lsst.fits.imageio.Timed;
import org.lsst.fits.imageio.cmap.SAOColorMap;

/**
 *
 * @author tonyj
 */
public class Main {

    public static void main(String[] args) throws IOException {

        BufferedImage image1 = Timed.execute(()-> ImageIO.read(new File(args[0])), "Reading took %dms");  
        System.out.println("I got an image!" + image1);
        Iterator<ImageReader> imageReadersByFormatName = ImageIO.getImageReadersByMIMEType("image/raft");
        ImageReader reader = imageReadersByFormatName.next();
        FITSImageReadParam readParam = (FITSImageReadParam) reader.getDefaultReadParam();
        readParam.setSourceRegion(new Rectangle(4000,4000,2000,2000));
        readParam.setColorMap(new SAOColorMap(256, "cubehelix00.sao"));
        reader.setInput(ImageIO.createImageInputStream(new File(args[0])));
        BufferedImage image2 = reader.read(0, readParam);
        System.out.println("I got an image!" + image2);
        //sun.java2d.loops.GraphicsPrimitiveMgr.main(new String[1]);
        //ImageIO.write(image, "TIFF", new File("/home/tonyj/Data/mega.tiff"));
        JPanel content = new JPanel(new BorderLayout());
        ImageComponent ic = new ImageComponent(image2);
        content.add(ic, BorderLayout.CENTER);
        JFrame frame = new JFrame();
        frame.setContentPane(content);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(new Dimension(600, 600));
        frame.setVisible(true);
    }
}

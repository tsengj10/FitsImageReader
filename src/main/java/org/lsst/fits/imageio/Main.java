package org.lsst.fits.imageio;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.lsst.fits.test.ImageComponent;
import org.lsst.fits.test.Timed;

/**
 *
 * @author tonyj
 */
public class Main {

    public static void main(String[] args) throws IOException {

        BufferedImage image = Timed.execute(()-> ImageIO.read(new File("/home/tonyj/Data/pretty/R22.raft")), "Reading took %dms");
//        Iterator<ImageReader> imageReadersByFormatName = ImageIO.getImageReadersByMIMEType("image/raft");
//        ImageReader reader = imageReadersByFormatName.next();
//        ImageReadParam readParam = reader.getDefaultReadParam();
//        readParam.setSourceRegion(new Rectangle(4000,4000,2000,2000));
//        reader.setInput(ImageIO.createImageInputStream(new File("/home/tonyj/Data/pretty/R22.raft")));
//        BufferedImage image = reader.read(0, readParam);
        System.out.println("I got an image!" + image);
        //ImageIO.write(image, "TIFF", new File("/home/tonyj/Data/mega.tiff"));
        JPanel content = new JPanel(new BorderLayout());
        ImageComponent ic = new ImageComponent(image);
        content.add(ic, BorderLayout.CENTER);
        JFrame frame = new JFrame();
        frame.setContentPane(content);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(new Dimension(600, 600));
        frame.setVisible(true);
    }
}

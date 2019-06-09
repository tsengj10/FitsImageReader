package org.lsst.fits.imageio.test;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Simple component for displaying a buffered image
 *
 * @author tonyj
 */
public class ImageComponent extends JComponent {

    private static final long serialVersionUID = 1L;
    private float scale = 1;
    private BufferedImage image;
    private float minScale = 0;

    public ImageComponent() {
        image = null;
        addMouseWheelListener(new MouseAdapter() {

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double delta = 0.005f * e.getPreciseWheelRotation();
                scale += delta;
                revalidate();
                repaint();
            }

        });
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = new Dimension(200, 200);
        if (image != null) {
            if (minScale == 0) {
                JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
                scale = Math.min((float) (scroll.getHeight()) / image.getHeight(), (float) (scroll.getWidth()) / image.getWidth());
                System.out.println(scale);
                minScale = scale;
                return scroll.getPreferredSize();
            } else {
                size.width = Math.round(image.getWidth() * scale);
                size.height = Math.round(image.getHeight() * scale);
            }
        }
        return size;
    }

    public ImageComponent(BufferedImage image) {
        this();
        setImage(image);
    }

    final void setImage(BufferedImage image) {

        this.image = image;
        revalidate();
        repaint();
    }

    BufferedImage getImage() {
        return image;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (image != null) {
            AffineTransform at = new AffineTransform();
            at.scale(1, -1);
            at.translate(0, -getHeight());
            at.scale(scale, scale);
//            float ninth = 1.0f / 9.0f;
//            float[] blurKernel = {
//                ninth, ninth, ninth,
//                ninth, ninth, ninth,
//                ninth, ninth, ninth
//            };
//            Map<RenderingHints.Key, Object> map = new HashMap<>();
//            map.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
//            map.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
//            map.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//            RenderingHints hints = new RenderingHints(map);
//            BufferedImageOp op = new ConvolveOp(new Kernel(3, 3, blurKernel), ConvolveOp.EDGE_NO_OP, hints);
//            BufferedImage scale = op.filter(image, null);
//            Timed.execute(
            ((Graphics2D) g).drawImage(image, at, this);
//                    "Paint image of type %d and size %dx%d took %dms", image.getType(), image.getWidth(), image.getHeight()
//            );
        }
    }
}

package org.lsst.fits.imageio.test;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JScrollPane;

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
    private final float maxScale = 1;
    private final boolean zoomPan;
    private JScrollPane scroll;
    private boolean zoomScaleSet = false;

    public ImageComponent() {
        this(false);
    }

    public ImageComponent(boolean zoomPan) {
        this.zoomPan = zoomPan;
        image = null;
        this.setLayout(new BorderLayout());
        Inner inner = new Inner();
        if (zoomPan) {
            scroll = new JScrollPane(inner);
            add(scroll);
        } else {
            add(inner);
        }
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (zoomScaleSet) {
                    minScale = Math.min((float) getHeight() / image.getHeight(), (float) getWidth() / image.getWidth());
                    minScale = Math.min(minScale, maxScale);
                    scale = Math.max(scale, minScale);
                }
            }
        });
    }

    public ImageComponent(boolean zoomPan, BufferedImage image) {
        this(zoomPan);
        setImage(image);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = new Dimension(200, 200);
        if (image != null) {
            size.width = image.getWidth();
            size.height = image.getHeight();
        }
        return size;
    }

    final void setImage(BufferedImage image) {
        this.image = image;
        this.zoomScaleSet = false;
        repaint();
    }

    private void checkScale() {
        if (!zoomScaleSet) {
            minScale = Math.min((float) getHeight() / image.getHeight(), (float) getWidth() / image.getWidth());
            minScale = Math.min(minScale, maxScale);
            scale = minScale;
            zoomScaleSet = true;
        }
    }

    BufferedImage getImage() {
        return image;
    }

    private class Inner extends JComponent {

        Inner() {
            if (zoomPan) {
                MouseAdapter a = new MouseAdapter() {
                    private Point savePoint;
                    private Rectangle saveViewRectangle;

                    @Override
                    public void mouseDragged(MouseEvent e) {
                        Rectangle newRectangle = new Rectangle(saveViewRectangle);
                        newRectangle.x += savePoint.x - e.getX();
                        newRectangle.y += savePoint.y - e.getY();
                        scroll.getViewport().scrollRectToVisible(newRectangle);
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        savePoint = e.getPoint();
                        saveViewRectangle = scroll.getViewport().getVisibleRect();
                    }

                    @Override
                    public void mouseWheelMoved(MouseWheelEvent e) {
                        if (e.getPreciseWheelRotation() > 0) {
                            zoom(e.getPoint(), 1 / 0.9f);
                        } else {
                            zoom(e.getPoint(), 0.9f);
                        }

                    }
                };

                addMouseWheelListener(a);
                addMouseMotionListener(a);
                addMouseListener(a);
            }
        }

        public void zoom(Point point, float zoomFactor) {
            Point pos = scroll.getViewport().getViewPosition();
            Point.Float mouseRelativeToImage = new Point.Float((point.x) / scale, (point.y) / scale);
            scale *= zoomFactor;
            scale = Math.max(minScale, Math.min(maxScale, scale));
            Point.Float mouseRelativeToImageNew = new Point.Float(point.x / scale, point.y / scale);
            Point.Float shiftNeeded = new Point.Float(mouseRelativeToImageNew.x - mouseRelativeToImage.x,
                    mouseRelativeToImageNew.y - mouseRelativeToImage.y);
            float newX = pos.x - shiftNeeded.x * scale;
            float newY = pos.y - shiftNeeded.y * scale;
            final Point newPos = new Point(Math.round(newX), Math.round(newY));
            scroll.getViewport().setViewPosition(newPos);
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = new Dimension(200, 200);
            if (image != null) {
                checkScale();
                size.width = (int) (image.getWidth() * scale);
                size.height = (int) (image.getHeight() * scale);
            }
            return size;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (image != null) {
                if (zoomPan) {
                    AffineTransform at = new AffineTransform();
                    at.scale(1, -1);
                    at.translate(0, -getHeight());
                    at.scale(scale, scale);
                    ((Graphics2D) g).drawImage(image, at, this);
                } else {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.scale(1, -1);
                    g2.translate(0, -getHeight());
                    g2.drawImage(image, 0, 0, getWidth(), getHeight(), this);
                }
            }
        }
    }
}

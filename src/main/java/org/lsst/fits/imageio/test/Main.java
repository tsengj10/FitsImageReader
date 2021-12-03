package org.lsst.fits.imageio.test;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileFilter;
import org.lsst.fits.imageio.CameraImageReadParam;
import org.lsst.fits.imageio.CameraImageReader;
import org.lsst.fits.imageio.Segment;
import org.lsst.fits.imageio.bias.BiasCorrection.CorrectionFactors;

/**
 *
 * @author tonyj
 */
public class Main {

    private CameraImageReader reader;
    private CameraImageReadParam readParam;
    private ImageReaderComponent ic;
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException, UnsupportedLookAndFeelException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Main main = new Main();
        main.start(args[0]);
    }

    private void start(String file) throws IOException {
        //BufferedImage image1 = Timed.execute(()-> ImageIO.read(new File(args[0])), "Reading took %dms");  
        //System.out.println("I got an image!" + image1);
        CameraImageReader reader = open(new File(file));
        //sun.java2d.loops.GraphicsPrimitiveMgr.main(new String[1]);
        //ImageIO.write(image, "TIFF", new File("/home/tonyj/Data/mega.tiff"));

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(createFileMenu());
        menuBar.add(createColorMenu());
        menuBar.add(createBiasMenu());
        menuBar.add(createOverscanMenu());
        menuBar.add(createScaleMenu());
        ic = new ImageReaderComponent(true, reader, readParam);
        JTextField infoComponent = new JTextField("Info");
        infoComponent.setEditable(false);
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout());
        contentPanel.add(ic, BorderLayout.CENTER);
        contentPanel.add(infoComponent, BorderLayout.SOUTH);
        ic.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point ip = ic.getImagePosition(e.getPoint());
                StringBuilder builder = new StringBuilder();
                builder.append("x=").append(ip.x).append(" y=").append(ip.y);
                Segment segment = Main.this.reader.getImageMetaDataForPoint(readParam, ip.x, ip.y);
                if (segment != null) {
                    try {
                        builder.append(" ").append(segment.getRaftBay()).append(" ").append(segment.getCcdSlot()).append(" ").append(segment.getSegmentName());
                        AffineTransform wcsTranslation = segment.getWCSTranslation(false);
                        AffineTransform inverse = wcsTranslation.createInverse();
                        inverse.transform(ip, ip);
                        builder.append(" x=").append(ip.x).append(" y=").append(ip.y);
                        int pixel = Main.this.reader.getPixelForSegment(segment, ip.x, ip.y);
                        builder.append(" pixel=").append(pixel);
                        int rgb = Main.this.reader.getRGBForSegment(segment, ip.x, ip.y);
                        builder.append(" rgb=").append(rgb&0xff);
                        CorrectionFactors cf = Main.this.reader.getCorrectionFactorForSegment(segment);
                        final int correctionFactor = cf.correctionFactor(segment.getDataSec().x + ip.x, segment.getDataSec().y + ip.y);
                        builder.append(" cf=").append(correctionFactor);
                        builder.append(" pixel-cf=").append(pixel-correctionFactor);
                    } catch (NoninvertibleTransformException ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                infoComponent.setText(builder.toString());
            }
        });
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame();
            frame.setJMenuBar(menuBar);
            frame.setContentPane(contentPanel);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(new Dimension(600, 600));
            frame.setVisible(true);
        });

    }

    private CameraImageReader open(File file) throws IOException {
        int pos_suffix = file.getName().lastIndexOf('.');
        String suffix = file.getName().substring(pos_suffix);
        Iterator<ImageReader> imageReadersByFormatName = ImageIO.getImageReadersBySuffix(suffix);
        reader = (CameraImageReader) imageReadersByFormatName.next();
        CameraImageReadParam newReadParam = (CameraImageReadParam) reader.getDefaultReadParam();
        if (readParam != null) {
            newReadParam.setColorMap(this.readParam.getColorMap());
            newReadParam.setBiasCorrection(this.readParam.getBiasCorrection());
            newReadParam.setScale(this.readParam.getScale());
            newReadParam.setShowBiasRegions(this.readParam.isShowBiasRegions());
        }
        this.readParam = newReadParam;
//        if (suffix.equals(".fp")) {
//            readParam.setSourceSubsampling(8, 8, 0, 0);
//            readParam.setWCSString('E');
//        }
        //readParam.setSourceRegion(new Rectangle(1000,10,256,256));
        //readParam.setColorMap(new SAOColorMap(256, "cubehelix00.sao"));
        reader.setInput(ImageIO.createImageInputStream(file));
        //BufferedImage image1 = reader.read(0, readParam);
        //System.out.println("I got an image!" + image1);
        return reader;
    }

    private JMenu createFileMenu() {
        JMenu fileMenu = new JMenu("File");
        JMenuItem open = new JMenuItem("Open...");
        open.addActionListener((ActionEvent event) -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileFilter() {
                @Override
                public String getDescription() {
                    return "Raft file (.raft)";
                }

                @Override
                public boolean accept(File file) {
                    return file.isDirectory() || file.getName().endsWith(".raft");
                }
            });
            chooser.setFileFilter(new FileFilter() {
                @Override
                public String getDescription() {
                    return "Focal Plane file (.fp)";
                }

                @Override
                public boolean accept(File file) {
                    return file.isDirectory() || file.getName().endsWith(".fp");
                }
            });
            chooser.setFileFilter(new FileFilter() {
                @Override
                public String getDescription() {
                    return "CCD file (.ccd)";
                }

                @Override
                public boolean accept(File file) {
                    return file.isDirectory() || file.getName().endsWith(".ccd");
                }
            });
            int rc = chooser.showOpenDialog(ic);
            if (rc == JFileChooser.APPROVE_OPTION) {
                try {
                    reader = open(chooser.getSelectedFile());
                    ic.setImageReader(reader, readParam);
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Unable to open file", ex);
                }
            }
        });
        fileMenu.add(open);
        return fileMenu;
    }

    private JMenu createBiasMenu() {
        JMenu biasMenu = new JMenu("Bias");
        ButtonGroup group = new ButtonGroup();
        String currentBiasCorrection = readParam.getBiasCorrectionName();
        for (String biasCorrectionMenuName : readParam.getAvailableBiasCorrections()) {
            JCheckBoxMenuItem biasCorrectionItem = new JCheckBoxMenuItem(biasCorrectionMenuName);
            if (biasCorrectionMenuName.equals(currentBiasCorrection)) {
                biasCorrectionItem.setSelected(true);
            }
            biasCorrectionItem.addActionListener((ActionEvent e) -> {
                readParam.setBiasCorrection(((JMenuItem) e.getSource()).getText());
                refresh();
            });
            group.add(biasCorrectionItem);
            biasMenu.add(biasCorrectionItem);
        }
        return biasMenu;
    }

    private JMenu createOverscanMenu() {
        JMenu overscanMenu = new JMenu("Overscan");
        final JCheckBoxMenuItem show = new JCheckBoxMenuItem("Show");
        show.addActionListener((ActionEvent e) -> {
            readParam.setShowBiasRegions(((JMenuItem) e.getSource()).isSelected());
            refresh();
        });
        overscanMenu.add(show);
        return overscanMenu;
    }

    private JMenu createColorMenu() {
        JMenu colorMenu = new JMenu("Color");
        ButtonGroup group = new ButtonGroup();
        String currentColorItem = readParam.getColorMapName();
        for (String colorMenuItemName : readParam.getAvailableColorMaps()) {
            JCheckBoxMenuItem colorMenuItem = new JCheckBoxMenuItem(colorMenuItemName);
            if (colorMenuItemName.equals(currentColorItem)) {
                colorMenuItem.setSelected(true);
            }
            colorMenuItem.addActionListener((ActionEvent e) -> {
                readParam.setColorMap(((JMenuItem) e.getSource()).getText());
                refresh();
            });
            group.add(colorMenuItem);
            colorMenu.add(colorMenuItem);
        }
        return colorMenu;
    }

    private JMenu createScaleMenu() {
        JMenu scaleMenu = new JMenu("Scale");
        JCheckBoxMenuItem global = new JCheckBoxMenuItem("Global");
        global.setSelected(readParam.getScale() == CameraImageReadParam.Scale.GLOBAL);
        global.addActionListener((ActionEvent e) -> {
            readParam.setScale(global.isSelected() ? CameraImageReadParam.Scale.GLOBAL : CameraImageReadParam.Scale.AMPLIFIER);
            refresh();
        });
        scaleMenu.add(global);
        return scaleMenu;
    }

    private void refresh() {

        try {
            ic.setImageReader(reader, readParam);
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}

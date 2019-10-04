package org.lsst.fits.imageio.test;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
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
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileFilter;
import org.lsst.fits.imageio.FITSImageReadParam;

/**
 *
 * @author tonyj
 */
public class Main {

    private ImageReader reader;
    private FITSImageReadParam readParam;
    private ImageComponent ic;
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException, UnsupportedLookAndFeelException, ClassNotFoundException, InstantiationException, IllegalAccessException {
         UIManager.setLookAndFeel(
            UIManager.getSystemLookAndFeelClassName());
        Main main = new Main();
        main.start(args[0]);
    }

    private void start(String file) throws IOException {
        //BufferedImage image1 = Timed.execute(()-> ImageIO.read(new File(args[0])), "Reading took %dms");  
        //System.out.println("I got an image!" + image1);
        BufferedImage bi = open(new File(file));
        //sun.java2d.loops.GraphicsPrimitiveMgr.main(new String[1]);
        //ImageIO.write(image, "TIFF", new File("/home/tonyj/Data/mega.tiff"));

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(createFileMenu());
        menuBar.add(createColorMenu());
        menuBar.add(createBiasMenu());
        menuBar.add(createOverscanMenu());
        ic = new ImageComponent(true, bi);
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame();
            frame.setJMenuBar(menuBar);
            frame.add(ic);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(new Dimension(600, 600));
            frame.setVisible(true);
        });

    }
    
    private BufferedImage open(File file) throws IOException {
        int pos_suffix = file.getName().lastIndexOf('.');
        String suffix = file.getName().substring(pos_suffix);
        Iterator<ImageReader> imageReadersByFormatName = ImageIO.getImageReadersBySuffix(suffix);
        reader = imageReadersByFormatName.next();
        readParam = (FITSImageReadParam) reader.getDefaultReadParam();
        if (suffix.equals(".fp")) {
            readParam.setSourceSubsampling(8, 8, 0, 0);
            readParam.setWCSString('E');
        }
        //readParam.setSourceRegion(new Rectangle(4000,4000,2000,2000));
        //readParam.setColorMap(new SAOColorMap(256, "cubehelix00.sao"));
        reader.setInput(ImageIO.createImageInputStream(file));
        BufferedImage image1 = reader.read(0, readParam);
        System.out.println("I got an image!" + image1);
        return image1;
    }

    private JMenu createFileMenu() {
        JMenu fileMenu = new JMenu("File");
        JMenuItem open = new JMenuItem("Open...");
        open.addActionListener((ActionEvent event) -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileFilter(){
                @Override
                public String getDescription() {
                    return "Raft file (.raft)";
                }
                @Override
                public boolean accept(File file) {
                    return file.isDirectory() || file.getName().endsWith(".raft");
                }
            });
            chooser.setFileFilter(new FileFilter(){
                @Override
                public String getDescription() {
                    return "Focal Plane file (.fp)";
                }
                @Override
                public boolean accept(File file) {
                    return file.isDirectory() || file.getName().endsWith(".fp");
                }
            });
            int rc = chooser.showOpenDialog(ic);
            if (rc == JFileChooser.APPROVE_OPTION) {
                BufferedImage bi;
                try {
                    bi = open(chooser.getSelectedFile());
                    ic.setImage(bi);
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE,"Unable to open file", ex);
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

    private void refresh() {

        try {
            BufferedImage image1 = reader.read(0, readParam);
            ic.setImage(image1);
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}

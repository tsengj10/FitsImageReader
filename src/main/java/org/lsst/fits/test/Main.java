package org.lsst.fits.test;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import nom.tam.fits.FitsException;
import nom.tam.fits.FitsFactory;
import nom.tam.fits.FitsUtil;
import nom.tam.fits.Header;
import nom.tam.fits.header.Standard;
import nom.tam.util.BufferedFile;

/**
 *
 * @author tonyj
 */
public class Main {

    public static void main(String[] args) throws FitsException, IOException {
        FitsFactory.setUseHierarch(true);
        File dir = new File("/home/tonyj/Data/pretty");
        File[] listFiles = dir.listFiles();

        BufferedImage mega = new BufferedImage(13000, 13000, BufferedImage.TYPE_USHORT_GRAY);
        Graphics2D g2 = mega.createGraphics();
        for (File file : listFiles) {
            try (BufferedFile bf = new BufferedFile(file)) {

                for (int i = 0; i < 17; i++) {
                    Header header = new Header(bf);
                    if (i > 0) {
                        int nAxis1 = header.getIntValue(Standard.NAXIS1);
                        int nAxis2 = header.getIntValue(Standard.NAXIS2);
                        final int bitpix = header.getIntValue(Standard.BITPIX);
                        String datasec = header.getStringValue("DATASEC");
                        Pattern pattern = Pattern.compile("\\[(\\d+):(\\d+),(\\d+):(\\d+)\\]");
                        Matcher matcher = pattern.matcher(datasec);
                        if (!matcher.matches()) {
                            throw new IOException("Invalid datasec: " + datasec);
                        }
                        int datasec1 = Integer.parseInt(matcher.group(1));
                        int datasec2 = Integer.parseInt(matcher.group(2));
                        int datasec3 = Integer.parseInt(matcher.group(3));
                        int datasec4 = Integer.parseInt(matcher.group(4));

                        String wcsname = header.getStringValue("WCSNAMEQ");
                        double pc1_1 = header.getDoubleValue("PC1_1Q");
                        double pc2_1 = header.getDoubleValue("PC2_1Q");
                        double pc1_2 = header.getDoubleValue("PC1_2Q");
                        double pc2_2 = header.getDoubleValue("PC2_2Q");

                        double crval1 = header.getDoubleValue("CRVAL1Q");
                        double crval2 = header.getDoubleValue("CRVAL2Q");

                        System.out.printf("%d: %s %g %g %g %g %g %g %s %d %d %,d\n", i, wcsname, crval1, crval2, pc1_1, pc1_2, pc2_1, pc2_2, datasec, nAxis1, nAxis2, nAxis1 * nAxis2 * 4);

                        int[] data = new int[nAxis1 * nAxis2];
                        int len = bf.read(data);
                        System.out.println(len);
                        int pad = FitsUtil.padding(4 * nAxis1 * nAxis2);
                        bf.skip(pad);

                        int[] count = new int[1 << 18];
                        for (int d : data) {
                            count[d]++;
                        }
                        ScalingUtils su = new ScalingUtils(count);
                        final int min = su.getMin();
                        final int max = su.getMax();
                        int[] cdf = su.computeCDF();
                        int range = cdf[max];
                        System.out.printf("min=%d max=%d range=%d\n", min, max, range);

                        // Scale data 
                        WritableRaster raster = Raster.createInterleavedRaster(DataBuffer.TYPE_USHORT, nAxis1, nAxis2, 1, new Point(0, 0));
                        DataBuffer db = raster.getDataBuffer();


                        for (int p = 0; p < data.length; p++) {
                            db.setElem(p, 0xffff & (int) ((cdf[data[p]]) * 65536L / range));
                        }

                        ComponentColorModel cm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY),
                                false, false, Transparency.OPAQUE,
                                raster.getTransferType());
                        BufferedImage bufferedImage = new BufferedImage(cm, raster, false, null);

                        // Convert int array to BufferedImage
//                        DataBufferInt idb = new DataBufferInt(data, 1);
//                        int[] offsets = new int[]{0};
//                        SampleModel sm = new ComponentSampleModel(idb.getDataType(), nAxis1, nAxis2, 0, nAxis1, offsets);
//                        WritableRaster raster = Raster.createWritableRaster(sm, idb, null);
//                        ComponentColorModel cm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY),
//                                new int[]{18}, false, false, Transparency.OPAQUE,
//                                raster.getTransferType());
//                        BufferedImage bufferedImage = new BufferedImage(cm, raster, false, null);
                        int y = (int) crval2;
//                        if (pc2_2 < 0) {
//                            y -= datasec4 - datasec3 + 1;
//                        }
                        int x = (int) crval1;
//                        if (pc1_1 < 0) {
//                            x -= datasec2 - datasec1 + 1;
//                        }
                        AffineTransform t = new AffineTransform();
                        t.translate(x,y);
                        t.scale(pc1_1,pc2_2);
                        g2.drawImage(bufferedImage.getSubimage(datasec1, datasec3, datasec2 - datasec1 + 1, datasec4 - datasec3 + 1),t,null);
                    }
                }
            }
        }
        g2.dispose();
        ImageIO.write(mega,"TIFF",new File("/home/tonyj/Data/mega.tiff"));
//        JPanel content = new JPanel(new BorderLayout());
//        ImageComponent ic = new ImageComponent(mega);
//        content.add(ic, BorderLayout.CENTER);
//        JFrame frame = new JFrame();
//        frame.setContentPane(content);
//        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        frame.setSize(new Dimension(600, 600));
//        frame.setVisible(true);
    }
}

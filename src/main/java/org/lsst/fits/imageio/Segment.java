package org.lsst.fits.imageio;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nom.tam.fits.BasicHDU;
import nom.tam.fits.Data;
import nom.tam.fits.FitsException;
import nom.tam.fits.FitsFactory;
import nom.tam.fits.FitsUtil;
import nom.tam.fits.Header;
import nom.tam.fits.header.Standard;
import nom.tam.image.compression.hdu.CompressedImageHDU;
import nom.tam.util.BufferedFile;

/**
 * Represents one segment (amplifier) of a FITS file
 *
 * @author tonyj
 */
public class Segment {

    private static final Pattern DATASET_PATTERN = Pattern.compile("\\[(\\d+):(\\d+),(\\d+):(\\d+)\\]");

    private final File file;
    private final long seekPosition;
    private final Rectangle2D.Double wcs;
    private final AffineTransform wcsTranslation;
    private Rectangle datasec;
    private final int nAxis1;
    private final int nAxis2;
    private double crval1;
    private double crval2;
    private double pc1_1;
    private double pc2_2;
    private double pc1_2;
    private double pc2_1;
    private final char wcsLetter;
    private final int rawDataLength;
    private final boolean isCompressed;
    private final BasicHDU<?> compressedImageHDU;
    private final int ccdX;
    private final int ccdY;
    private int channel;

    public Segment(Header header, File file, BufferedFile bf, String ccdSlot, char wcsLetter, Map<String, Object> wcsOverride) throws IOException, FitsException {
        this.file = file;
        this.seekPosition = bf.getFilePointer();
        this.wcsLetter = wcsLetter;
        isCompressed = header.getBooleanValue("ZIMAGE");
        if (isCompressed) {
            nAxis1 = header.getIntValue("ZNAXIS1");
            nAxis2 = header.getIntValue("ZNAXIS2");
            rawDataLength = header.getIntValue(Standard.NAXIS1) * header.getIntValue(Standard.NAXIS2) + header.getIntValue("PCOUNT");
            Data data = header.makeData();
            data.read(bf);
            compressedImageHDU = FitsFactory.hduFactory(header, data);
        } else {
            nAxis1 = header.getIntValue(Standard.NAXIS1);
            nAxis2 = header.getIntValue(Standard.NAXIS2);
            rawDataLength = nAxis1 * nAxis2 * 4;
            // Skip the data (for now)
            int pad = FitsUtil.padding(rawDataLength);
            bf.skip(rawDataLength + pad);
            compressedImageHDU = null;
        }
        if (wcsOverride != null) {
            String datasecString = wcsOverride.get("DATASEC").toString();
            datasec = computeDatasec(datasecString);
            pc1_1 = ((Number) wcsOverride.get("PC1_1" + wcsLetter)).doubleValue();
            pc2_2 = ((Number) wcsOverride.get("PC2_2" + wcsLetter)).doubleValue();
            pc1_2 = ((Number) wcsOverride.get("PC1_2" + wcsLetter)).doubleValue();
            pc2_1 = ((Number) wcsOverride.get("PC2_1" + wcsLetter)).doubleValue();
            crval1 = ((Number) wcsOverride.get("CRVAL1" + wcsLetter)).doubleValue();
            crval2 = ((Number) wcsOverride.get("CRVAL2" + wcsLetter)).doubleValue();
            channel = header.getIntValue("CHANNEL");
        } else {
            String datasecString = header.getStringValue("DATASEC");
            if (datasecString == null) {
                throw new IOException("Missing datasec for file: " + file);
            }
            datasec = computeDatasec(datasecString);
            pc1_1 = header.getDoubleValue("PC1_1" + wcsLetter);
            pc2_2 = header.getDoubleValue("PC2_2" + wcsLetter);
            pc1_2 = header.getDoubleValue("PC1_2" + wcsLetter);
            pc2_1 = header.getDoubleValue("PC2_1" + wcsLetter);
            crval1 = header.getDoubleValue("CRVAL1" + wcsLetter);
            crval2 = header.getDoubleValue("CRVAL2" + wcsLetter);
            channel = header.getIntValue("CHANNEL");
        }
        ccdX = Integer.parseInt(ccdSlot.substring(1, 2));
        ccdY = Integer.parseInt(ccdSlot.substring(2, 3));
        wcsTranslation = new AffineTransform(pc1_1, pc2_1, pc1_2, pc2_2, crval1, crval2);
        wcsTranslation.translate(datasec.x + 0.5, datasec.y + 0.5);
        //wcsTranslation.translate(crval1, crval2);
        //wcsTranslation.scale(pc1_1, pc2_2);
        //System.out.printf("FILE %s CCDSLOT %s\n", file, ccdSlot);
        //System.out.printf("pc1_1=%3.3g pc2_2=%3.3g pc1_2=%3.3g pc2_1=%3.3g\n", pc1_1, pc2_2, pc1_2, pc2_1);
        //System.out.printf("qcs=%s\n", wcsTranslation);
        Point2D origin = wcsTranslation.transform(new Point(0, 0), null);
        Point2D corner = wcsTranslation.transform(new Point(datasec.width, datasec.height), null);
        double x = Math.min(origin.getX(), corner.getX());
        double y = Math.min(origin.getY(), corner.getY());
        double width = Math.abs(origin.getX() - corner.getX());
        double height = Math.abs(origin.getY() - corner.getY());
        wcs = new Rectangle2D.Double(x, y, width, height);
        //System.out.printf("wcs=%s\n", wcs);
    }

    private Rectangle computeDatasec(String datasecString) throws IOException, NumberFormatException {
        Matcher matcher = DATASET_PATTERN.matcher(datasecString);
        if (!matcher.matches()) {
            throw new IOException("Invalid datasec: " + datasecString);
        }
        int datasec1 = Integer.parseInt(matcher.group(1)) - 1;
        int datasec2 = Integer.parseInt(matcher.group(2));
        int datasec3 = Integer.parseInt(matcher.group(3)) - 1;
        int datasec4 = Integer.parseInt(matcher.group(4));
        return new Rectangle(datasec1, datasec3, datasec2 - datasec1, datasec4 - datasec3);
    }

    public int getImageSize() {
        return nAxis1 * nAxis2 * 4;
    }

    public int getDataSize() {
        return rawDataLength;
    }

    public File getFile() {
        return file;
    }

    public IntBuffer readData(BufferedFile bf) throws IOException, FitsException {
        if (isCompressed) {
            synchronized (bf) {
                // FIXME: This internally uses the bf passed in to the constructor, which may have been closed by now
                return (IntBuffer) ((CompressedImageHDU) compressedImageHDU).getUncompressedData();
            }
        } else {
            ByteBuffer bb = ByteBuffer.allocateDirect(rawDataLength);
            FileChannel fileChannel = bf.getChannel();
            int len = fileChannel.read(bb, seekPosition);
            if (bb.remaining() != 0) {
                throw new IOException("Unexpected length " + len);
            }
            bb.flip();
            return bb.asIntBuffer();
        }
    }

    public int getNAxis1() {
        return nAxis1;
    }

    public int getNAxis2() {
        return nAxis2;
    }

    public AffineTransform getWCSTranslation(boolean includeOverscan) {
        if (includeOverscan) {
            int parallel_overscan = nAxis2 - datasec.height;
            int serial_overscan = nAxis1 - datasec.width;
            AffineTransform wcsTranslation = new AffineTransform();
            int c = channel > 8 ? 16 - channel : channel - 1;
            wcsTranslation.translate(crval1 + ccdY * serial_overscan * 8 + (c % 8) * serial_overscan, crval2 - parallel_overscan * pc2_2);
            wcsTranslation.scale(pc1_1, pc2_2);
            return wcsTranslation;
        } else {
            return wcsTranslation;
        }
    }

    public Rectangle getDataSec() {
        return datasec;
    }

    boolean intersects(Rectangle sourceRegion) {
        return wcs.intersects(sourceRegion);
    }

    @Override
    public String toString() {
        return "Segment{" + "file=" + file + ", seekPosition=" + seekPosition + ", wcsLetter=" + wcsLetter + '}';
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 71 * hash + Objects.hashCode(this.file);
        hash = 71 * hash + (int) (this.seekPosition ^ (this.seekPosition >>> 32));
        hash = 71 * hash + Objects.hashCode(this.wcsLetter);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Segment other = (Segment) obj;
        if (this.seekPosition != other.seekPosition) {
            return false;
        }
        if (!Objects.equals(this.wcsLetter, other.wcsLetter)) {
            return false;
        }
        return Objects.equals(this.file, other.file);
    }

}

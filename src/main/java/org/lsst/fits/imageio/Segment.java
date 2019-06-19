package org.lsst.fits.imageio;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nom.tam.fits.Header;
import nom.tam.fits.header.Standard;

/**
 * Represents one segment (amplifier) of a FITS file
 *
 * @author tonyj
 */
public class Segment {

    private static final Pattern DATASET_PATTERN = Pattern.compile("\\[(\\d+):(\\d+),(\\d+):(\\d+)\\]");

    private final File file;
    private final long seekPosition;
    private Rectangle2D.Double wcs;
    private AffineTransform wcsTranslation;
    private Rectangle datasec;
    private final int nAxis1;
    private final int nAxis2;
    private double crval1;
    private double crval2;
    private double pc1_1;
    private double pc2_2;
    private int channel;
    private int ccdX;
    private int ccdY;

    public Segment(Header header, File file, long seekPointer, String ccdSlot) throws IOException {
        this.file = file;
        this.seekPosition = seekPointer;
        nAxis1 = header.getIntValue(Standard.NAXIS1);
        nAxis2 = header.getIntValue(Standard.NAXIS2);
        String datasetString = header.getStringValue("DATASEC");
        Matcher matcher = DATASET_PATTERN.matcher(datasetString);
        if (!matcher.matches()) {
            throw new IOException("Invalid datasec: " + datasetString);
        }
        int datasec1 = Integer.parseInt(matcher.group(1))-1;
        int datasec2 = Integer.parseInt(matcher.group(2));
        int datasec3 = Integer.parseInt(matcher.group(3))-1;
        int datasec4 = Integer.parseInt(matcher.group(4));
        datasec = new Rectangle(datasec1, datasec3, datasec2 - datasec1, datasec4 - datasec3);
        // Hard wired to use WCSQ coordinates (raft level coordinates)
        pc1_1 = header.getDoubleValue("PC1_1Q");
        pc2_2 = header.getDoubleValue("PC2_2Q");
        crval1 = header.getDoubleValue("CRVAL1Q");
        crval2 = header.getDoubleValue("CRVAL2Q");
        channel = header.getIntValue("CHANNEL");
        ccdX = Integer.parseInt(ccdSlot.substring(1,2));
        ccdY = Integer.parseInt(ccdSlot.substring(2,3));
        wcsTranslation = new AffineTransform();
        wcsTranslation.translate(crval1, crval2);
        wcsTranslation.scale(pc1_1, pc2_2);
        Point2D origin = wcsTranslation.transform(new Point(datasec.x, datasec.y), null);
        Point2D corner = wcsTranslation.transform(new Point(datasec.x + datasec.width, datasec.y + datasec.height), null);
        double x = Math.min(origin.getX(), corner.getX());
        double y = Math.min(origin.getY(), corner.getY());
        double width = Math.abs(origin.getX() - corner.getX());
        double height = Math.abs(origin.getY() - corner.getY());
        wcs = new Rectangle2D.Double(x, y, width, height);
    }

    public int getDataSize() {
        return nAxis1 * nAxis2 * 4;
    }

    File getFile() {
        return file;
    }

    public long getSeekPosition() {
        return seekPosition;
    }

    public int getNAxis1() {
        return nAxis1;
    }

    public int getNAxis2() {
        return nAxis2;
    }

    AffineTransform getWCSTranslation(boolean includeOverscan) {
        if (includeOverscan) {
            int parallel_overscan = nAxis2 - datasec.height;
            int serial_overscan = nAxis1 - datasec.width;
            AffineTransform wcsTranslation = new AffineTransform();
            int c = channel>8 ? 16 - channel : channel-1;
            wcsTranslation.translate(crval1 + ccdY*serial_overscan*8 + (c%8)*serial_overscan, crval2 - parallel_overscan*pc2_2);
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
        return "Segment{" + "file=" + file + ", seekPosition=" + seekPosition + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.file);
        hash = 79 * hash + (int) (this.seekPosition ^ (this.seekPosition >>> 32));
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
        return Objects.equals(this.file, other.file);
    }

    
}

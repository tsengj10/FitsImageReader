package org.lsst.fits.imageio;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nom.tam.fits.Header;
import nom.tam.fits.header.Standard;

/**
 * Represents one segment (amplifier) of a FITS file
 *
 * @author tonyj
 */
class Segment {

    private static final Pattern DATASET_PATTERN = Pattern.compile("\\[(\\d+):(\\d+),(\\d+):(\\d+)\\]");

    private final File file;
    private final long seekPosition;
    private Rectangle2D.Double wcs;
    private AffineTransform wcsTranslation;
    private Rectangle datasec;
    private final int nAxis1;
    private final int nAxis2;

    Segment(Header header, File file, long seekPointer) throws IOException {
        this.file = file;
        this.seekPosition = seekPointer;
        nAxis1 = header.getIntValue(Standard.NAXIS1);
        nAxis2 = header.getIntValue(Standard.NAXIS2);
        String datasetString = header.getStringValue("DATASEC");
        Matcher matcher = DATASET_PATTERN.matcher(datasetString);
        if (!matcher.matches()) {
            throw new IOException("Invalid datasec: " + datasetString);
        }
        int datasec1 = Integer.parseInt(matcher.group(1));
        int datasec2 = Integer.parseInt(matcher.group(2));
        int datasec3 = Integer.parseInt(matcher.group(3));
        int datasec4 = Integer.parseInt(matcher.group(4));
        //TODO: Check +1
        datasec = new Rectangle(datasec1, datasec3, datasec2 - datasec1 + 1, datasec4 - datasec3 + 1);
        // Hard wired to use WCSQ coordinates (raft level coordinates)
        double pc1_1 = header.getDoubleValue("PC1_1Q");
        double pc2_2 = header.getDoubleValue("PC2_2Q");
        double crval1 = header.getDoubleValue("CRVAL1Q");
        double crval2 = header.getDoubleValue("CRVAL2Q");
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

    int getDataSize() {
        return nAxis1 * nAxis2 * 4;
    }

    File getFile() {
        return file;
    }

    long getSeekPosition() {
        return seekPosition;
    }

    public int getNAxis1() {
        return nAxis1;
    }

    public int getNAxis2() {
        return nAxis2;
    }

    public AffineTransform getWCSTranslation() {
        return wcsTranslation;
    }

    public Rectangle getDataSec() {
        return datasec;
    }

    public Rectangle.Double getWCS() {
        return wcs;
    }

    @Override
    public String toString() {
        return "Segment{" + "file=" + file + ", seekPosition=" + seekPosition + '}';
    }
}

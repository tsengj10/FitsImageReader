package org.lsst.fits.imageio;

import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

/**
 *
 * @author tonyj
 */
public class CameraFocalPlaneImageReaderSpi extends ImageReaderSpi {

    public CameraFocalPlaneImageReaderSpi() {
        super("LSST", "1.0-SNAPSHOT", new String[]{"FP"}, new String[]{".fp"}, new String[]{"image/fp"},
                CameraImageReader.class.getName(),
                new Class[]{ImageInputStream.class}, null, false, null, null, null, null, false,
                null, null, null, null);
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        return (source instanceof ImageInputStream);
    }

    @Override
    public ImageReader createReaderInstance(Object extension) throws IOException {
        return new CameraImageReader(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "LSST Camera Focal-Plane Image";
    }
}

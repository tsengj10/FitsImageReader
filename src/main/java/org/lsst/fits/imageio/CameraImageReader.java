package org.lsst.fits.imageio;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import nom.tam.fits.FitsFactory;

/**
 *
 * @author tonyj
 */
public class CameraImageReader extends ImageReader {

    private static final Logger LOG = Logger.getLogger(CameraImageReader.class.getName());
    private static final CachingReader reader = new CachingReader();
    private static final ImageTypeSpecifier GRAYSCALE = ImageTypeSpecifier.createGrayscale(16, DataBuffer.TYPE_USHORT, false);

    static {
        FitsFactory.setUseHierarch(true);
    }

    public CameraImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    @Override
    public void reset() {
        super.reset();
    }

    @Override
    public ImageReadParam getDefaultReadParam() {
        return super.getDefaultReadParam();
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);

    }

    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
        return 1;
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        return 3 * 4096 + 4 * 100;
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        return 3 * 4096 + 4 * 100;
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        return Collections.singleton(GRAYSCALE).iterator();
    }

    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
        return null;
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {

        if (param != null) {
            LOG.log(Level.INFO, "read called sourceRegion={0}", param.getSourceRegion());
            LOG.log(Level.INFO, "read called destination={0}", param.getDestination());
            LOG.log(Level.INFO, "read called renderSize={0}", param.getSourceRenderSize());
            LOG.log(Level.INFO, "read called xsub={0}", param.getSourceXSubsampling());
            LOG.log(Level.INFO, "read called ysub={0}", param.getSourceYSubsampling());
            LOG.log(Level.INFO, "read called xoffsub={0}", param.getSubsamplingXOffset());
            LOG.log(Level.INFO, "read called yoffsub={0}", param.getSubsamplingYOffset());
        }
        BufferedImage result;
        Graphics2D g;
        if (param == null || param.getSourceRegion() == null) {
            result = GRAYSCALE.createBufferedImage(getWidth(0), getHeight(0));
            g = result.createGraphics();
            g.translate(0,getHeight(0));
            g.scale(1,-1);
        } else {
            result = GRAYSCALE.createBufferedImage((int) param.getSourceRegion().getWidth(), (int) param.getSourceRegion().getHeight()); 
            g = result.createGraphics();
            g.translate(0,param.getSourceRegion().getHeight());
            g.scale(1,-1);
            g.translate(-param.getSourceRegion().getX(), -param.getSourceRegion().getY());
        }
        try {
            reader.readImage((ImageInputStream) getInput(), param, g);
            return result;
        } finally {
            g.dispose();
        }
    }
}

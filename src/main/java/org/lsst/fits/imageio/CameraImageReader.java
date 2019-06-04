package org.lsst.fits.imageio;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
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
import org.lsst.fits.imageio.cmap.RGBColorMap;
import org.lsst.fits.imageio.cmap.SAOColorMap;

/**
 *
 * @author tonyj
 */
public class CameraImageReader extends ImageReader {

    private static final Logger LOG = Logger.getLogger(CameraImageReader.class.getName());
    private static final CachingReader READER = new CachingReader();
    public static final ImageTypeSpecifier IMAGE_TYPE = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
    public static final RGBColorMap DEFAULT_COLOR_MAP = new SAOColorMap(256, "grey.sao");
    
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
    public FITSImageReadParam getDefaultReadParam() {
        return new FITSImageReadParam();
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
        return Collections.singleton(IMAGE_TYPE).iterator();
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
        Rectangle sourceRegion = param == null ? null : param.getSourceRegion();
        RGBColorMap cmap = param instanceof FITSImageReadParam ? ((FITSImageReadParam) param).getColorMap() : DEFAULT_COLOR_MAP;

        // Note, graphics and source region being flipped in Y to comply with Camera visualization standards
        if (sourceRegion == null) {
            result = IMAGE_TYPE.createBufferedImage(getWidth(0), getHeight(0));
            g = result.createGraphics();
            g.translate(0,getHeight(0));
            g.scale(1,-1);
        } else {
            sourceRegion = new Rectangle(sourceRegion.x, getHeight(0)-sourceRegion.y-sourceRegion.height, sourceRegion.width, sourceRegion.height);
            result = IMAGE_TYPE.createBufferedImage((int) sourceRegion.getWidth(), (int) sourceRegion.getHeight());
            g = result.createGraphics();
            g.translate(0,sourceRegion.getHeight());
            g.scale(1,-1);
            g.translate(-sourceRegion.getX(), -sourceRegion.getY());
        }
        try {
            READER.readImage((ImageInputStream) getInput(), sourceRegion, g, cmap);
            return result;
        } finally {
            g.dispose();
        }
    }
}

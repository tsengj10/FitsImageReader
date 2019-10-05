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
import org.lsst.fits.imageio.bias.BiasCorrection;
import org.lsst.fits.imageio.bias.NullBiasCorrection;
import org.lsst.fits.imageio.bias.SerialParallelBiasCorrection;
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
    public static final BiasCorrection DEFAULT_BIAS_CORRECTION = new NullBiasCorrection();
    
    public enum ImageType { FOCAL_PLANE, RAFT };

    private boolean showBiasRegion;
    private ImageType imageType;
    
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
        final FITSImageReadParam fitsImageReadParam = new FITSImageReadParam();
        fitsImageReadParam.setBiasCorrection(new SerialParallelBiasCorrection());
        return fitsImageReadParam;
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        int lines = READER.preReadImage((ImageInputStream) input);
        imageType = lines>9 ? ImageType.FOCAL_PLANE : ImageType.RAFT;
        
    }

    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
        return 1;
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        if (imageType == ImageType.RAFT) {
            return 3 * 4096 + 4 * 100 + (showBiasRegion ? 1600 : 0);
        } else {
            return 15 * 4096 + 16 * 100;
        }
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        if (imageType == ImageType.RAFT) {
            return 3 * 4096 + 4 * 100;
        } else {
            return 15 * 4096 + 16 * 100;            
        }
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

        int xSubSampling = 1;
        int ySubSampling = 1;
        if (param != null) {
            LOG.log(Level.INFO, "read called sourceRegion={0}", param.getSourceRegion());
            LOG.log(Level.INFO, "read called destination={0}", param.getDestination());
            LOG.log(Level.INFO, "read called renderSize={0}", param.getSourceRenderSize());
            LOG.log(Level.INFO, "read called xsub={0}", param.getSourceXSubsampling());
            LOG.log(Level.INFO, "read called ysub={0}", param.getSourceYSubsampling());
            LOG.log(Level.INFO, "read called xoffsub={0}", param.getSubsamplingXOffset());
            LOG.log(Level.INFO, "read called yoffsub={0}", param.getSubsamplingYOffset());
            xSubSampling = param.getSourceXSubsampling();
            ySubSampling = param.getSourceYSubsampling();
        }
        BufferedImage result;
        Graphics2D g;
        RGBColorMap cmap;
        BiasCorrection bc;
        char wcsString;
        Rectangle sourceRegion = param == null ? null : param.getSourceRegion();
        if (param instanceof FITSImageReadParam) {
            FITSImageReadParam fitsParam = (FITSImageReadParam) param;
            cmap = fitsParam.getColorMap();
            bc = fitsParam.getBiasCorrection();
            showBiasRegion = fitsParam.isShowBiasRegions();
            wcsString = fitsParam.getWCSString();
        } else {
            cmap = DEFAULT_COLOR_MAP;
            bc = DEFAULT_BIAS_CORRECTION;
            showBiasRegion = false;
            wcsString = ' ';
        }
        if (wcsString == ' ') wcsString = imageType == ImageType.FOCAL_PLANE ? 'E' : 'Q';
        
        // Note, graphics and source region being flipped in Y to comply with Camera visualization standards
        if (sourceRegion == null) {
            result = IMAGE_TYPE.createBufferedImage(getWidth(0)/xSubSampling, getHeight(0)/ySubSampling);
            g = result.createGraphics();
            g.translate(0,getHeight(0)/ySubSampling);
            g.scale(1.0/xSubSampling,-1.0/ySubSampling);
        } else {
            sourceRegion = new Rectangle(sourceRegion.x, getHeight(0)-sourceRegion.y-sourceRegion.height, sourceRegion.width, sourceRegion.height);
            result = IMAGE_TYPE.createBufferedImage((int) sourceRegion.getWidth(), (int) sourceRegion.getHeight());
            g = result.createGraphics();
            g.translate(0,sourceRegion.getHeight());
            g.scale(1,-1);
            g.translate(-sourceRegion.getX(), -sourceRegion.getY());
        }
        try {
            READER.readImage((ImageInputStream) getInput(), sourceRegion, g, cmap, bc, showBiasRegion, wcsString);
            return result;
        } finally {
            g.dispose();
        }
    }
}

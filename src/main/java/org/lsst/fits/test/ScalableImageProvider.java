package org.lsst.fits.test;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

/**
 *
 * @author tonyj
 * @param <T>
 */
public abstract class ScalableImageProvider<T> {

    private final WritableRaster rawRaster;
    private final int bitpix;
    private final int bZero;
    private final int bScale;
    private final ScalingUtils utils;

    public enum Scaling {
        LINEAR, LOG, HIST
    }

    ScalableImageProvider(int bitpix, int bZero, int bScale, ScalingUtils<T> utils, WritableRaster rawRaster) {
        this.bitpix = bitpix;
        this.bZero = bZero;
        this.bScale = bScale;
        this.rawRaster = rawRaster;
        this.utils = utils;
    }

    public WritableRaster getRawRaster() {
        return rawRaster;
    }

    public ScalingUtils<T> getScalingUtils() {
        return utils;
    }

    public int getBitpix() {
        return bitpix;
    }
    
    abstract BufferedImage createScaledImage(Scaling scaling);
}

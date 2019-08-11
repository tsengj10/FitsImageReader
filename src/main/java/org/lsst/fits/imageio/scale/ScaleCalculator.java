package org.lsst.fits.imageio.scale;

import org.lsst.fits.imageio.RawData;

/**
 *
 * @author tonyj
 */
public interface ScaleCalculator {
    double[] computeScale(RawData data);
}

package org.lsst.fits.imageio;

import java.util.logging.Logger;

/**
 *
 * @author tonyj
 * @param <T>
 */
class ScalingUtils<T> {

    private static final Logger LOG = Logger.getLogger(ScalingUtils.class.getName());

    private final int[] counts;
    private int min;
    private int max;

    ScalingUtils(int[] counts) {
        this.counts = counts;
        computeMinMax();
    }

    private void computeMinMax() {
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                min = i;
                break;
            }
        }
        for (int i = counts.length - 1; i >= 0; i--) {
            if (counts[i] > 0) {
                max = i;
                break;
            }
        }
        LOG.fine(() -> String.format("min=%d max=%d", min, max));
    }

    int[] computeCDF() {
        int[] cdf = new int[counts.length];
        int cum = 0;
        for (int i = min; i <= max; i++) {
            cum += counts[i];
            cdf[i] = cum;
        }
        return cdf;
    }

    int getMax() {
        return max;
    }

    int getMin() {
        return min;
    }
}

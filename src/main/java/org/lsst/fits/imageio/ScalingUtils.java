package org.lsst.fits.imageio;

import java.util.logging.Logger;

/**
 *
 * @author tonyj
 * @param <T>
 */
public class ScalingUtils<T> {

    private static final Logger LOG = Logger.getLogger(ScalingUtils.class.getName());

    private final int[] counts;
    private int min;
    private int max;

    public ScalingUtils(int[] counts) {
        this.counts = counts;
        computeMinMax();
    }
    
    ScalingUtils(long[] counts) {
        this.counts = new int[counts.length];
        for (int i=0; i<counts.length; i++) {
            this.counts[i] = (int) (counts[i]/512); 
        }
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

    public int[] computeCDF() {
        int[] cdf = new int[counts.length];
        int cum = 0;
        for (int i = min; i <= max; i++) {
            cum += counts[i];
            cdf[i] = cum;
        }
        return cdf;
    }

    public int getMax() {
        return max;
    }

    public int getMin() {
        return min;
    }
}

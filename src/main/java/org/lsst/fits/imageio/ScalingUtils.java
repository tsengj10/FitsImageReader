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
    private int lowestBin;
    private int highestBin;

    public ScalingUtils(int[] counts) {
        this.counts = counts;
        computeMinMax();
    }
    /**
     * Build a scaling utils from a set of long counts (typically representing the full 
     * focal plane). Since the longs may be too large to fit in the int[] we normally use
     * the values have to be scaled, but we need to be careful not to miss any occupied bins when
     * computing the highest and lowest bin.
     * @param longCounts 
     */
    ScalingUtils(long[] longCounts) {
        this.counts = new int[longCounts.length];
        for (int i=0; i<longCounts.length; i++) {
            this.counts[i] = (int) (longCounts[i]/100); 
        }
        for (int i = 0; i < longCounts.length; i++) {
            if (longCounts[i] > 0) {
                lowestBin = i;
                break;
            }
        }
        for (int i = longCounts.length - 1; i >= 0; i--) {
            if (longCounts[i] > 0) {
                highestBin = i;
                break;
            }
        }
    }

    private void computeMinMax() {
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                lowestBin = i;
                break;
            }
        }
        for (int i = counts.length - 1; i >= 0; i--) {
            if (counts[i] > 0) {
                highestBin = i;
                break;
            }
        }
        LOG.fine(() -> String.format("min=%d max=%d", lowestBin, highestBin));
    }

    public int[] computeCDF() {
        int[] cdf = new int[counts.length];
        int cum = 0;
        for (int i = lowestBin; i <= highestBin; i++) {
            cum += counts[i];
            cdf[i] = cum;
        }
//        int total = cdf[highestBin];
//        int n05 = 0;
//        int n95 = 0;
//        for (int i = lowestBin; i <= highestBin; i++) {
//            if (cdf[i]>=total/20) {
//                n05 = i;
//                break;
//            }
//        }
//        for (int i = highestBin; i >= lowestBin; i--) {
//            if (cdf[i]<=total*19/20) {
//                n95 = i;
//                break;
//            }
//        }
//        for (int i = lowestBin; i <= highestBin; i++) {
//            if (i<n05) {
//                cdf[i] = 0;
//            } else if (i>n95) {
//                cdf[i] = total;
//            } else {
//                cdf[i] = total * (i - n05) / (n95 - n05);
//            }
//        }     
        return cdf;
    }

    public int getHighestOccupiedBin() {
        return highestBin;
    }

    public int getLowestOccupiedBin() {
        return lowestBin;
    }
    
    public int getCount(int bin) {
        return counts[bin];
    }
}

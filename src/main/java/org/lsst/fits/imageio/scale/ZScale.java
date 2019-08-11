package org.lsst.fits.imageio.scale;

import java.util.Arrays;
import java.util.BitSet;
import org.lsst.fits.imageio.RawData;

/**
 * Implementation of the IRAF zscale algorithm.
 *
 * The zscale algorithm is designed to display the image values near the median
 * image value without the time consuming process of computing a full image
 * histogram. This is particularly useful for astronomical images which
 * generally have a very peaked histogram corresponding to the background sky in
 * direct imaging or the continuum in a two dimensional spectrum.
 *
 * The sample of pixels, specified by values greater than zero in the sample
 * mask zmask or by an image section, is selected up to a maximum of nsample
 * pixels. If a bad pixel mask is specified by the bpmask parameter then any
 * pixels with mask values which are greater than zero are not counted in the
 * sample. Only the first pixels up to the limit are selected where the order is
 * by line beginning from the first line. If no mask is specified then a grid of
 * pixels with even spacing along lines and columns that make up a number less
 * than or equal to the maximum sample size is used. If a contrast of zero is
 * specified (or the zrange flag is used and the image does not have a valid
 * minimum/maximum value) then the minimum and maximum of the sample is used for
 * the intensity mapping range.
 *
 * If the contrast is not zero the sample pixels are ranked in brightness to
 * form the function I(i) where i is the rank of the pixel and I is its value.
 * Generally the midpoint of this function (the median) is very near the peak of
 * the image histogram and there is a well defined slope about the midpoint
 * which is related to the width of the histogram. At the ends of the I(i)
 * function there area few very bright and dark pixels due to objects and
 * defects in the field. To determine the slope a linear function is fit with
 * iterative rejection;
 *
 * I(i) = intercept + slope * (i - midpoint)
 *
 * If more than half of the points are rejected then there is no well defined
 * slope and the full range of the sample defines z1 and z2. Otherwise the
 * endpoints of the linear function are used (provided they are within the
 * original range of the sample):
 *
 * z1 = I(midpoint) + (slope / contrast) * (1 - midpoint) z2 = I(midpoint) +
 * (slope / contrast) * (npoints - midpoint)
 *
 * As can be seen, the parameter contrast may be used to adjust the contrast
 * produced by this algorithm.
 *
 * @author tonyj
 */
public class ZScale implements ScaleCalculator {

    private static final double MAX_REJECT = 0.5;
    private static final int MIN_NPIXELS = 5;
    private static final double KREJ = 2.5;
    private static final int MAX_ITERATIONS = 5;

    private final double contrast;
    private final int nSamples;

    ZScale(int nSamples, double contrast) {
        this.nSamples = nSamples;
        this.contrast = contrast;
    }

    public double[] computeScale(RawData data) {
        int[] samples = sample(data, nSamples);
        return samples(samples);
    }

    private int[] sample(RawData data, int nSamples) {
        int nc = data.getSegment().getNAxis1();
        int nl = data.getSegment().getNAxis2();
        int stride = (int) Math.max(1.0, Math.sqrt((nc - 1) * (nl - 1) / (float) (nSamples)));
        return null;
    }

    private double[] samples(int[] samples) {
        Arrays.sort(samples);
        int npix = samples.length;
        double zmin = samples[0];
        double zmax = samples[npix - 1];
        int centerPixel = (npix - 1) / 2;
        // Compute median
        double median;
        if (npix % 2 == 1) {
            median = samples[centerPixel];
        } else {
            median = 0.5 * (samples[centerPixel] + samples[centerPixel + 1]);
        }
        // Fit a line to the sorted array of samples
        int minpix = (int) Math.max(MIN_NPIXELS, npix * MAX_REJECT);
        int ngrow = (int) Math.max(1, npix * 0.01);
        Line line = fitLine(samples, KREJ, ngrow, MAX_ITERATIONS);
        if (line.getNGoodPix() < minpix) {
            return new double[]{zmin, zmax};
        } else {
            double zslope = line.getZSlope();
            if (contrast > 0) {
                zslope /= contrast;
            }
            double z1 = Math.max(zmin, median - (centerPixel - 1) * zslope);
            double z2 = Math.min(zmax, median + (npix - centerPixel) * zslope);
            return new double[]{z1, z2};
        }
    }

    private Line fitLine(int[] samples, double kReject, int ngrow, int maxIterations) {
        final int npix = samples.length;
        if (npix <= 1) {
            return new Line(npix, 0, 1);
        }

        int ngoodpix = npix;
        int minpix = (int) Math.max(MIN_NPIXELS, npix * MAX_REJECT);
        int last_ngoodpix = npix + 1;

        BitSet badpix = new BitSet(npix);

        for (int iter = 0; iter < maxIterations; iter++) {
            if (ngoodpix >= last_ngoodpix || ngoodpix < minpix) {
                break;
            }
            double sumx = 0;
            double sumxx = 0;
            double sumxy = 0;
            double sumy = 0;
            int sum = 0;
            for (int i = 0; i < npix; i++) {
                if (badpix.get(i)) {
                    continue;
                }
                double x = -1.0 + i * 2.0 / (npix - 1);
                sumx += x;
                sumxx += x * x;
                sumxy += x * samples[i];
                sumy += samples[i];
                sum++;
            }
            double delta = sum * sumxx - sumx * sumx;
            // Slope and intercept
            double intercept = (sumxx * sumy - sumx * sumxy) / delta;
            double slope = (sum * sumxy - sumx * sumy) / delta;

            double[] meanSigma = computeSigma(badpix, samples, intercept, slope);
            double threshold = meanSigma[1] * kReject;
            // Detect and reject pixels further than k*sigma from the fitted line
            for (int i = 0; i < npix; i++) {
                if (!badpix.get(i)) {
                    continue;
                }
                double x = -1.0 + i * 2.0 / (npix - 1);
                double z = Math.abs(samples[i] - (intercept + x * slope));
                if (z>threshold) {
                    badpix.set(i);
                }
            }            
        }
        return null;
    }

    private double[] computeSigma(BitSet badpix, int[] samples, double intercept, double slope) {
        int npix = samples.length;
        double sumz = 0;
        double sumzz = 0;
        int goodPixels = 0;
        for (int i = 0; i < npix; i++) {
            if (!badpix.get(i)) {
                continue;
            }
            double x = -1.0 + i * 2.0 / (npix - 1);
            double z = samples[i] - (intercept + x * slope);
            sumz += z;
            sumzz += z * z;
            goodPixels++;
        }
        switch (goodPixels) {
            case 0:
                return new double[]{Double.NaN, Double.NaN};
            case 1:
                return new double[]{sumz, Double.NaN};
            default:
                double mean = sumz / goodPixels;
                double temp = (sumzz / (goodPixels - 1) - sumz * sumz /
                        (goodPixels * (goodPixels - 1)));
                double sigma = temp < 0 ? 0.0 : Math.sqrt(temp);
                return new double[]{mean,sigma};
        }
    }

    private static class Line {

        public Line() {
        }

        private Line(int length, int i, int i0) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        private int getNGoodPix() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        private double getZSlope() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }

}

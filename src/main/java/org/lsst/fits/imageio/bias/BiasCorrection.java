package org.lsst.fits.imageio.bias;

import java.nio.IntBuffer;
import org.lsst.fits.imageio.Segment;

/**
 *
 * @author tonyj
 */
public interface BiasCorrection {

    CorrectionFactors compute(IntBuffer data, Segment segment);

    public interface CorrectionFactors {

        public int correctionFactor(int x, int y);

    }
}

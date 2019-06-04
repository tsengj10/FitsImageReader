package org.lsst.fits.imageio;

import javax.imageio.ImageReadParam;
import org.lsst.fits.imageio.cmap.RGBColorMap;
import org.lsst.fits.imageio.cmap.SAOColorMap;

/**
 *
 * @author tonyj
 */
public class FITSImageReadParam extends ImageReadParam {
    private RGBColorMap colorMap = CameraImageReader.DEFAULT_COLOR_MAP;

    public RGBColorMap getColorMap() {
        return colorMap;
    }

    public void setColorMap(RGBColorMap colorMap) {
        this.colorMap = colorMap;
    }
    
}

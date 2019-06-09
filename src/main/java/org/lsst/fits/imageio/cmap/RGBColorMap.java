package org.lsst.fits.imageio.cmap;

import java.awt.image.ByteLookupTable;
import java.awt.image.LookupOp;

/**
 *
 * @author tonyj
 */
public abstract class RGBColorMap {

    private final int size;
    public RGBColorMap(int size) {
       this.size = size; 
    }
    
    public abstract int getRGB(int value);

    public int getSize() {
        return size;
    }

    public LookupOp getLookupOp() {
        byte[][] data  = new byte[3][size];
        for (int i=0; i<size;i++) {
            int rgb = getRGB(i);
            data[0][i] = (byte) ((rgb>>16) & 0xff);
            data[1][i] = (byte) ((rgb>>8) & 0xff);
            data[2][i] = (byte) (rgb & 0xff);
        }
        ByteLookupTable table = new ByteLookupTable(0, data);
        return new LookupOp(table, null);
    }
    
    
}

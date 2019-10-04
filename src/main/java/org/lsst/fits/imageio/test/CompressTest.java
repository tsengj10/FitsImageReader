package org.lsst.fits.imageio.test;

import java.io.IOException;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.ImageData;
import nom.tam.fits.ImageHDU;
import nom.tam.image.compression.hdu.CompressedImageHDU;

/**
 *
 * @author tonyj
 */
public class CompressTest {
   public static void main(String[] args) throws FitsException, IOException {
       Fits fits = new Fits("/home/tonyj/Data/compress/MC_C_20190410_000019_R22_S11.fits");
       fits.readHDU();
       CompressedImageHDU hdu1 = (CompressedImageHDU) fits.readHDU();
       ImageHDU imageHDU = hdu1.asImageHDU();
       imageHDU.getHeader().dumpHeader(System.out);
       ImageData data = imageHDU.getData();
       System.out.println(data.getData());
   }
}

package org.lsst.fits.imageio.wcs;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author tonyj
 */
public class WCSReaderTest {
    
    @Test
    public void testRead() throws IOException {
        InputStream resourceAsStream = WCSReaderTest.class.getResourceAsStream("keywords_itl_R44_LCA-13381B.wcs");
        assertNotNull(resourceAsStream);
        WCSReader wcsReader = new WCSReader(resourceAsStream);
        System.out.println(wcsReader.getWCSInfo());
    }
    
}

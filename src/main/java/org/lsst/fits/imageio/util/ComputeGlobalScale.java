package org.lsst.fits.imageio.util;

import java.awt.Rectangle;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import org.lsst.fits.imageio.CachingReader;
import org.lsst.fits.imageio.RawData;
import org.lsst.fits.imageio.Segment;

/**
 *
 * @author tonyj
 */
public class ComputeGlobalScale {
    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        CachingReader reader = new CachingReader();
        File file = new File(args[0]);
        ImageInputStream in = new FileImageInputStream(file);
        List<Segment> segments = reader.readSegments(in, 'Q');
        long[] count = new long[1 << 18];
        for(Segment segment : segments) {
            RawData rawData = reader.getRawData(segment);
            IntBuffer intBuffer = (IntBuffer) rawData.getBuffer();        
            Rectangle datasec = segment.getDataSec();
            // Note: This is hardwired for Camera (18 bit) data
            for (int x = datasec.x; x < datasec.width + datasec.x; x++) {
                for (int y = datasec.y; y < datasec.height + datasec.y; y++) {
                    count[intBuffer.get(x + y * segment.getNAxis1())]++;
                }
            }
        }
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0]+".counts")))) {
            for (long i : count) {
                out.writeLong(i);
            }
        }
    }
}

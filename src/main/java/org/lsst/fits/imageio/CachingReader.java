package org.lsst.fits.imageio;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import javax.imageio.stream.ImageInputStream;
import nom.tam.fits.FitsUtil;
import nom.tam.fits.Header;
import nom.tam.fits.TruncatedFileException;
import nom.tam.util.BufferedFile;
import org.lsst.fits.test.ScalingUtils;

/**
 *
 * @author tonyj
 */
public class CachingReader {

    private final AsyncLoadingCache<File, List<Segment>> segmentCache;
    private final LoadingCache<File, BufferedFile> openFileCache;
    private final AsyncLoadingCache<Segment, BufferedImage> bufferedImageCache;

    CachingReader() {
        openFileCache = Caffeine.newBuilder()
                .expireAfterAccess(1,TimeUnit.MINUTES)
                .removalListener((File file, BufferedFile bf, RemovalCause rc) -> {
                    try {
                        bf.close();
                    } catch (IOException ex) {
                        // Silently ignore for now.
                    }
                })
                .build((File file) -> new BufferedFile(file, "r"));

        segmentCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .buildAsync((File file) -> {
                    BufferedFile bf = openFileCache.get(file);
                    return readSegments(file, bf);
                });

        bufferedImageCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .buildAsync((Segment segment) -> {
                    BufferedFile bf = openFileCache.get(segment.getFile());
                    return readBufferedImage(segment, bf);
                });
    }

    void readImage(ImageInputStream fileInput, Rectangle sourceRegion, Graphics2D g) throws IOException {

        try {
            Queue<CompletableFuture> l1 = new ConcurrentLinkedQueue<>();
            Queue<CompletableFuture> l2 = new ConcurrentLinkedQueue<>();
            for (;;) {
                String line = fileInput.readLine();
                if (line == null) {
                    break;
                }
                CompletableFuture<List<Segment>> futureSegments = segmentCache.get(new File(line));
                l1.add(futureSegments.thenAccept((List<Segment> segments) -> {
                    List<Segment> segmentsToRead = computeSegmentsToRead(segments, sourceRegion);
                    for (Segment segment : segmentsToRead) {
                        CompletableFuture<BufferedImage> fbi = bufferedImageCache.get(segment);
                        l2.add(fbi.thenAccept((BufferedImage bi) -> {
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.transform(segment.getWCSTranslation());
                            Rectangle datasec = segment.getDataSec();
                            g2.drawImage(bi.getSubimage(datasec.x, datasec.y, datasec.width, datasec.height), 0, 0, null);
                            g2.dispose();
                        }));
                    }
                }));
            }
            CompletableFuture.allOf(l1.toArray(new CompletableFuture[l1.size()])).join();
            CompletableFuture.allOf(l2.toArray(new CompletableFuture[l2.size()])).join();
        } catch (CompletionException x) {
            Throwable cause = x.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new IOException("Unexpected exception during image reading", cause);
            }
        }
    }

    private List<Segment> computeSegmentsToRead(List<Segment> segments, Rectangle sourceRegion) {
        if (sourceRegion == null) {
            return segments;
        } else {
            List<Segment> segmentsToRead = new ArrayList<>();
            for (Segment segment : segments) {
                if (segment.getWCS().intersects(sourceRegion)) {
                    segmentsToRead.add(segment);
                }
            }
            return segmentsToRead;
        }
    }

    private static List<Segment> readSegments(File flle, BufferedFile bf) throws IOException, TruncatedFileException {
        List<Segment> result = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            Header header = new Header(bf);
            // Skip primary header, assumes file contains 16 image extensions
            if (i > 0) {
                Segment segment = new Segment(header, flle, bf.getFilePointer());
                // Skip the data (for now)
                final int dataSize = segment.getDataSize();
                int pad = FitsUtil.padding(dataSize);
                bf.skip(dataSize + pad);
                result.add(segment);
            }
        }
        return result;
    }

    private static BufferedImage readBufferedImage(Segment segment, BufferedFile bf) throws IOException {
        ByteBuffer bb = ByteBuffer.allocateDirect(segment.getDataSize());
        FileChannel channel = bf.getChannel();
        int len = channel.read(bb, segment.getSeekPosition());
        if (bb.remaining() != 0) {
            throw new IOException("Unexpected length " + len);
        }
        bb.flip();
        IntBuffer intBuffer = bb.asIntBuffer();
        // Note: This is hardwired for Camera (18 bit) data
        int[] count = new int[1 << 18];
        while (intBuffer.hasRemaining()) {
            count[intBuffer.get()]++;
        }
        ScalingUtils su = new ScalingUtils(count);
        final int min = su.getMin();
        final int max = su.getMax();
        int[] cdf = su.computeCDF();
        int range = cdf[max];

        // Scale data 
        WritableRaster raster = Raster.createInterleavedRaster(DataBuffer.TYPE_USHORT, segment.getNAxis1(), segment.getNAxis2(), 1, new Point(0, 0));
        DataBuffer db = raster.getDataBuffer();

        intBuffer.rewind();
        for (int p = 0; p < intBuffer.capacity(); p++) {
            db.setElem(p, 0xffff & (int) ((cdf[intBuffer.get(p)]) * 65536L / range));
        }
        ComponentColorModel cm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY),
                false, false, Transparency.OPAQUE,
                raster.getTransferType());
        return new BufferedImage(cm, raster, false, null);
    }
}

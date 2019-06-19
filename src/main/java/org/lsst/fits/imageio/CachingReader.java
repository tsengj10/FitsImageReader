package org.lsst.fits.imageio;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.LookupOp;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.imageio.stream.ImageInputStream;
import nom.tam.fits.FitsUtil;
import nom.tam.fits.Header;
import nom.tam.fits.TruncatedFileException;
import nom.tam.util.BufferedFile;
import org.lsst.fits.imageio.bias.BiasCorrection;
import org.lsst.fits.imageio.cmap.RGBColorMap;

/**
 *
 * @author tonyj
 */
public class CachingReader {

    private final AsyncLoadingCache<File, List<Segment>> segmentCache;
    private final LoadingCache<File, BufferedFile> openFileCache;
    private final AsyncLoadingCache<Segment, RawData> rawDataCache;
    private final AsyncLoadingCache<MultiKey<RawData, BiasCorrection>, BufferedImage> bufferedImageCache;
    private final LoadingCache<ImageInputStream, List<String>> linesCache;

    private static final Logger LOG = Logger.getLogger(CachingReader.class.getName());

    CachingReader() {
        openFileCache = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .removalListener((File file, BufferedFile bf, RemovalCause rc) -> {
                    try {
                        bf.close();
                    } catch (IOException ex) {
                        LOG.log(Level.WARNING, "Error closing file", ex);
                    }
                })
                .build((File file) -> new BufferedFile(file, "r"));

        segmentCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .buildAsync((File file) -> {
                    return Timed.execute(() -> {
                        BufferedFile bf = openFileCache.get(file);
                        return readSegments(file, bf);
                    }, "Loading %s took %dms", file);
                });

        rawDataCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .buildAsync((Segment segment) -> {
                    return Timed.execute(() -> {
                        BufferedFile bf = openFileCache.get(segment.getFile());
                        return readRawData(segment, bf);
                    }, "Reading raw daata for %s took %dms", segment);
                });

        bufferedImageCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .buildAsync((MultiKey<RawData, BiasCorrection> key) -> {
                    return Timed.execute(() -> {
                        return createBufferedImage(key.getKey1(), key.getKey2());
                    }, "Loading buffered image for segment %s took %dms", key.getKey1());
                });

        linesCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .build((ImageInputStream in) -> {
                    return Timed.execute(() -> {
                        List<String> lines = new ArrayList<>();
                        in.seek(0);
                        for (;;) {
                            String line = in.readLine();
                            if (line == null) {
                                break;
                            }
                            lines.add(line);
                        }
                        return lines;
                    }, "Read lines in %dms");
                });

    }

    @SuppressWarnings("null")
    void readImage(ImageInputStream fileInput, Rectangle sourceRegion, Graphics2D g, RGBColorMap cmap, BiasCorrection bc, boolean showBiasRegion) throws IOException {

        try {
            Queue<CompletableFuture> l1 = new ConcurrentLinkedQueue<>();
            Queue<CompletableFuture> l2 = new ConcurrentLinkedQueue<>();
            Queue<CompletableFuture> l3 = new ConcurrentLinkedQueue<>();
            List<String> lines = linesCache.get(fileInput);
            lines.stream().map((line) -> segmentCache.get(new File(line))).forEach((futureSegments) -> {
                l1.add(futureSegments.thenAccept((List<Segment> segments) -> {
                    List<Segment> segmentsToRead = computeSegmentsToRead(segments, sourceRegion);
                    segmentsToRead.forEach((segment) -> {
                        CompletableFuture<RawData> futureRawData = rawDataCache.get(segment);
                        l2.add(futureRawData.thenAccept((RawData rawData) -> {
                            CompletableFuture<BufferedImage> fbi = bufferedImageCache.get(new MultiKey(rawData, bc));
                            l3.add(fbi.thenAccept((BufferedImage bi) -> {
                                Timed.execute(() -> {
                                    Graphics2D g2 = (Graphics2D) g.create();
                                    g2.transform(segment.getWCSTranslation(showBiasRegion));
                                    Rectangle datasec = segment.getDataSec();
                                    BufferedImage subimage;
                                    if (showBiasRegion) {
                                        subimage = bi;
                                    } else {
                                        subimage = bi.getSubimage(datasec.x, datasec.y, datasec.width, datasec.height);
                                    }
                                    if (cmap != CameraImageReader.DEFAULT_COLOR_MAP) {
                                        LookupOp op = cmap.getLookupOp();
                                        subimage = op.filter(subimage, null);
                                    }
                                    g2.drawImage(subimage, 0, 0, null);
                                    g2.dispose();
                                    return null;
                                }, "drawImage for segment %s took %dms", segment);
                            }));
                        }));
                    });
                }));
            });
            LOG.log(Level.INFO, "Waiting for {0} segments", l1.size());
            CompletableFuture.allOf(l1.toArray(new CompletableFuture[l1.size()])).join();
            LOG.log(Level.INFO, "Waiting for {0} raw data", l2.size());
            CompletableFuture.allOf(l2.toArray(new CompletableFuture[l2.size()])).join();
            LOG.log(Level.INFO, "Waiting for {0} buffered image", l3.size());
            CompletableFuture.allOf(l3.toArray(new CompletableFuture[l3.size()])).join();
            LOG.log(Level.INFO, "Done waiting");
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
            return segments.stream()
                    .filter((segment) -> (segment.intersects(sourceRegion)))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private static List<Segment> readSegments(File flle, BufferedFile bf) throws IOException, TruncatedFileException {
        List<Segment> result = new ArrayList<>();
        String ccdSlot = null;
        for (int i = 0; i < 17; i++) {
            Header header = new Header(bf);
            // Skip primary header, assumes file contains 16 image extensions
            if (i == 0) {
                ccdSlot = header.getStringValue("CCDSLOT");
            }
            if (i > 0) {
                Segment segment = new Segment(header, flle, bf.getFilePointer(), ccdSlot);
                // Skip the data (for now)
                final int dataSize = segment.getDataSize();
                int pad = FitsUtil.padding(dataSize);
                bf.skip(dataSize + pad);
                result.add(segment);
            }
        }
        return result;
    }

    private static RawData readRawData(Segment segment, BufferedFile bf) throws IOException {
        ByteBuffer bb = ByteBuffer.allocateDirect(segment.getDataSize());
        FileChannel channel = bf.getChannel();
        int len = channel.read(bb, segment.getSeekPosition());
        if (bb.remaining() != 0) {
            throw new IOException("Unexpected length " + len);
        }
        bb.flip();
        return new RawData(segment, bb);
    }

    private static BufferedImage createBufferedImage(RawData rawData, BiasCorrection bc) throws IOException {
        IntBuffer intBuffer = rawData.asIntBuffer();
        Segment segment = rawData.getSegment();
        Rectangle datasec = segment.getDataSec();
        // Apply bias correction
        BiasCorrection.CorrectionFactors factors = bc.compute(intBuffer, segment);
        // Note: This is hardwired for Camera (18 bit) data
        int[] count = new int[1 << 18];
        for (int x = datasec.x; x < datasec.width + datasec.x; x++) {
            for (int y = datasec.y; y < datasec.height + datasec.y; y++) {
                count[Math.max(intBuffer.get(x + y * segment.getNAxis1()) - factors.correctionFactor(x, y), 0)]++;
            }
        }

        ScalingUtils su = new ScalingUtils(count);
        final int min = su.getMin();
        final int max = su.getMax();
        int[] cdf = su.computeCDF();
        int range = cdf[max];

        // Scale data 
        BufferedImage image = CameraImageReader.IMAGE_TYPE.createBufferedImage(segment.getNAxis1(), segment.getNAxis2());
        WritableRaster raster = image.getRaster();
        DataBuffer db = raster.getDataBuffer();
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(0, 0, datasec.x, segment.getNAxis2());
        graphics.setColor(Color.RED);
        graphics.fillRect(datasec.x+datasec.width, 0, segment.getNAxis1()-datasec.x-datasec.width , segment.getNAxis2());
        graphics.setColor(Color.BLUE);
        graphics.fillRect(datasec.x, datasec.y+datasec.height, datasec.width , segment.getNAxis2());
        
        for (int x = datasec.x; x < datasec.width + datasec.x; x++) {
            for (int y = datasec.y; y < datasec.height + datasec.y; y++) {
                int p = x + y * segment.getNAxis1();
                int rgb = CameraImageReader.DEFAULT_COLOR_MAP.getRGB(cdf[Math.max(intBuffer.get(p) - factors.correctionFactor(x, y), 0)] * 255 / range);
                db.setElem(p, rgb);
            }
        }
        return image;
    }
}

package org.lsst.fits.imageio;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.LookupOp;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.imageio.stream.ImageInputStream;
import nom.tam.fits.FitsException;
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

    private final AsyncLoadingCache<MultiKey<File, Character>, List<Segment>> segmentCache;
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
                .maximumSize(Integer.getInteger("org.lsst.fits.imageio.segmentCacheSize", 10_000))
                .recordStats()
                .buildAsync((MultiKey<File, Character> key) -> {
                    return Timed.execute(() -> {
                        BufferedFile bf = openFileCache.get(key.getKey1());
                        return readSegments(key.getKey1(), bf, key.getKey2());
                    }, "Loading %s took %dms", key.getKey1());
                });

        rawDataCache = Caffeine.newBuilder()
                .maximumSize(Integer.getInteger("org.lsst.fits.imageio.rawDataCacheSize", 1_000))
                .recordStats()
                .buildAsync((Segment segment) -> {
                    return Timed.execute(() -> {
                        BufferedFile bf = openFileCache.get(segment.getFile());
                        return readRawData(segment, bf);
                    }, "Reading raw daata for %s took %dms", segment);
                });

        bufferedImageCache = Caffeine.newBuilder()
                .maximumSize(Integer.getInteger("org.lsst.fits.imageio.bufferedImageCacheSize", 10_000))
                .recordStats()
                .buildAsync((MultiKey<RawData, BiasCorrection> key) -> {
                    return Timed.execute(() -> {
                        return createBufferedImage(key.getKey1(), key.getKey2());
                    }, "Loading buffered image for segment %s took %dms", key.getKey1());
                });

        linesCache = Caffeine.newBuilder()
                .maximumSize(Integer.getInteger("org.lsst.fits.imageio.linesCacheSize", 10_000))
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
        // Report stats every minute
        Timer timer = new Timer();
        timer.schedule(new TimerTask(){
            @Override
            public void run() {
                report();
            }
        }, 60_000, 60_000);
    }

    void report() {
        LoadingCache<MultiKey<File, Character>, List<Segment>> s1 = segmentCache.synchronous();
        LOG.log(Level.INFO, "segment Cache size {0} stats {1}", new Object[]{s1.estimatedSize(), s1.stats()});
        LoadingCache<Segment, RawData> s2 = rawDataCache.synchronous();
        LOG.log(Level.INFO, "rawData Cache size {0} stats {1}", new Object[]{s2.estimatedSize(), s2.stats()});
        LoadingCache<MultiKey<RawData, BiasCorrection>, BufferedImage> s3 = bufferedImageCache.synchronous();
        LOG.log(Level.INFO, "bufferedImage Cache size {0} stats {1}", new Object[]{s3.estimatedSize(), s3.stats()});
    }
    
    int preReadImage(ImageInputStream fileInput) {
        List<String> lines = linesCache.get(fileInput);
        return lines == null ? 0 : lines.size();
    }
    
    @SuppressWarnings("null")
    void readImage(ImageInputStream fileInput, Rectangle sourceRegion, Graphics2D g, RGBColorMap cmap, BiasCorrection bc, boolean showBiasRegion, char wcsLetter) throws IOException {

        try {
            Queue<CompletableFuture> l1 = new ConcurrentLinkedQueue<>();
            Queue<CompletableFuture> l2 = new ConcurrentLinkedQueue<>();
            Queue<CompletableFuture> l3 = new ConcurrentLinkedQueue<>();
            List<String> lines = linesCache.get(fileInput);
            lines.stream().map((line) -> segmentCache.get(new MultiKey(new File(line), wcsLetter))).forEach(new Consumer<CompletableFuture>() {
                @Override
                public void accept(CompletableFuture futureSegments) {
                    l1.add(futureSegments.thenAccept(new Consumer<List<Segment>>() {
                        @Override
                        public void accept(List<Segment> segments) {
                            List<Segment> segmentsToRead = computeSegmentsToRead(segments, sourceRegion);
                            segmentsToRead.forEach(new Consumer<Segment>() {
                                @Override
                                public void accept(Segment segment) {
                                    CompletableFuture<RawData> futureRawData = rawDataCache.get(segment);
                                    l2.add(futureRawData.thenAccept(new Consumer<RawData>() {
                                        @Override
                                        public void accept(RawData rawData) {
                                            CompletableFuture<BufferedImage> fbi = bufferedImageCache.get(new MultiKey(rawData, bc));
                                            l3.add(fbi.thenAccept(new Consumer<BufferedImage>() {
                                                @Override
                                                public void accept(BufferedImage bi) {
                                                    Timed.execute(new Callable<Object>() {
                                                        @Override
                                                        public Object call() throws Exception {
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
                                                        }
                                                    }, "drawImage for segment %s took %dms", segment);
                                                }
                                            }));
                                        }
                                    }));
                                }
                            });
                        }
                    }));
                }
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

    private static List<Segment> readSegments(File file, BufferedFile bf, char wcsLetter) throws IOException, TruncatedFileException, FitsException {
        List<Segment> result = new ArrayList<>();
        String ccdSlot = null;
        int nSegments = 16;
        for (int i = 0; i < nSegments+1; i++) {
            Header header = new Header(bf);
            if (i == 0) {
                ccdSlot = header.getStringValue("CCDSLOT");
                if (ccdSlot.startsWith("SW")) nSegments = 8;
            }
            if (i > 0) {
                Segment segment = new Segment(header, file, bf, ccdSlot, wcsLetter);
                result.add(segment);
            }
        }
        return result;
    }

    private static RawData readRawData(Segment segment, BufferedFile bf) throws IOException, FitsException {
        IntBuffer ib = segment.readData();
        return new RawData(segment, ib);
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

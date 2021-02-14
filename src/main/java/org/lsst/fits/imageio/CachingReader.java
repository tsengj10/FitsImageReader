package org.lsst.fits.imageio;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
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
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
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

    private final AsyncLoadingCache<MultiKey3<File, Character, Map<String, Map<String, Object>>>, List<Segment>> segmentCache;
    private final LoadingCache<File, BufferedFile> openFileCache;
    private final AsyncLoadingCache<Segment, RawData> rawDataCache;
    private final AsyncLoadingCache<MultiKey3<Segment, BiasCorrection, long[]>, BufferedImage> bufferedImageCache;
    private final LoadingCache<ImageInputStream, List<String>> linesCache;

    private static final Logger LOG = Logger.getLogger(CachingReader.class.getName());

    public CachingReader() {
        openFileCache = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .removalListener((File file, BufferedFile bf, RemovalCause rc) -> {
                    try {
                        bf.close();
                    } catch (IOException ex) {
                        LOG.log(Level.WARNING, "Error closing file "+file, ex);
                    }
                })
                .build((File file) -> new BufferedFile(file, "r"));

        segmentCache = Caffeine.newBuilder()
                .maximumSize(Integer.getInteger("org.lsst.fits.imageio.segmentCacheSize", 10_000))
                .recordStats()
                .buildAsync((MultiKey3<File, Character, Map<String, Map<String, Object>>> key) -> {
                    return Timed.execute(() -> {
                        BufferedFile bf = openFileCache.get(key.getKey1());
                        return readSegments(key.getKey1(), bf, key.getKey2(), key.getKey3());
                    }, "Loading %s took %dms", key.getKey1());
                });

        rawDataCache = Caffeine.newBuilder()
                .maximumSize(Integer.getInteger("org.lsst.fits.imageio.rawDataCacheSize", 1_000))
                .recordStats()
                .buildAsync((Segment segment, Executor executor) -> segment.readRawDataAsync(executor));

        bufferedImageCache = Caffeine.newBuilder()
                .maximumSize(Integer.getInteger("org.lsst.fits.imageio.bufferedImageCacheSize", 10_000))
                .recordStats()
                .buildAsync((MultiKey3<Segment, BiasCorrection, long[]> key, Executor executor) -> {
                    return rawDataCache.get(key.getKey1()).thenApply(rawData -> { 
                        return Timed.execute(() -> {
                            return createBufferedImage(rawData, key.getKey2(), key.getKey3());
                        }, "Loading buffered image for segment %s took %dms", key.getKey1());
                    });
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
                            } else if (line.startsWith("#")) {
                                //continue;
                            } else {
                                lines.add(line);
                            }
                        }
                        return lines;
                    }, "Read lines in %dms");
                });
        // Report stats every minute
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                report();
            }
        }, 60_000, 60_000);
    }

    void report() {
        LoadingCache<MultiKey3<File, Character, Map<String, Map<String, Object>>>, List<Segment>> s1 = segmentCache.synchronous();
        LOG.log(Level.INFO, "segment Cache size {0} stats {1}", new Object[]{s1.estimatedSize(), s1.stats()});
        LoadingCache<Segment, RawData> s2 = rawDataCache.synchronous();
        LOG.log(Level.INFO, "rawData Cache size {0} stats {1}", new Object[]{s2.estimatedSize(), s2.stats()});
        LoadingCache<MultiKey3<Segment, BiasCorrection, long[]>, BufferedImage> s3 = bufferedImageCache.synchronous();
        LOG.log(Level.INFO, "bufferedImage Cache size {0} stats {1}", new Object[]{s3.estimatedSize(), s3.stats()});
    }

    int preReadImage(ImageInputStream fileInput) {
        List<String> lines = linesCache.get(fileInput);
        return lines == null ? 0 : lines.size();
    }

    void readImage(ImageInputStream fileInput, Rectangle sourceRegion, Graphics2D g, RGBColorMap cmap, BiasCorrection bc, boolean showBiasRegion, char wcsLetter, long[] globalScale, Map<String, Map<String, Object>> wcsOverride) throws IOException {

        try {
            Queue<CompletableFuture<Void>> segmentsCompletables = new ConcurrentLinkedQueue<>();
            Queue<CompletableFuture<Void>> bufferedImageCompletables = new ConcurrentLinkedQueue<>();
            List<String> lines = linesCache.get(fileInput);
            lines.stream().map((line) -> segmentCache.get(new MultiKey3<>(new File(line), wcsLetter, wcsOverride))).forEach((CompletableFuture<List<Segment>> futureSegments) -> {
                segmentsCompletables.add(futureSegments.thenAccept((List<Segment> segments) -> {
                    List<Segment> segmentsToRead = computeSegmentsToRead(segments, sourceRegion);
                    segmentsToRead.stream().forEach((Segment segment) -> {
                        CompletableFuture<BufferedImage> fbi = bufferedImageCache.get(new MultiKey3<>(segment, bc, globalScale));
                        bufferedImageCompletables.add(fbi.thenAccept((BufferedImage bi) -> {
                            Timed.execute(() -> {
                                // g2=g is the graphics we are writing into
                                Graphics2D g2 = (Graphics2D) g.create();
                                g2.transform(segment.getWCSTranslation(showBiasRegion));
                                BufferedImage subimage;
                                if (showBiasRegion) {
                                    subimage = bi;
                                } else {
                                    Rectangle datasec = segment.getDataSec();
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
                    });
                }));
            });
            LOG.log(Level.INFO, "Waiting for {0} files", segmentsCompletables.size());
            CompletableFuture.allOf(segmentsCompletables.toArray(new CompletableFuture[segmentsCompletables.size()])).join();
            LOG.log(Level.INFO, "Waiting for {0} buffered images", bufferedImageCompletables.size());
            CompletableFuture.allOf(bufferedImageCompletables.toArray(new CompletableFuture[bufferedImageCompletables.size()])).join();
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

    private static List<Segment> readSegments(File file, BufferedFile bf, char wcsLetter, Map<String, Map<String, Object>> wcsOverride) throws IOException, TruncatedFileException, FitsException {
        List<Segment> result = new ArrayList<>();
        String ccdSlot = null;
        String raftSlot = null;
        int nSegments = 16;
        synchronized (bf) {
            bf.seek(0);
            for (int i = 0; i < nSegments + 1; i++) {
                Header header = new Header(bf);
                if (i == 0) {
                    raftSlot = header.getStringValue("RAFTBAY");
                    ccdSlot = header.getStringValue("CCDSLOT");
                    if (ccdSlot == null) {
                        ccdSlot = header.getStringValue("SENSNAME");
                    }
                    if (ccdSlot == null) {
                        throw new IOException("Missing CCDSLOT while reading " + file);
                    }
                    if (ccdSlot.startsWith("SW")) {
                        nSegments = 8;
                    }
                }
                if (i > 0) {
                    String extName = header.getStringValue("EXTNAME");
                    String wcsKey = String.format("%s/%s/%s", raftSlot, ccdSlot, extName.substring(7, 9));
                    Segment segment = new Segment(header, file, bf, ccdSlot, wcsLetter, wcsOverride == null ? null : wcsOverride.get(wcsKey));
                    result.add(segment);
                }
            }
        }
        return result;
    }

    private static BufferedImage createBufferedImage(RawData rawData, BiasCorrection bc, long[] globalScale) {
        IntBuffer intBuffer = rawData.asIntBuffer();
        Segment segment = rawData.getSegment();
        Rectangle datasec = segment.getDataSec();
        // Apply bias correction
        BiasCorrection.CorrectionFactors factors = bc.compute(intBuffer, segment);
        ScalingUtils su;
        if (globalScale != null) {
            su = new ScalingUtils(globalScale);
        } else {
            su = histogram(datasec, intBuffer, segment, factors);
        }
        final int max = su.getMax();
        int[] cdf = su.computeCDF();
        
        int range = cdf[max];
        for (int i=su.getMin(); i<=max; i++) {
            cdf[i] = CameraImageReader.DEFAULT_COLOR_MAP.getRGB(cdf[i] * 255 / range);
        }

        // Scale data 
        BufferedImage image = CameraImageReader.IMAGE_TYPE.createBufferedImage(segment.getNAxis1(), segment.getNAxis2());
        WritableRaster raster = image.getRaster();
        DataBuffer db = raster.getDataBuffer();
//        Used for testing bias region
//        Graphics2D graphics = image.createGraphics();        
//        graphics.setColor(Color.GREEN);
//        graphics.fillRect(0, 0, datasec.x, segment.getNAxis2());
//        graphics.setColor(Color.RED);
//        graphics.fillRect(datasec.x + datasec.width, 0, segment.getNAxis1() - datasec.x - datasec.width, segment.getNAxis2());
//        graphics.setColor(Color.BLUE);
//        graphics.fillRect(datasec.x, datasec.y + datasec.height, datasec.width, segment.getNAxis2());
        copyAndScaleData(datasec, segment, cdf, intBuffer, factors, db);
        return image;
    }

    private static void copyAndScaleData(Rectangle datasec, Segment segment, int[] cdf, IntBuffer intBuffer, BiasCorrection.CorrectionFactors factors, DataBuffer db) {
        for (int y = datasec.y; y < datasec.height + datasec.y; y++) {
            int p = datasec.x + y * segment.getNAxis1();
            for (int x = datasec.x; x < datasec.width + datasec.x; x++) {
                int rgb = cdf[Math.max(intBuffer.get(p) - factors.correctionFactor(x, y), 0)];
                db.setElem(p, rgb);
                p++;
            }
        }
    }

    private static ScalingUtils histogram(Rectangle datasec, IntBuffer intBuffer, Segment segment, BiasCorrection.CorrectionFactors factors) {
        // Note: This is hardwired for Camera (18 bit) data
        int[] count = new int[1 << 18];
        for (int y = datasec.y; y < datasec.height + datasec.y; y++) {
            int p = datasec.x + y * segment.getNAxis1();
            for (int x = datasec.x; x < datasec.width + datasec.x; x++) {
                count[Math.max(intBuffer.get(p) - factors.correctionFactor(x, y), 0)]++;
                p++;
            }
        }
        return new ScalingUtils(count);
    }

    public List<Segment> readSegments(ImageInputStream in) throws InterruptedException, ExecutionException {
        List<Segment> result = new ArrayList<>();
        List<String> lines = linesCache.get(in);
        for (String line : lines) {
            result.addAll((List<Segment>) (segmentCache.get(new MultiKey3<>(new File(line), 'Q', null)).get()));
        }
        return result;
    }

    public RawData getRawData(Segment segment) throws InterruptedException, ExecutionException {
        return rawDataCache.get(segment).get();
    }
}

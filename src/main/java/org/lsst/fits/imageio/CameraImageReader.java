package org.lsst.fits.imageio;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import nom.tam.fits.FitsFactory;
import nom.tam.fits.FitsUtil;
import nom.tam.fits.Header;
import nom.tam.fits.TruncatedFileException;
import nom.tam.fits.header.Standard;
import nom.tam.util.BufferedFile;
import org.lsst.fits.test.ScalingUtils;
import org.lsst.fits.test.Timed;

/**
 *
 * @author tonyj
 */
public class CameraImageReader extends ImageReader {

    private static final Logger LOG = Logger.getLogger(CameraImageReader.class.getName());
    private static final Pattern datasecPattern = Pattern.compile("\\[(\\d+):(\\d+),(\\d+):(\\d+)\\]");

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicReference<CompletableFuture<List<Segment>>> futureSegments = new AtomicReference<>();

    static {
        FitsFactory.setUseHierarch(true);
    }

    public CameraImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
        LOG.info("Camera Image Reader created");
    }

    @Override
    public void reset() {
        LOG.info("Reset called");
        super.reset();
    }

    @Override
    public ImageReadParam getDefaultReadParam() {
        LOG.info("getDefaultReadParam called");
        return super.getDefaultReadParam();
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        LOG.log(Level.INFO, "setInput called: {0}", input);
        super.setInput(input, seekForwardOnly, ignoreMetadata);

    }

    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
        return 1;
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        return 3 * 4096 + 4 * 100;
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        return 3 * 4096 + 4 * 100;
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
        return null;
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        // Start by reading the input file (if not already being read)
        CompletableFuture<List<Segment>> segmentList = futureSegments.getAndSet(new CompletableFuture<>());
        if (segmentList == null) {
            // I am the first one here, I have to schedule the file reads
            ImageInputStream fileInput = (ImageInputStream) getInput();
            List<Future<List<Segment>>> futures = new ArrayList();
            for (;;) {
                String line = fileInput.readLine();
                if (line == null) {
                    break;
                }
                futures.add(executor.submit(new ProcessFileTask(new File(line))));
            }
            segmentList = futureSegments.get();
            List<Segment> fullSegmentList = new ArrayList<>();
            for (Future<List<Segment>> future : futures) {
                try {
                    List<Segment> segments = future.get();
                    fullSegmentList.addAll(segments);
                } catch (ExecutionException x) {
                    segmentList.completeExceptionally(x.getCause());
                } catch (InterruptedException ex) {
                    segmentList.completeExceptionally(ex);
                }
            }
            segmentList.complete(fullSegmentList);
        }
        try {
            List<Segment> segments = segmentList.get();
            if (param != null) {
                LOG.log(Level.INFO, "read called sourceRegion={0}", param.getSourceRegion());
                LOG.log(Level.INFO, "read called destination={0}", param.getDestination());
                LOG.log(Level.INFO, "read called renderSize={0}", param.getSourceRenderSize());
                LOG.log(Level.INFO, "read called xsub={0}", param.getSourceXSubsampling());
                LOG.log(Level.INFO, "read called ysub={0}", param.getSourceYSubsampling());
                LOG.log(Level.INFO, "read called xoffsub={0}", param.getSubsamplingXOffset());
                LOG.log(Level.INFO, "read called yoffsub={0}", param.getSubsamplingYOffset());
            }
            // Find out which segments we need to be able to satisfy the request
            List<Segment> segmentsToRead = computeSegmentsToRead(segments, param);
            LOG.log(Level.INFO, "reading {0} segments", segmentsToRead.size());
            CompletionService<Segment> ecs = new ExecutorCompletionService<>(executor);
            for (Segment segment : segmentsToRead) {
                ecs.submit(new ProcessSegmentTask(segment));
            }
            BufferedImage result;
            Graphics2D g;
            if (param == null || param.getSourceRegion() == null) {
                result = new BufferedImage(getWidth(0), getHeight(0), BufferedImage.TYPE_USHORT_GRAY);
                g = result.createGraphics();
            } else {
                result = new BufferedImage((int) param.getSourceRegion().getWidth(), (int) param.getSourceRegion().getHeight(), BufferedImage.TYPE_USHORT_GRAY);
                g = result.createGraphics();
                g.translate(-param.getSourceRegion().getX(), -param.getSourceRegion().getY());
            }
            for (int i = 0; i < segmentsToRead.size(); i++) {
                Segment segment = ecs.take().get();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.transform(segment.wcsTranslation);
                g2.drawImage(segment.image.getSubimage(segment.datasec.x, segment.datasec.y, segment.datasec.width, segment.datasec.height), 0, 0, null);
                g2.dispose();
            }
            g.dispose();
            return result;
        } catch (InterruptedException ex) {
            throw new InterruptedIOException("Interrupt during IO");
        } catch (ExecutionException ex) {
            Throwable x = ex.getCause();
            if (x instanceof IOException) {
                throw (IOException) x;
            } else {
                throw new IOException("Error while reading FITS files", x);
            }
        }
    }

    private List<Segment> computeSegmentsToRead(List<Segment> segments, ImageReadParam param) {
        if (param == null || param.getSourceRegion() == null) {
            return segments;
        } else {
            List<Segment> segmentsToRead = new ArrayList<>();
            Rectangle r = param.getSourceRegion();
            for (Segment segment : segments) {
                if (segment.wcs.intersects(r)) {
                    segmentsToRead.add(segment);
                }
            }
            return segmentsToRead;
        }
    }

    private static class Segment {

        private final File file;
        private final long seekPosition;
        private Rectangle2D.Double wcs;
        private AffineTransform wcsTranslation;
        private Rectangle datasec;
        private final int nAxis1;
        private final int nAxis2;
        private BufferedImage image;

        Segment(Header header, File file, long seekPointer) throws IOException {
            this.file = file;
            this.seekPosition = seekPointer;
            nAxis1 = header.getIntValue(Standard.NAXIS1);
            nAxis2 = header.getIntValue(Standard.NAXIS2);
            String datasetString = header.getStringValue("DATASEC");
            Matcher matcher = datasecPattern.matcher(datasetString);
            if (!matcher.matches()) {
                throw new IOException("Invalid datasec: " + datasetString);
            }
            int datasec1 = Integer.parseInt(matcher.group(1));
            int datasec2 = Integer.parseInt(matcher.group(2));
            int datasec3 = Integer.parseInt(matcher.group(3));
            int datasec4 = Integer.parseInt(matcher.group(4));

            //TODO: Check +1
            datasec = new Rectangle(datasec1, datasec3,
                    datasec2 - datasec1 + 1,
                    datasec4 - datasec3 + 1);

            // Hard wired to use WCSQ coordinates (raft level coordinates)
            double pc1_1 = header.getDoubleValue("PC1_1Q");
            double pc2_2 = header.getDoubleValue("PC2_2Q");

            double crval1 = header.getDoubleValue("CRVAL1Q");
            double crval2 = header.getDoubleValue("CRVAL2Q");
            wcsTranslation = new AffineTransform();
            wcsTranslation.translate(crval1, crval2);
            wcsTranslation.scale(pc1_1, pc2_2);
            Point2D origin = wcsTranslation.transform(new Point(datasec.x, datasec.y), null);
            Point2D corner = wcsTranslation.transform(new Point(datasec.x + datasec.width, datasec.y + datasec.height), null);
            double x = Math.min(origin.getX(), corner.getX());
            double y = Math.min(origin.getY(), corner.getY());
            double width = Math.abs(origin.getX() - corner.getX());
            double height = Math.abs(origin.getY() - corner.getY());
            wcs = new Rectangle2D.Double(x, y, width, height);
        }
    }

    private class ProcessFileTask implements Callable<List<Segment>> {

        private final File fitsFile;

        ProcessFileTask(File fitsFile) {
            this.fitsFile = fitsFile;
        }

        @Override
        public List<Segment> call() throws IOException, TruncatedFileException {

            if (!fitsFile.canRead()) {
                throw new IOException("Unreadable FITS file: " + fitsFile.getAbsolutePath());
            }
            try (BufferedFile bf = new BufferedFile(fitsFile)) {

                List<Segment> result = new ArrayList<>();
                for (int i = 0; i < 17; i++) {
                    Header header = new Header(bf);
                    // Skip primary header, assumes file contains 16 image extensions
                    if (i > 0) {
                        Segment segment = new Segment(header, fitsFile, bf.getFilePointer());
                        // Skip the data (for now)
                        final int dataSize = segment.nAxis1 * segment.nAxis2 * 4;
                        int pad = FitsUtil.padding(dataSize);
                        bf.skip(dataSize + pad);
                        result.add(segment);
                    }
                }
                return result;
            }
        }
    }

    /**
     * Processes one amplifier's worth of data, including scaling the data
     * (currently hardwired to use Histogram scaling)
     */
    private class ProcessSegmentTask implements Callable<Segment> {

        private final Segment segment;

        ProcessSegmentTask(Segment segment) {
            this.segment = segment;
        }

        @Override
        public Segment call() throws IOException {
            int[] data = new int[segment.nAxis1 * segment.nAxis2];
            Timed.execute(()-> {
                try (BufferedFile file = new BufferedFile(segment.file)) {
                    file.seek(segment.seekPosition);
                    file.read(data);
                }
                return 0;
            }, "Reading data took %dms");
            
            return Timed.execute(()-> {
                // Note: This is hardwired for Camera (18 bit) data
                int[] count = new int[1 << 18];
                for (int d : data) {
                    count[d]++;
                }
                ScalingUtils su = new ScalingUtils(count);
                final int min = su.getMin();
                final int max = su.getMax();
                int[] cdf = su.computeCDF();
                int range = cdf[max];

                // Scale data 
                WritableRaster raster = Raster.createInterleavedRaster(DataBuffer.TYPE_USHORT, segment.nAxis1, segment.nAxis2, 1, new Point(0, 0));
                DataBuffer db = raster.getDataBuffer();

                for (int p = 0; p < data.length; p++) {
                    db.setElem(p, 0xffff & (int) ((cdf[data[p]]) * 65536L / range));
                }

                ComponentColorModel cm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY),
                        false, false, Transparency.OPAQUE,
                        raster.getTransferType());
                segment.image = new BufferedImage(cm, raster, false, null);
                return segment;
            }, "Scaling took %dms");
        }

    }
}

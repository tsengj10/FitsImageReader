package org.lsst.fits.imageio.cmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an SAO colormap file (as read/written by ds9)
 *
 * @author tonyj
 */
public class SAOColorMap extends RGBColorMap {

    private static final Pattern COORD_PATTERN = Pattern.compile("\\(([0-9.]+),([0-9.]+)\\)");
    private int[] rgb;

    private enum ColorScheme {
        PSEUDOCOLOR
    };

    private enum Color {
        RED, GREEN, BLUE
    };

    public SAOColorMap(int size, String colorMap) {
        super(size);
        try (InputStream input = SAOColorMap.class.getResourceAsStream(colorMap)) {
            if (input == null) {
                throw new RuntimeException("Missing soa file: " + colorMap);
            }
            LineProvider lines = new LineProvider(input);
            ColorScheme colorScheme = ColorScheme.valueOf(lines.nextLine());
            switch (colorScheme) {
                case PSEUDOCOLOR:
                    Map<Color, Interpolation> cmap = new HashMap<>();
                    Color currentColor = null;
                    Interpolation currentInterpolation = null;
                    for (;;) {
                        String line = lines.nextLine();
                        if (line == null) {
                            if (currentColor != null) {
                                cmap.put(currentColor, currentInterpolation);
                            }
                            break;
                        }
                        if (line.endsWith(":")) {
                            if (currentColor != null) {
                                cmap.put(currentColor, currentInterpolation);
                            }
                            currentColor = Color.valueOf(line.replace(":", ""));
                            currentInterpolation = new Interpolation();
                        } else {
                            if (currentColor == null || currentInterpolation == null) {
                                throw new RuntimeException("Missing color line");
                            }
                            currentInterpolation.readPoints(line);
                        }
                    }
                    rgb = convertToCMap(size, cmap);
                    break;

                default:
                    throw new RuntimeException("Unsupported color scheme: " + colorScheme);
            }
        } catch (IOException x) {
            throw new RuntimeException("Invalid colormap " + colorMap, x);
        }

    }

    @Override
    public int getRGB(int value) {
        return rgb[value];
    }

    private static int[] convertToCMap(int size, Map<Color, Interpolation> cmap) {
        int[] rgb = new int[size];
        for (int i = 0; i < size; i++) {
            float f = i / (size - 1.0f);
            rgb[i] = (int) Math.round((size - 1) * cmap.get(Color.RED).get(f)) << 16
                    | (int) Math.round((size - 1) * cmap.get(Color.GREEN).get(f)) << 8
                    | (int) Math.round((size - 1) * cmap.get(Color.BLUE).get(f));
        }
        return rgb;
    }

    private static class Interpolation {

        private final List<Float> x = new ArrayList<>();
        private final List<Float> y = new ArrayList<>();

        float get(float value) {
            int binarySearch = Collections.binarySearch(x, value);
            if (binarySearch >= 0) {
                return y.get(binarySearch);
            } else {
                float y1 = y.get(-binarySearch - 2);
                float y2 = y.get(-binarySearch - 1);
                float x1 = x.get(-binarySearch - 2);
                float x2 = x.get(-binarySearch - 1);
                return y1 + (y2 - y1) * (value - x1) / (x2 - x1);
            }
        }

        void readPoints(String line) throws IOException {
            Matcher matcher = COORD_PATTERN.matcher(line);
            while (matcher.find()) {
                if (matcher.groupCount() != 2) {
                    throw new IOException("Invalid pattern: " + COORD_PATTERN);
                }
                float f1 = Float.parseFloat(matcher.group(1));
                float f2 = Float.parseFloat(matcher.group(2));
                add(f1, f2);
            }
        }

        private void add(float x, float y) {
            this.x.add(x);
            this.y.add(y);
        }
    }

    private static class LineProvider implements AutoCloseable {

        private final BufferedReader reader;

        public LineProvider(InputStream input) {
            reader = new BufferedReader(new InputStreamReader(input));
        }

        String nextLine() throws IOException {
            for (;;) {
                String line = reader.readLine();
                if (line == null) {
                    return null;
                }
                String[] tokens = line.split("#", 2);
                String commentRemoved = tokens[0].trim();
                if (commentRemoved.isEmpty()) {
                    continue;
                }
                return commentRemoved;
            }
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    public static void main(String[] args) {
        SAOColorMap image = new SAOColorMap(256, "cubehelix00.sao");
        for (int i = 0; i < image.rgb.length; i++) {
            System.out.printf("%3d: %06x\n", i, image.rgb[i]);
        }
    }
}

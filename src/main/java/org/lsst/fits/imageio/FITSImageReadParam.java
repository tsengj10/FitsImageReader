package org.lsst.fits.imageio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.imageio.ImageReadParam;
import org.lsst.fits.imageio.bias.BiasCorrection;
import org.lsst.fits.imageio.bias.SerialParallelBiasCorrection;
import org.lsst.fits.imageio.cmap.RGBColorMap;
import org.lsst.fits.imageio.cmap.SAOColorMap;

/**
 *
 * @author tonyj
 */
public class FITSImageReadParam extends ImageReadParam {

    private final GetSetAvailable<BiasCorrection> bc
            = new GetSetAvailable<>(CameraImageReader.DEFAULT_BIAS_CORRECTION, "Bias Correction",
                    new LinkedHashMap<String, BiasCorrection>() {
                {
                    put("None", CameraImageReader.DEFAULT_BIAS_CORRECTION);
                    put("Simple Overscan Correction", new SerialParallelBiasCorrection());
                }
            });
    private final GetSetAvailable<RGBColorMap> colorMap
            = new GetSetAvailable<>(CameraImageReader.DEFAULT_COLOR_MAP, "Color Map",
                    new LinkedHashMap<String, RGBColorMap>() {
                {
                    put("Grey", new SAOColorMap(256, "grey.sao"));
                    put("B", new SAOColorMap(256, "b.sao"));
                    put("Cube Helix", new SAOColorMap(256, "cubehelix00.sao"));
                }
            });

    public RGBColorMap getColorMap() {
        return colorMap.getValue();
    }

    public void setColorMap(RGBColorMap colorMap) {
        this.colorMap.setValue(colorMap);
    }

    public Set<String> getAvailableColorMaps() {
        return colorMap.getAvailable();
    }

    public void setColorMap(String name) {
        colorMap.setValue(name);

    }

    public String getColorMapName() {
        return colorMap.getValueName();
    }

    public BiasCorrection getBiasCorrection() {
        return bc.getValue();
    }

    public void setBiasCorrection(BiasCorrection bc) {
        this.bc.setValue(bc);
    }

    public Set<String> getAvailableBiasCorrections() {
        return bc.getAvailable();
    }

    public void setBiasCorrection(String name) {
        bc.setValue(name);
    }

    public String getBiasCorrectionName() {
        return bc.getValueName();
    }

    private static class GetSetAvailable<T> {

        private T value;
        private final Map<String, T> available;
        private final String type;

        public GetSetAvailable(T initial, String type, Map<String, T> available) {
            this.value = initial;
            this.available = available;
            this.type = type;
        }

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }

        public Set<String> getAvailable() {
            return Collections.unmodifiableSet(available.keySet());
        }

        public void setValue(String name) {
            if (!available.containsKey(name)) {
                throw new IllegalArgumentException("Unknown " + type + ": " + name);
            }
            setValue(available.get(name));
        }

        public String getValueName() {
            for (Map.Entry<String, T> entry : available.entrySet()) {
                if (Objects.equals(entry.getValue(), value)) {
                    return entry.getKey();
                }
            }
            return null;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 97 * hash + Objects.hashCode(this.value);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final GetSetAvailable<?> other = (GetSetAvailable<?>) obj;
            return Objects.equals(this.value, other.value);
        }
    }
}

package org.lsst.fits.imageio;

import java.nio.Buffer;
import java.util.Objects;

/**
 * Raw data corresponding to one segment read from a Fits File
 * @author tonyj
 * @param <T>
 */
public class RawData<T extends Buffer> {

    private final Segment segment;
    private final T buffer;

    /**
     * Create raw data from integer pixel data buffer
     * @param segment The corresponding segment
     * @param ib The integer pixel data
     */
    RawData(Segment segment, T buffer) {
        this.segment = segment;
        this.buffer = buffer;
    }

    public T getBuffer() {
        return buffer;
    }

    public Segment getSegment() {
        return segment;
    }

    @Override
    public String toString() {
        return "RawData{" + "segment=" + segment + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.segment);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RawData other = (RawData) obj;
        return Objects.equals(this.segment, other.segment);
    }
    
}

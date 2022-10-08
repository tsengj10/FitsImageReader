package org.lsst.fits.imageio;

import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.util.Objects;

/**
 * Raw data corresponding to one segment read from a Fits File
 * @author tonyj
 */
public class RawData {

    private final Segment segment;
    private final IntBuffer ib;

    /**
     * Create raw data from integer pixel data buffer
     * @param segment The corresponding segment
     * @param ib The integer pixel data
     */
    RawData(Segment segment, IntBuffer ib) {
        this.segment = segment;
        this.ib = ib;
    }

    /**
     * Temporary implementation, simply converts float to int by a cast.
     * @param segment The segment corresponding to this data
     * @param fb Floating point pixel data
     */
    RawData(Segment segment, FloatBuffer fb) {
        this.segment = segment;
        this.ib = IntBuffer.allocate(fb.capacity());
        for (int i=0; i<fb.limit(); i++) {
            ib.put((int) fb.get());
        }
        ib.flip();
    }
    
    public IntBuffer asIntBuffer() {
        return ib;
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

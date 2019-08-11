package org.lsst.fits.imageio;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Objects;

/**
 * Raw data corresponding to one segment read from a Fits File
 * @author tonyj
 */
public class RawData {

    private final Segment segment;
    private final ByteBuffer bb;

    RawData(Segment segment, ByteBuffer bb) {
        this.segment = segment;
        this.bb = bb;
    }

    public IntBuffer asIntBuffer() {
        return bb.asIntBuffer();
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

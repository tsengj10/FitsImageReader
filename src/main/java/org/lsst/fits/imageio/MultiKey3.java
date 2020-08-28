package org.lsst.fits.imageio;

import java.util.Objects;

/**
 * A class which can be used as a hash key combining the hashes of 3 other
 * objects
 *
 * @author tonyj
 * @param <T1> The class of the first object
 * @param <T2> The class of the second object.
 * @param <T3> The class of the third object.
 */
public class MultiKey3<T1, T2, T3> {

    private final T1 key1;
    private final T2 key2;
    private final T3 key3;

    public MultiKey3(T1 key1, T2 key2, T3 key3) {
        this.key1 = key1;
        this.key2 = key2;
        this.key3 = key3;
    }

    public T1 getKey1() {
        return key1;
    }

    public T2 getKey2() {
        return key2;
    }

    public T3 getKey3() {
        return key3;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + Objects.hashCode(this.key1);
        hash = 19 * hash + Objects.hashCode(this.key2);
        hash = 19 * hash + Objects.hashCode(this.key3);
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
        final MultiKey3<?, ?, ?> other = (MultiKey3<?, ?, ?>) obj;
        return Objects.equals(this.key1, other.key1) && 
               Objects.equals(this.key2, other.key2) &&
               Objects.equals(this.key3, other.key3);
    }

}

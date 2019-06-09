package org.lsst.fits.imageio;

import java.util.Objects;

/**
 *
 * @author tonyj
 * @param <T1>
 * @param <T2>
 */
public class MultiKey<T1,T2> {

    private final T1 key1;
    private final T2 key2;

    public MultiKey(T1 key1, T2 key2) {
        this.key1 = key1;
        this.key2 = key2;
    }

    public T1 getKey1() {
        return key1;
    }

    public T2 getKey2() {
        return key2;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + Objects.hashCode(this.key1);
        hash = 19 * hash + Objects.hashCode(this.key2);
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
        final MultiKey<?, ?> other = (MultiKey<?, ?>) obj;
        return Objects.equals(this.key1, other.key1) && Objects.equals(this.key2, other.key2);
    }
    
}

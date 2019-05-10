package org.lsst.fits.imageio;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author tonyj
 */
public class Timed {

    private static final Logger LOG = Logger.getLogger(Timed.class.getName());

    public static <T> T execute(Callable<T> callable, String message, Object... args) {
        return execute(callable, (time) -> String.format(message, append(args,time)));
    }

    private static <T> T execute(Callable<T> callable, MessageSupplier message) {
        long start = System.currentTimeMillis();
        try {
            return callable.call();
        } catch (Exception x) {
            return Timed.sneakyThrow(x);
        } finally {
            long stop = System.currentTimeMillis();
            LOG.log(Level.FINE, () -> message.get(stop - start));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Exception, R> R sneakyThrow(Exception t) throws T {
        throw (T) t;
    }

    private static Object[] append(Object[] args, Object... arg) {
        Object[] result = new Object[args.length+arg.length];
        System.arraycopy(args, 0, result, 0,  args.length);
        System.arraycopy( arg, 0, result, args.length, arg.length);
        return result;
    }

    static interface MessageSupplier {

        String get(long time);
    }

}

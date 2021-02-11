package org.lsst.fits.imageio;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility for logging timing info
 * @author tonyj
 */
public class Timed {

    private static final Logger LOG = Logger.getLogger(Timed.class.getName());
    private static final Level DEFAULT_LOG_LEVEL = Level.FINE;

    public static <T> T execute(Callable<T> callable, String message, Object... args) {
        return execute(DEFAULT_LOG_LEVEL, callable, message, args);
    }    

    public static <T> T execute(Level logLevel, Callable<T> callable, String message, Object... args) {
        return execute(logLevel, callable, (time) -> String.format(message, append(args,time)));
    }

    private static <T> T execute(Callable<T> callable, MessageSupplier message) {
        return execute(DEFAULT_LOG_LEVEL, callable, message);
    }

    private static <T> T execute(Level logLevel, Callable<T> callable, MessageSupplier message) {
        long start = System.currentTimeMillis();
        try {
            return callable.call();
        } catch (Exception x) {
            return Timed.sneakyThrow(x);
        } finally {
            long stop = System.currentTimeMillis();
            LOG.log(logLevel, () -> message.get(stop - start));
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

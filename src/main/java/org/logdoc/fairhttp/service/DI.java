package org.logdoc.fairhttp.service;

import com.typesafe.config.Config;
import org.logdoc.fairhttp.service.api.helpers.Preloaded;
import org.logdoc.fairhttp.service.api.helpers.Singleton;
import org.logdoc.fairhttp.service.http.Http;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;


/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 14:09
 * FairHttpService ☭ sweat and blood
 */
public final class DI {
    private static final Logger logger = LoggerFactory.getLogger(DI.class);

    private static final ConcurrentMap<Integer, Singleton> singletonMap = new ConcurrentHashMap<>(16, .5f, 1);
    private static final ConcurrentMap<Integer, Supplier<?>> builderMap = new ConcurrentHashMap<>(16, .5f, 1);
    private static final Set<Integer> primitiveHashes = ConcurrentHashMap.newKeySet(16);
    private static final Map<Class<?>, Class<?>> substitutes = new HashMap<>();

    private static Config config;

    static void init(final Config config0) {
        config = config0;
        logger.info("Initializing");
    }

    static void preload(final Class<Preloaded> clas) {
        try {
            logger.info("Preloading '" + clas.getName() + "'");

            final Preloaded p = clas.getDeclaredConstructor().newInstance();

            p.configure(config);

            logger.info("Successfully loaded '" + clas.getName() + "'");
        } catch (final Exception e) {
            logger.error("Cant load '" + clas.getName() + "' :: " + e.getMessage(), e);
        }
    }

    public static synchronized void bind(final Class<?> type, final Class<?> implementation) {
        if (type == null || implementation == null)
            throw new NullPointerException();

        substitutes.put(type, implementation);
        logger.info("Bound type '" + type.getName() + "' to implementation '" + implementation.getName() + "'");
    }

    public static synchronized <T> void bindAsSingleton(final Class<T> type, final Class<? extends T> implementation) {
        if (type == null || implementation == null)
            throw new NullPointerException();

        substitutes.put(type, implementation);

        singletonMap.put(implementation.hashCode(), new Singleton() {
            private T t = null;
            @Override
            public T get() {
                if (t == null)
                    synchronized (this) {
                        try {
                            t = initSync(implementation, null);
                        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                            logger.error(e.getMessage(), e);
                            return null;
                        }
                    };

                return t;
            }
        });

        logger.info("Bound type '" + type.getName() + "' to implementation '" + implementation.getName() + "'");
    }

    public static synchronized <T> void bindProvider(final Class<T> type, final Supplier<? extends T> provider) {
        builderMap.put(type.hashCode(), provider);
    }

    public static <T> T gain(final Class<T> clas) {
        return gain(clas, true, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T gain(final Class<T> clas, final boolean sync, final Class<?> loop) {
        if (clas == null)
            return null;

        if (clas.equals(Config.class))
            return (T) config;

        if (sync && loop == null) { // side top-level check
            Class<?> substit;

            if ((substit = substitutes.get(clas)) != null)
                return (T) gain(substit, true, null);
        }

        final int hash = clas.hashCode();
        final boolean single = Singleton.class.isAssignableFrom(clas);

        try {
            T tmp;
            if (single && (tmp = (T) singletonMap.get(hash)) != null)
                return tmp == ((Singleton) tmp).get() ? tmp : ((Singleton) tmp).get();

            Supplier<T> bld;
            if (!single && (bld = (Supplier<T>) builderMap.get(hash)) != null)
                return bld.get();

            if (primitiveHashes.contains(hash))
                tmp = clas.getDeclaredConstructor().newInstance();
            else
                tmp = sync ? initSync(clas, loop) : initNoSync(clas, loop);

            if (tmp == null)
                throw new Exception("No valid constructor found for class '" + clas.getName() + "'. E.g. public empty constructor or with classpath-known arguments.");

            if (single)
                singletonMap.put(hash, (Singleton) tmp);

            return tmp;
        } catch (final RuntimeException any) {
            throw any;
        } catch (final Throwable any) {
            throw new RuntimeException(any);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T initNoSync(final Class<T> clas, final Class<?> loop) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        final Constructor<T>[] ctrs = (Constructor<T>[]) clas.getDeclaredConstructors();

        for (final Constructor<T> c : ctrs)
            if (!c.isSynthetic() && c.getParameterCount() == 0 && Modifier.isPublic(c.getModifiers())) {
                primitiveHashes.add(clas.hashCode());
                return c.newInstance();
            }

        List<Object> argz;

        CYCLE:
        for (final Constructor<T> c : ctrs)
            if (!c.isSynthetic() && Modifier.isPublic(c.getModifiers())) {
                final Class<?>[] args = c.getParameterTypes();
                argz = new ArrayList<>(args.length);

                for (final Class<?> arg : args) {
                    if (arg == null || arg.isPrimitive() || arg.isArray() || Map.class.isAssignableFrom(arg) || Collection.class.isAssignableFrom(arg) || arg.equals(clas) || Http.Request.class.isAssignableFrom(arg))
                        continue CYCLE;

                    if (arg.equals(loop)) {
                        logger.error("Found cross loop dependancy between '" + arg.getName() + "' and '" + loop.getName() + "' classes");
                        return null;
                    }

                    if (arg.equals(Config.class))
                        argz.add(config);
                    else {
                        Object o = null;
                        try {
                            o = gain(arg, false, clas);
                        } catch (final Exception ignore) {
                        }

                        if (o != null)
                            argz.add(o);
                        else
                            continue CYCLE;
                    }
                }


                if (argz.size() != c.getParameterCount()) {
                    logger.warn("Cant build constructors args: " + clas.getName() + " :: " + c);
                    continue;
                }

                final List<Object> finalArgz = argz;
                builderMap.put(clas.hashCode(), () -> {
                    try {
                        return c.newInstance(finalArgz.toArray());
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        logger.error(e.getMessage(), e);
                    }

                    return null;
                });

                return (T) builderMap.get(clas.hashCode()).get();
            }

        return null;
    }

    private static <T> T initSync(final Class<T> clas, final Class<?> loop) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        synchronized (DI.class) {
            return initNoSync(clas, loop);
        }
    }
}

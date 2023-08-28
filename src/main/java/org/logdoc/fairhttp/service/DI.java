package org.logdoc.fairhttp.service;

import com.typesafe.config.Config;
import org.logdoc.fairhttp.service.api.helpers.EagerSingleton;
import org.logdoc.fairhttp.service.api.helpers.Preloaded;
import org.logdoc.fairhttp.service.api.helpers.Route;
import org.logdoc.fairhttp.service.api.helpers.Singleton;
import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.logdoc.helpers.Texts.*;


/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 14:09
 * FairHttpService ☭ sweat and blood
 */
public final class DI {
    private static final Logger logger = LoggerFactory.getLogger("FairServer");
    private static final ConcurrentMap<String, DI> refMap = new ConcurrentHashMap<>(4);
    private static Config config;

    private final Set<Class<? extends EagerSingleton>> eagers;
    private final Map<Class<?>, Class<?>> bindMap;
    private final Map<Integer, Supplier<?>> knownConstructors;
    private final Map<Integer, Object> singleMap;

    private DI() {
        bindMap = new HashMap<>(64);
        knownConstructors = new HashMap<>();
        singleMap = new HashMap<>();
        eagers = new HashSet<>(8);
    }

    public static void endpoints(final Route... routes) {
        if (routes == null || routes.length == 0)
            return;

        gain(Server.class).addEndpoints(Arrays.stream(routes).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    synchronized static void init(final Config config0) {
        config = config0;
        logger.info("Initializing");
        refMap.putIfAbsent("", new DI());
    }

    private static DI ref(final String name) {
        DI di = refMap.get(notNull(name));

        if (di == null && name != null && !name.isEmpty())
            di = refMap.get("");

        return di;
    }

    private static void initRef(final String name) {
        if (isEmpty(name) || refMap.get(name) != null)
            return;

        refMap.put(name, new DI());
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

    public static void initEagers() {
        refMap.values().forEach(DI::initEagers0);
    }

    public static void unbind(final Class<?> type) {
        unbind(null, type);
    }

    public static void unbind(final String named, final Class<?> type) {
        if (type == null)
            return;

        ref(named).unbind0(type);
    }

    private synchronized void unbind0(final Class<?> type) {
        bindMap.remove(type);
        knownConstructors.remove(type.hashCode());
        singleMap.remove(type.hashCode());
    }

    public static <A> void bindProvider(final Class<A> type, final Supplier<? extends A> provider) {
        bindProvider(null, type, provider);
    }

    public static <A> void bindProvider(final String named, final Class<A> type, final Supplier<? extends A> provider) {
        initRef(named);
        ref(named).bindProvider0(type, provider);
    }

    public static <A, B extends A> void bind(final Class<A> type, final Class<B> implementation) {
        bind(null, type, implementation);
    }

    public static <A, B extends A> void bind(final String named, final Class<A> type, final Class<B> implementation) {
        initRef(named);
        ref(named).bind0(type, implementation);
    }

    public static <A> A gain(final Class<A> clas) {
        return gain(null, clas);
    }

    public static <A> A gain(final String named, final Class<A> clas) {
        return ref(named).gainInternal(clas, Collections.emptyList());
    }

    private synchronized void initEagers0() {
        if (eagers.isEmpty())
            return;

        final Collection<Class<?>> init = Collections.emptyList();
        eagers.forEach(c -> {
            try {
                gainInternal(c, init);
            } catch (final Exception e) {
                logger.warn("Cant eager init singleton '" + c.getName() + "' :: " + e.getMessage(), e);
            }
        });

        eagers.clear();
    }

    private synchronized <A> void bindProvider0(final Class<A> type, final Supplier<? extends A> provider) {
        if (type == null)
            throw new NullPointerException("Type is null");

        if (provider == null)
            throw new NullPointerException("Provider is null");

        knownConstructors.put(type.hashCode(), provider);
    }

    @SuppressWarnings("unchecked")
    private synchronized <A, B extends A> void bind0(final Class<A> type, final Class<B> implementation) {
        if (type == null)
            throw new NullPointerException("Type is null");

        if (implementation == null)
            throw new NullPointerException("Implementation is null");

        if (EagerSingleton.class.isAssignableFrom(implementation))
            eagers.add((Class<? extends EagerSingleton>) implementation);

        if (!type.equals(implementation))
            bindMap.put(type, implementation);

        logger.info("Bound type '" + type.getName() + "' to implementation '" + implementation.getName() + "'");
    }

    @SuppressWarnings("unchecked")
    private <A> A gainInternal(final Class<A> clas, final Collection<Class<?>> ancestors) {
        if (clas == null)
            return null;

        if (clas.equals(Config.class))
            return (A) config;

        final Class<?> c = bindMap.get(clas);
        if (c != null) {
            if (ancestors.contains(c))
                return null;

            final List<Class<?>> ancestorz = new ArrayList<>(ancestors);
            ancestorz.add(clas);

            return (A) gainInternal(c, ancestorz);
        }

        final boolean singleton = Singleton.class.isAssignableFrom(clas);
        final int hash = clas.hashCode();

        A value = null;

        if (singleton)
            value = (A) singleMap.get(hash);

        boolean missed = value == null;

        if (missed)
            value = build(clas, hash, ancestors);

        if (value == null)
            return null;

        if (singleton && missed)
            synchronized (singleMap) {
                singleMap.put(hash, value);
            }

        return value;
    }

    @SuppressWarnings("unchecked")
    private <A> A build(final Class<A> clas, final int hash, final Collection<Class<?>> ancestors) {
        Supplier<?> constructor;
        if ((constructor = knownConstructors.get(hash)) != null)
            return (A) constructor.get();

        final Constructor<A>[] ctrs = (Constructor<A>[]) clas.getDeclaredConstructors();

        for (final Constructor<A> c : ctrs)
            if (!c.isSynthetic() && c.getParameterCount() == 0 && Modifier.isPublic(c.getModifiers())) {
                constructor = () -> {
                    try {
                        return c.newInstance();
                    } catch (final Exception e) {
                        logger.error("!!! Cant build object of type '" + clas.getName() + "' :: " + e.getMessage(), e);
                        return null;
                    }
                };

                break;
            }

        if (constructor == null) {
            final List<Class<?>> ancestorz = new ArrayList<>(ancestors);
            ancestorz.add(clas);
            List<Object> argz;

            CYCLE:
            for (final Constructor<A> c : ctrs)
                if (!c.isSynthetic() && Modifier.isPublic(c.getModifiers())) {
                    final Class<?>[] args = c.getParameterTypes();
                    argz = new ArrayList<>(args.length);

                    for (final Class<?> arg : args) {
                        if (arg == null || arg.isPrimitive() || arg.isArray() || ancestors.contains(arg) || Map.class.isAssignableFrom(arg) || Collection.class.isAssignableFrom(arg) || arg.equals(clas) || Request.class.isAssignableFrom(arg))
                            continue CYCLE;

                        if (arg.equals(Config.class))
                            argz.add(config);
                        else {
                            Object o = gainInternal(arg, ancestorz);

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
                    constructor = () -> {
                        try {
                            return c.newInstance(finalArgz.toArray());
                        } catch (final InstantiationException | IllegalAccessException | InvocationTargetException e) {
                            logger.error("!!! Cant build object of type '" + clas.getName() + "' :: " + e.getMessage(), e);
                            return null;
                        }
                    };
                    break;
                }
        }

        if (constructor == null) {
            logger.error("!!! Cant build object of type '" + clas.getName() + "' :: no valid constructor found.");
            return null;
        }

        synchronized (knownConstructors) {
            knownConstructors.put(hash, constructor);
        }

        return (A) constructor.get();
    }
}

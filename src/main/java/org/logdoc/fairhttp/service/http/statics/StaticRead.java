package org.logdoc.fairhttp.service.http.statics;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.logdoc.fairhttp.service.DI;
import org.logdoc.fairhttp.service.http.Response;
import org.logdoc.helpers.Sporadics;
import org.logdoc.helpers.std.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.logdoc.fairhttp.service.tools.ConfigTools.*;
import static org.logdoc.helpers.Texts.isEmpty;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 25.04.2023 12:14
 * fair-http-server ☭ sweat and blood
 */
abstract class StaticRead implements Function<String, Response> {
    protected final static Logger logger = LoggerFactory.getLogger(StaticRead.class);
    private static final DateTimeFormatter format = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private final static String autoIdxPrm = "auto_index", indexesPrm = "index_files", cachePrm = "memory_cache", mimesPrm = "mime_types",
            cacheEnblPrm = "enabled", cacheSizePrm = "max_file_size", cacheLifePrm = "lifetime",
            mimeMimePrm = "mime", mimeExtPrm = "ext";

    protected final boolean autoDirList, gotIndex;
    protected final Set<String> indexFile;
    protected final boolean cache;
    protected final long maxCacheSize, maxCacheLife;

    private final ConcurrentMap<String, String> mimes;
    private final ConcurrentMap<String, Response> cachedMap;
    private final ConcurrentMap<String, ScheduledFuture<?>> futuresMap;

    protected final String namedRef;

    protected StaticRead(final Config staticCfg) {
        namedRef = Sporadics.generateUuid().toString();

        try {
            indexFile = new HashSet<>(3);
            mimes = new ConcurrentHashMap<>(8);

            autoDirList = sureBool(staticCfg, autoIdxPrm);

            logger.info("Static folders auto indexing is " + (autoDirList ? "en" : "dis") + "abled.");

            final List<String> names = sureStrings(staticCfg, indexesPrm);

            if (!isEmpty(names))
                indexFile.addAll(names);

            gotIndex = !indexFile.isEmpty();

            if (gotIndex)
                logger.info("Index files defined names: " + indexFile);
            else
                logger.info("No index files defined.");

            final Config cacheCfg = sureConf(staticCfg, cachePrm);

            long cs = 0, cl = 0;
            if (cacheCfg != null) {
                cache = sureBool(cacheCfg, cacheEnblPrm);

                if (cache) {
                    cs = 512 * 1024;
                    cl = Duration.of(3, ChronoUnit.MINUTES).toMillis();

                    if (sureNN(cacheCfg, cacheLifePrm))
                        cl = cacheCfg.getDuration(cacheLifePrm, TimeUnit.MILLISECONDS);

                    if (sureNN(cacheCfg, cacheSizePrm))
                        cs = cacheCfg.getBytes(cacheSizePrm);
                }
            } else
                cache = false;

            maxCacheSize = cs;
            maxCacheLife = cl;

            if (cache) {
                final ScheduledExecutorService ses = Executors.newScheduledThreadPool(Math.min(6, Math.max(2, Runtime.getRuntime().availableProcessors() / 2)));
                DI.bindProvider(namedRef, ScheduledExecutorService.class, () -> ses);
                cachedMap = new ConcurrentHashMap<>(64);
                futuresMap = new ConcurrentHashMap<>(64);

                logger.info("Static caching is enabled for files smaller or equal to " + maxCacheSize + " bytes for a period of " + Duration.of(maxCacheLife, ChronoUnit.MILLIS).toSeconds() + " seconds.");
            } else {
                cachedMap = null;
                futuresMap = null;

                logger.info("Static caching is disabled");
            }

            if (sureNN(staticCfg, mimesPrm)) {
                final List<? extends Config> mcfgs = staticCfg.getConfigList(mimesPrm);

                for (final Config mc : mcfgs)
                    try {
                        final String mime = new MimeType(mc.getString(mimeMimePrm)).toString();

                        final Set<String> exts = new HashSet<>(sureStrings(mc, mimeExtPrm));

                        if (!isEmpty(mime) && !isEmpty(exts)) {
                            for (final String ext : exts)
                                mimes.put(ext.trim().toLowerCase(), mime);

                            logger.info("Additional MIME type '" + mime + "' associated with extensions: " + exts);
                        }
                    } catch (final Exception er) {
                        logger.error(er.getMessage(), er);
                    }
            }

            if (isEmpty(mimes))
                logger.info("No additional MIME types defined.");

        } catch (final ConfigException e) {
            logger.error(e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    protected byte[] dirList(final String dirName, final Collection<FRes> content) {
        final StringBuilder html = new StringBuilder("<html><head><title>Index of " + dirName + "</title></head><body><h1>Index of " + dirName + "</h1><hr><pre><a href=\"../\">../</a>\n");

        content.stream().sorted()
                .forEach(f -> {
                    final boolean i = !f.isFile;
                    final String n = f.name + (i ? "/" : "");
                    final String d = f.time.format(format); // 17
                    final String l = i ? "-" : Long.toString(f.size);

                    html.append("<a href='").append((dirName + "/" + n).replaceAll("/{2,}", "/")).append("'>").append(n).append("</a>").append(" ".repeat(Math.max(0, (51 - n.length())))).append(d).append(" ".repeat(Math.max(0, 20 - l.length()))).append(l).append("\n");
                });

        html.append("</pre><hr></body></html>");

        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    protected void cacheMe(final String id, final Response response) {
        if (!cache || response == null || response.size() <= 0 || response.size() > maxCacheSize)
            return;

        if (futuresMap.get(id) != null)
            try {
                futuresMap.remove(id).cancel(true);
            } catch (final Exception ignore) {
            }
        cachedMap.put(id, response);
        futuresMap.put(id, DI.gain(namedRef, ScheduledExecutorService.class).schedule(() -> cachedMap.remove(id), maxCacheLife, TimeUnit.MILLISECONDS));
    }

    protected String getMime(final String ext) {
        if (isEmpty(ext))
            return null;

        return mimes.get(ext.trim().toLowerCase());
    }

    protected String refreshMime(final String id) {
        return mimes.get(id);
    }

    protected void rememberMime(final String id, final String mime) {
        mimes.put(id, mime);
    }

    protected Response pickCached(final String id) {
        return cache ? cachedMap.get(id) : null;
    }

    protected static class FRes implements Comparable<FRes> {
        boolean exists;
        String name;
        long size;
        boolean isFile;
        LocalDateTime time;

        @Override
        public int compareTo(final FRes o) {
            final int res = Boolean.compare(isFile, o.isFile);

            return res == 0 ? name.compareTo(o.name) : res;
        }
    }
}


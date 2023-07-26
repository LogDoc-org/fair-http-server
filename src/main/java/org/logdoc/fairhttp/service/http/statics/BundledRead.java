package org.logdoc.fairhttp.service.http.statics;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.logdoc.fairhttp.service.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.logdoc.helpers.Texts.isEmpty;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 25.04.2023 11:44
 * fair-http-server ☭ sweat and blood
 */
public class BundledRead extends StaticRead {
    public static final String PlaceHolder = ":classpath:/";
    private static final Logger logger = LoggerFactory.getLogger(DirectRead.class);
    private final ClassLoader cl;
    private final String prefix;

    public BundledRead(final Config staticsCfg, String prefix) {
        super(staticsCfg);

        cl = BundledRead.class.getClassLoader();

        try {
            prefix = prefix.replace(PlaceHolder, "").trim();
            if (isEmpty(prefix))
                prefix = "/";
            else if (!prefix.endsWith("/"))
                prefix += "/";
            this.prefix = prefix;
        } catch (final ConfigException e) {
            logger.error(e.getMessage(), e);
            throw new IllegalStateException(e);
        }

        if (!this.prefix.equals("/") && cl.getResource(this.prefix) == null)
            throw new IllegalStateException("Unknown static root resource: " + this.prefix);

        logger.info("Static bundled content root: " + this.prefix);
        if (autoDirList)
            logger.warn("WARNING: Auto directory listing is disabled in bundled content.");
    }

    private FRes resolve(final String path) {
        final String p = (prefix + path).replaceAll("/{2,}", "/");

        final URL fileRes = cl.getResource(p);

        final FRes f = new FRes();
        f.name = p;
        f.exists = fileRes != null;
        f.time = LocalDateTime.now();
        try {
            f.size = fileRes != null ? fileRes.openConnection().getContentLength() : 0;
        } catch (final Exception ignore) {
        }

        return f;
    }

    @Override
    public Response apply(final String webpath) {
        final FRes p = resolve(webpath);

        if (!p.exists)
            return map404(webpath);

        Response response = pickCached(p.name);

        try {
            if (response != null)
                return response;

            if (webpath.endsWith("/")) {
                if (gotIndex)
                    for (final String idx : indexFile)
                        if (resolve(webpath + '/' + idx).exists)
                            return apply(webpath + '/' + idx);

                response = Response.Forbidden();
            } else {
                final int dot = p.name.lastIndexOf('.');
                String mime = null;

                if (dot > 0)
                    mime = getMime(p.name.substring(dot));

                if (mime == null) {
                    mime = refreshMime(webpath);

                    if (mime == null) {
                        final int[] head = new int[16];


                        try (final InputStream is = cl.getResourceAsStream(p.name)) {
                            if (is != null)
                                for (int i = 0, b = 0; i < head.length && b != -1; i++)
                                    head[i] = (b = is.read());
                        }

                        mime = MimeType.guessMime(head).toString();

                        rememberMime(webpath, mime);
                    }
                }

                response = Response.Ok();
                response.header(Headers.ContentType, mime);
                response.header(Headers.ContentLength, p.size);
                response.setPromise(os -> {
                    final byte[] buf = new byte[1024 * 640];
                    int read;

                    try (final InputStream is = cl.getResourceAsStream(p.name)) {
                        if (is != null)
                            while ((read = is.read(buf)) != -1)
                                os.write(buf, 0, read);

                        os.flush();
                    } catch (final Exception e) {
                        logger.error(p + " :: " + e.getMessage(), e);
                    }
                });
            }

            return response;
        } catch (final IOException e) {
            logger.error(e.getMessage(), e);

            return Response.ServerError();
        } finally {
            final Response finalResponse = response;
            CompletableFuture.runAsync(() -> cacheMe(webpath, finalResponse));
        }
    }
}

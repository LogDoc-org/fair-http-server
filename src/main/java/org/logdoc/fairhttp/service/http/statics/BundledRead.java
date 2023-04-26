package org.logdoc.fairhttp.service.http.statics;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.logdoc.fairhttp.service.http.Http;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;

import static org.logdoc.fairhttp.service.tools.Strings.isEmpty;

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

        logger.info("Static content root: " + this.prefix);
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
    public Http.Response apply(final String s0) {
        final FRes p = resolve(s0);

        if (!p.exists)
            return Http.Response.NotFound();

        Http.Response response = pickCached(p.name);

        try {
            if (response != null)
                return response;

            if (s0.endsWith("/")) {
                if (gotIndex)
                    for (final String idx : indexFile)
                        if (resolve(s0 + '/' + idx).exists)
                            return apply(s0 + '/' + idx);

                response = Http.Response.Forbidden();
            } else {
                final int dot = p.name.lastIndexOf('.');
                String mime = null;

                if (dot > 0)
                    mime = getMime(p.name.substring(dot));

                if (mime == null) {
                    mime = refreshMime(s0);

                    if (mime == null) {
                        final int[] head = new int[16];


                        try (final InputStream is = cl.getResourceAsStream(p.name)) {
                            if (is != null)
                                for (int i = 0, b = 0; i < head.length && b != -1; i++)
                                    head[i] = (b = is.read());
                        }

                        mime = MimeType.guessMime(head).toString();

                        rememberMime(s0, mime);
                    }
                }

                response = Http.Response.Ok();
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

            return Http.Response.ServerError();
        } finally {
            cacheMe(s0, response);
        }
    }
}

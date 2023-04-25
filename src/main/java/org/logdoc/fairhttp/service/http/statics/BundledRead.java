package org.logdoc.fairhttp.service.http.statics;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.logdoc.fairhttp.service.http.Http;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.logdoc.fairhttp.service.tools.Strings.notNull;

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

    public BundledRead(final Config staticsCfg, final String prefix) {
        super(staticsCfg);

        cl = BundledRead.class.getClassLoader();

        try {
            this.prefix = notNull(prefix.replace(PlaceHolder, ""), "/");
        } catch (final ConfigException e) {
            logger.error(e.getMessage(), e);
            throw new IllegalStateException(e);
        }

        if (!this.prefix.equals("/") && cl.getResource(prefix + "/") == null)
            throw new IllegalStateException("Unknown static root resource: " + prefix);
    }

    private FRes resolve(final String path) {
        final String p = (prefix + '/' + path).replaceAll("/{2,}", "/");
        final String dp = p + (p.endsWith("/") ? "" : "/");

        final URL res = cl.getResource(p);

        final FRes f = new FRes();
        f.isFile = cl.getResource(dp) == null;
        f.name = f.isFile ? p : dp;
        f.exists = res != null;
        f.time = LocalDateTime.now();
        try {
            f.size = f.isFile && f.exists && res != null ? res.openConnection().getContentLength() : 0;
        } catch (final Exception ignore) {
        }

        return f;
    }

    @Override
    public Http.Response apply(final String s) {
        final FRes p = resolve(s);

        if (!p.exists)
            return Http.Response.NotFound();

        Http.Response response = pickCached(s);

        try {
            if (response != null)
                return response;

            if (!p.isFile) {
                if (gotIndex) {
                    FRes check;

                    for (final String idx : indexFile)
                        if ((check = resolve(s + '/' + idx)).exists && check.isFile)
                            return apply(s + '/' + idx);
                }

                if (autoDirList) {
                    response = Http.Response.Ok();

                    final List<FRes> list = new ArrayList<>(16);

                    try (final InputStream is = cl.getResourceAsStream(p.name); final BufferedReader br = is == null ? null : new BufferedReader(new InputStreamReader(is))) {
                        if (br != null) {
                            String resource;

                            while ((resource = br.readLine()) != null) {
                                list.add(resolve(s + '/' + resource));
                            }
                        }
                    }

                    response.setPayload(dirList(p.name, list), MimeType.TEXTHTML);
                } else
                    response = Http.Response.Forbidden();
            } else {
                final int dot = s.lastIndexOf('.');
                String mime = null;

                if (dot > 0)
                    mime = getMime(s.substring(dot));

                if (mime == null) {
                    mime = refreshMime(s);

                    if (mime == null) {
                        final int[] head = new int[16];


                        try (final InputStream is = cl.getResourceAsStream(p.name)) {
                            if (is != null)
                                for (int i = 0, b = 0; i < head.length && b != -1; i++)
                                    head[i] = (b = is.read());
                        }

                        mime = MimeType.guessMime(head).toString();

                        rememberMime(s, mime);
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
            cacheMe(s, response);
        }
    }
}

package org.logdoc.fairhttp.service.http.statics;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.logdoc.fairhttp.service.http.Http;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 04.03.2023 13:30
 * fair-http-server ☭ sweat and blood
 */
public class DirectRead extends StaticRead {
    private static final Logger logger = LoggerFactory.getLogger(DirectRead.class);

    private final Path root;

    public DirectRead(final Config staticsCfg, final String root) {
        super(staticsCfg);

        try {
            this.root = Paths.get(root);
        } catch (final ConfigException e) {
            logger.error(e.getMessage(), e);
            throw new IllegalStateException(e);
        }

        if (!Files.exists(this.root))
            throw new IllegalStateException("root dir doesnt exists");

        logger.info("Static content root dir: " + this.root);
    }

    @Override
    public Http.Response apply(final String webpath) {
        final Path p = root.resolve(('/' + webpath).replace('/', File.separatorChar));

        if (!Files.exists(p))
            return map404(webpath);

        Http.Response response = pickCached(webpath);

        try {
            if (response != null)
                return response;

            if (Files.isDirectory(p)) {
                if (gotIndex) {
                    for (final String idx : indexFile)
                        if (Files.exists(p.resolve(idx)) && !Files.isDirectory(p.resolve(idx)))
                            return apply(webpath + '/' + p.getFileName());
                }

                if (autoDirList) {
                    response = Http.Response.Ok();
                    try (final Stream<Path> fs = Files.list(p)) {
                        response.setPayload(dirList(p.getFileName().toString(), fs
                                .map(f -> {
                                    try {
                                        final FRes fr = new FRes();
                                        fr.isFile = !Files.isDirectory(f);
                                        fr.time = LocalDateTime.from(Files.getLastModifiedTime(f).toInstant().atZone(ZoneId.systemDefault()));
                                        fr.name = f.getFileName().toString();
                                        fr.size = fr.isFile ? Files.size(f) : 0;

                                        return fr;
                                    } catch (final Exception e) {
                                        logger.error(e.getMessage(), e);
                                        return null;
                                    }
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList())), MimeType.TEXTHTML);
                    }
                } else
                    response = Http.Response.Forbidden();
            } else {
                final int dot = p.getFileName().toString().lastIndexOf('.');
                String mime = null;

                if (dot > 0)
                    mime = getMime(p.getFileName().toString().substring(dot));

                if (mime == null) {
                    mime = refreshMime(webpath);

                    if (mime == null) {
                        final int[] head = new int[16];

                        try (final InputStream is = Files.newInputStream(p)) {
                            for (int i = 0, b = 0; i < head.length && b != -1; i++)
                                head[i] = (b = is.read());
                        }

                        mime = MimeType.guessMime(head).toString();

                        rememberMime(webpath, mime);
                    }
                }

                long size = Files.size(p);

                response = Http.Response.Ok();
                response.header(Headers.ContentType, mime);
                response.header(Headers.ContentLength, size);
                response.setPromise(os -> {
                    final byte[] buf = new byte[1024 * 640];
                    int read;

                    try (final InputStream is = Files.newInputStream(p)) {
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
            cacheMe(webpath, response);
        }
    }

    public static Http.Response fileResponse(final Path p, final String mimeType, final long size) {
        final Http.Response response = Http.Response.Ok();
        response.header(Headers.ContentType, mimeType);
        response.header(Headers.ContentLength, size);
        response.setPromise(os -> {
            final byte[] buf = new byte[1024 * 640];
            int read;

            try (final InputStream is = Files.newInputStream(p)) {
                while ((read = is.read(buf)) != -1)
                    os.write(buf, 0, read);
                os.flush();
            } catch (final Exception e) {
                logger.error(p + " :: " + e.getMessage(), e);
            }
        });

        return response;
    }
}

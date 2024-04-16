package org.logdoc.fairhttp.service.http.statics;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.http.Response;
import org.logdoc.fairhttp.service.tools.PhasedConsumer;
import org.logdoc.helpers.std.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.logdoc.fairhttp.service.http.RFC.FEED;
import static org.logdoc.helpers.std.MimeTypes.TEXTHTML;

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

    public static Response fileResponse(final Path p, final String mimeType, final long size) {
        final Response response = Response.Ok();
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

    @Override
    public boolean canProcess(String webpath) {
        webpath = webpath.replaceAll("/{2,}", "/");
        if (webpath.startsWith("/"))
            webpath = webpath.substring(1);
        webpath = webpath.replace('/', File.separatorChar);

        return Files.exists(root.resolve(webpath));
    }

    @Override
    public Response apply(String webpath) {
        webpath = webpath.replaceAll("/{2,}", "/");
        if (webpath.startsWith("/"))
            webpath = webpath.substring(1);
        webpath = webpath.replace('/', File.separatorChar);

        final Path p = root.resolve(webpath);

        if (!Files.exists(p))
            return Response.NotFound();

        Response response = pickCached(webpath);

        try {
            if (response != null)
                return response;

            if (Files.isDirectory(p)) {
                if (gotIndex) {
                    Path subid;
                    for (final String idx : indexFile)
                        if (Files.exists((subid = p.resolve(idx))) && !Files.isDirectory(subid))
                            return apply(webpath + '/' + subid.getFileName());
                }

                if (autoDirList) {
                    final String wp = webpath;
                    response = Response.Ok();
                    response.header(Headers.ContentType, TEXTHTML);
                    response.setPromise(new PhasedConsumer<>() {
                        private byte[] data;

                        @Override
                        public void warmUp(final OutputStream os) {
                            try (final Stream<Path> fs = Files.list(p)) {
                                data = dirList(p.getFileName().toString(), fs
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
                                        .collect(Collectors.toList()));

                                os.write((Headers.ContentLength + ": " + data.length).getBytes(StandardCharsets.UTF_8));
                                os.write(FEED);
                            } catch (final Exception e) {
                                logger.error(wp + " :: " + e.getMessage(), e);
                            }
                        }

                        @Override
                        public void accept(final OutputStream os) {
                            try {
                                os.write(data);
                            } catch (IOException e) {
                                logger.error(wp + " :: " + e.getMessage(), e);
                            }
                        }
                    });
                } else
                    response = Response.Forbidden();
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

                response = Response.Ok();
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


            if (response.is200())
                cacheMe(webpath, response);

            return response;
        } catch (final IOException e) {
            logger.error(e.getMessage(), e);

            return Response.ServerError();
        }
    }
}

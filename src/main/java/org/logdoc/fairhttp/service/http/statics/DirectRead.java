package org.logdoc.fairhttp.service.http.statics;

import com.typesafe.config.Config;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.logdoc.fairhttp.service.http.Http;
import org.logdoc.fairhttp.service.tools.ConfigPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.logdoc.fairhttp.service.DI.gain;
import static org.logdoc.fairhttp.service.tools.Strings.isEmpty;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 04.03.2023 13:30
 * fair-http-server ☭ sweat and blood
 */
public class DirectRead implements Function<String, Http.Response> {
    private static final Logger logger = LoggerFactory.getLogger(DirectRead.class);
    private static final DateTimeFormatter format = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final Path root;
    private final boolean autoIndex;
    private final String indexFile;

    public DirectRead() {
        final Config c = gain(Config.class);

        root = Paths.get(c.getString(ConfigPath.STATIC_DIR));
        autoIndex = c.hasPath(ConfigPath.AUTO_INDEX) && c.getBoolean(ConfigPath.AUTO_INDEX);
        indexFile = c.hasPath(ConfigPath.INDEX_FILE) ? c.getString(ConfigPath.INDEX_FILE) : null;

        if (!Files.exists(root))
            throw new IllegalStateException("root dir doesnt exists");
    }

    @Override
    public Http.Response apply(final String s) {
        final Path p = root.resolve("." + URLDecoder.decode(s, StandardCharsets.UTF_8).replace('/', File.separatorChar));

        if (!Files.exists(p))
            return Http.Response.NotFound();

        try {
            if (Files.isDirectory(p)) {
                if (!isEmpty(indexFile) && Files.exists(p.resolve(indexFile)))
                    return apply(s + '/' + indexFile);

                if (autoIndex) {
                    final StringBuilder html = new StringBuilder("<html><head><title>Index of " + s + "</title></head><body><h1>Index of " + s + "</h1><hr><pre><a href=\"../\">../</a>\n");

                    try (final Stream<Path> fs = Files.list(p)) {
                        fs
                                .sorted((o1, o2) -> {
                                    final boolean f1 = Files.isDirectory(o1);
                                    final boolean f2 = Files.isDirectory(o2);

                                    final int res = Boolean.compare(f2, f1);
                                    return res == 0 ? o1.compareTo(o2) : res;
                                })
                                .map(f -> {
                                    try {
                                        final boolean i = Files.isDirectory(f);
                                        final String n = f.getFileName() + (i ? "/" : "");
                                        final String d = LocalDateTime.from(Files.getLastModifiedTime(f).toInstant().atZone(ZoneId.systemDefault())).format(format); // 17
                                        final String l = i ? "-" : Long.toString(Files.size(f));

                                        return "<a href='" + (s + "/" + n).replaceAll("/{2,}", "/") + "'>" + n + "</a>" + " ".repeat(Math.max(0, (51 - n.length()))) +
                                                d +
                                                " ".repeat(Math.max(0, 20 - l.length())) +
                                                l +
                                                "\n";
                                    } catch (final Exception e) {
                                        logger.error(e.getMessage(), e);
                                        return null;
                                    }
                                })
                                .filter(Objects::nonNull)
                                .forEach(html::append);
                    }

                    html.append("</pre><hr></body></html>");

                    final Http.Response r = Http.Response.Ok();
                    r.setPayload(html.toString().getBytes(StandardCharsets.UTF_8), MimeType.TEXTHTML);

                    return r;
                }

                return Http.Response.Forbidden();
            }

            return Http.Response.filePromise(p);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);

            return Http.Response.ServerError();
        }
    }
}

package org.logdoc.fairhttp.service.tools;

import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.helpers.gears.Pair;
import org.logdoc.helpers.std.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.logdoc.helpers.Digits.getInt;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 09.06.2023 10:40
 * fair-http-server ☭ sweat and blood
 */
public class HttpBinStreaming {
    private static final Logger logger = LoggerFactory.getLogger(HttpBinStreaming.class);

    private static final byte CR = 0x0D, LF = 0x0A, DASH = 0x2D;
    private static final byte[] headerSeparator = {CR, LF, CR, LF}, fieldSeparator = {CR, LF}, streamEnd = {DASH, DASH}, boundaryPrefix = {CR, LF, DASH, DASH};

    public static byte[] getBoundary(final MimeType contentType) {
        String boundaryStr = contentType.getParameter("boundary");

        if (boundaryStr == null)
            throw new NullPointerException();

        return boundaryStr.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static List<Pair<Integer, Integer>> markParts(final byte[] body, final byte[] boundary0) {
        final List<Pair<Integer, Integer>> marks = new ArrayList<>(8);
        final byte[] boundary = new byte[boundaryPrefix.length + boundary0.length];
        System.arraycopy(boundaryPrefix, 0, boundary, 0, boundaryPrefix.length);
        System.arraycopy(boundary0, 0, boundary, boundaryPrefix.length, boundary0.length);

        final List<Integer> starts = new ArrayList<>(8);

        for (int i = 0, j = 2, from = -1; i < body.length - boundary.length - streamEnd.length; i++) {
            if (body[i] != boundary[j++]) {
                j = 0;
                from = i;
            } else if (j == boundary.length) {
                starts.add(from + boundary.length + 1);
                j = 0;
            }
        }

        for (int i = 0; i < starts.size(); i++) {
            if (i == starts.size() - 1)
                marks.add(Pair.create(starts.get(i), body.length - streamEnd.length - boundary.length - 1));
            else
                marks.add(Pair.create(starts.get(i), starts.get(i + 1) - boundary.length - 1));
        }

        return marks;
    }

    public static Consumer<Byte> headersTicker(final ByteArrayOutputStream tmp,
                                               final Consumer<Integer> bodyLengthConsumer,
                                               final Consumer<Boolean> chunkedFlagConsumer,
                                               final Consumer<MimeType> contentTypeConsumer,
                                               final BiConsumer<String, String> headerConsumer,
                                               final BiConsumer<String, String> cookieConsumer,
                                               final Consumer<Void> step,
                                               final Consumer<Void> stepOut
    ) {
        return b -> {
            if (b == '\n') {
                final String headerLine = tmp.toString(StandardCharsets.UTF_8).trim();
                tmp.reset();
                final int idx;
                if ((idx = headerLine.indexOf(':')) != -1) {
                    String name = headerLine.substring(0, idx).trim();

                    if (!name.isEmpty()) {
                        final String value = notNull(headerLine.substring(idx + 1));

                        if (name.equalsIgnoreCase(Headers.ContentLength))
                            bodyLengthConsumer.accept(getInt(value));
                        else if (name.equalsIgnoreCase(Headers.TransferEncoding))
                            chunkedFlagConsumer.accept("chunked".equalsIgnoreCase(value));
                        else if (name.equalsIgnoreCase(Headers.ContentType))
                            try {
                                contentTypeConsumer.accept(new MimeType(value));
                            } catch (Exception e) {
                                logger.warn("Cant parse request content-type :: " + e.getMessage(), e);
                            }
                        else if (name.equalsIgnoreCase(Headers.RequestCookies)) {
                            name = Headers.RequestCookies;

                            if (!value.isEmpty())
                                Arrays.stream(value.split(";"))
                                        .filter(s -> s.contains("="))
                                        .forEach(c -> {
                                            final String[] parts = c.split(Pattern.quote("="), 2);
                                            if (parts.length != 2) return;

                                            cookieConsumer.accept(notNull(parts[0]), stringQuotes(parts[1]));
                                        });
                        }

                        headerConsumer.accept(name.toUpperCase(), value);
                    }
                } else if (headerLine.isEmpty()) {
                    tmp.reset();
                    stepOut.accept(null);
                    return;
                }

                step.accept(null);
            } else
                tmp.write(b);
        };
    }

    public static String stringQuotes(String value) {
        if (value == null)
            return null;

        value = notNull(value);

        if (!value.startsWith("\""))
            return value;

        return value.substring(1, value.length() - 1);
    }
}

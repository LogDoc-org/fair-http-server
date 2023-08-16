package org.logdoc.fairhttp.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.logdoc.fairhttp.service.http.Response;
import org.logdoc.fairhttp.service.http.statics.DirectRead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 14:10
 * FairHttpService ☭ sweat and blood
 */
public class Controller {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    public static Response ok() {
        return Response.Ok();
    }

    public static Response ok(final JsonNode json) {
        final Response response = Response.Ok();

        response.setPayload(json.toString().getBytes(StandardCharsets.UTF_8), MimeType.JSON);

        return response;
    }

    public static Response ok(final Path p) {
        try {
            if (!Files.exists(p)) {
                logger.error("Path not found: " + p);
                return Response.NotFound();
            }

            final int[] head = new int[16];

            try (final InputStream is = Files.newInputStream(p)) {
                for (int i = 0, b = 0; i < head.length && b != -1; i++)
                    head[i] = (b = is.read());
            }

            return DirectRead.fileResponse(p, MimeType.guessMime(head).toString(), Files.size(p));
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);

            return Response.ServerError();
        }
    }

    public static Response ok(final String data) {
        final Response response = Response.Ok();

        response.setPayload(data.getBytes(StandardCharsets.UTF_8), MimeType.TEXTHTML);

        return response;
    }

    public static Response okText(final String data) {
        final Response response = Response.Ok();

        response.setPayload(data.getBytes(StandardCharsets.UTF_8), MimeType.TEXTPLAIN);

        return response;
    }

    public static Response ok(final byte[] bytes) {
        final Response response = Response.Ok();

        response.setPayload(bytes, MimeType.BINARY);

        return response;
    }

    public static Response status(final int code, final String message) {
        return new Response(code, message);
    }
}

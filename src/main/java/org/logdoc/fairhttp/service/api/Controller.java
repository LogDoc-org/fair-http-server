package org.logdoc.fairhttp.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.logdoc.fairhttp.service.api.helpers.Singleton;
import org.logdoc.fairhttp.service.http.Response;
import org.logdoc.fairhttp.service.http.statics.DirectRead;
import org.logdoc.helpers.std.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.logdoc.helpers.std.MimeTypes.*;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 14:10
 * FairHttpService ☭ sweat and blood
 */
public abstract class Controller implements Singleton {
    protected static final Logger logger = LoggerFactory.getLogger(Controller.class);

    protected Response created() {
        return Response.Created();
    }

    protected Response noContent() {
        return Response.NoContent();
    }

    protected Response notFound() {
        return Response.NotFound();
    }

    protected Response notFound(final String message) {
        return Response.NotFound(message);
    }

    protected Response forbidden() {
        return Response.Forbidden();
    }

    protected Response serverError() {
        return Response.ServerError();
    }

    protected Response serverError(final String reason) {
        return Response.ServerError(reason);
    }

    protected Response clientError(final String reason) {
        return Response.ClientError(reason);
    }

    protected Response ok() {
        return Response.Ok();
    }

    protected Response ok(final JsonNode json) {
        final Response response = Response.Ok();

        response.setPayload(json.toString().getBytes(StandardCharsets.UTF_8), JSON);

        return response;
    }

    protected Response ok(final Path p) {
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

    protected Response ok(final String html) {
        final Response response = Response.Ok();

        response.setPayload(html.getBytes(StandardCharsets.UTF_8), TEXTHTML);

        return response;
    }

    protected Response okText(final String data) {
        final Response response = Response.Ok();

        response.setPayload(data.getBytes(StandardCharsets.UTF_8), TEXTPLAIN);

        return response;
    }

    protected Response ok(final byte[] bytes) {
        final Response response = Response.Ok();

        if (bytes != null)
            response.setPayload(bytes, BINARY);

        return response;
    }

    protected Response status(final int code, final String message) {
        return new Response(code, message);
    }
}

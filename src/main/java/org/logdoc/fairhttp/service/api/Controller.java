package org.logdoc.fairhttp.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.logdoc.fairhttp.service.http.Http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 14:10
 * FairHttpService ☭ sweat and blood
 */
public class Controller {
    public static Http.Response ok() {
        return Http.Response.Ok();
    }

    public static Http.Response ok(final JsonNode json) {
        final Http.Response response = Http.Response.Ok();

        response.setPayload(json.toString().getBytes(StandardCharsets.UTF_8), MimeType.JSON);

        return response;
    }

    public static Http.Response ok(final Path file) throws IOException {
        return Http.Response.filePromise(file);
    }

    public static Http.Response ok(final String data) {
        final Http.Response response = Http.Response.Ok();

        response.setPayload(data.getBytes(StandardCharsets.UTF_8), MimeType.TEXTPLAIN);

        return response;
    }

    public static Http.Response ok(final byte[] bytes) {
        final Http.Response response = Http.Response.Ok();

        response.setPayload(bytes, MimeType.BINARY);

        return response;
    }

    public static Http.Response status(final int code, final String message) {
        return new Http.Response(code, message);
    }
}

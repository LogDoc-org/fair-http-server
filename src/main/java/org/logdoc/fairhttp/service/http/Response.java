package org.logdoc.fairhttp.service.http;

import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.tools.Json;
import org.logdoc.fairhttp.service.tools.PhasedConsumer;
import org.logdoc.helpers.std.MimeType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.logdoc.fairhttp.service.api.Controller.ok;
import static org.logdoc.helpers.Texts.isEmpty;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.07.2023 17:28
 * fair-http-server ☭ sweat and blood
 */
public class Response {
    public static final byte[] FEED = new byte[]{'\r', '\n'};
    private static final byte[] PROTO = "HTTP/1.1".getBytes(StandardCharsets.US_ASCII);
    protected final Map<String, String> headers;
    private final Set<Cookie> cookies;
    int code;
    private String message;
    private byte[] payload;
    private Consumer<OutputStream> promise;

    {
        headers = new HashMap<>(2);
        headers.put("Server", "FairHttp/1.2.5");
        headers.put("Connection", "keep-alive");

        cookies = new HashSet<>(2);
    }

    private Response() {
    }

    public Response(final int code, final String message) {
        this.code = code;
        this.message = message;
    }

    public static Response jsonSuccess() {
        return ok(Json.newObject().put("success", true));
    }

    public static Response Ok() {
        return new Response(200, "OK");
    }

    public static Response Created() {
        return new Response(201, "Created");
    }

    public static Response NoContent() {
        return new Response(204, "No content");
    }

    public static Response NotFound() {
        return new Response(404, "Not found");
    }

    public static Response NotFound(final String message) {
        return new Response(404, message);
    }

    public static Response Forbidden() {
        return new Response(403, "Access forbidden");
    }

    public static Response ServerError() {
        return new Response(500, "Internal error");
    }

    public static Response ServerError(final String reason) {
        return new Response(500, reason);
    }

    public static Response ClientError(final String reason) {
        return new Response(400, reason);
    }

    public void setPromise(final Consumer<OutputStream> promise) {
        if (promise == null)
            return;

        this.promise = promise;
        this.payload = null;
    }

    public void setPayload(final byte[] payload, final MimeType contentType) {
        if (isEmpty(payload))
            return;

        if (contentType == null)
            throw new NullPointerException("Content-Type");

        this.payload = payload;

        header(Headers.ContentType, contentType.toString());
        header(Headers.ContentLength, String.valueOf(payload.length));
        promise = null;
    }

    public void header(final String name, final Object value) {
        if (isEmpty(name) || isEmpty(value))
            return;

        headers.put(name.trim(), notNull(value));
    }

    byte[] asBytes() throws IOException {
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream(64 * 1024)) {
            os.write(PROTO);
            os.write((" " + code + (isEmpty(message) ? "" : " " + message)).getBytes(StandardCharsets.US_ASCII));
            os.write(FEED);

            if (promise != null && PhasedConsumer.class.isAssignableFrom(promise.getClass()))
                ((PhasedConsumer<OutputStream>) promise).warmUp(os);

            if (isEmpty(payload) && promise == null && !(this instanceof WebSocket))
                header(Headers.ContentLength, 0);

            header("Date", LocalDateTime.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME));

            for (final Map.Entry<String, String> e : headers.entrySet())
                if (!isEmpty(e.getValue()) && !isEmpty(e.getKey())) {
                    os.write((e.getKey() + ": " + e.getValue()).getBytes(StandardCharsets.UTF_8));
                    os.write(FEED);
                }

            for (final Cookie c : cookies) {
                os.write((Headers.ResponseCookies + ": " + c).getBytes(StandardCharsets.UTF_8));
                os.write(FEED);
            }

            os.write(FEED);

            if (!isEmpty(payload) && !(this instanceof WebSocket))
                os.write(payload);
            else if (promise != null)
                promise.accept(os);

            os.flush();

            return os.toByteArray();
        }
    }

    @Override
    public String toString() {
        return code + (isEmpty(message) ? "" : " " + message) + (payload == null ? "" : " :: " + payload.length + " bytes");
    }

    public Response withCookie(final Cookie... cookies) {
        if (!isEmpty(cookies))
            for (final Cookie c : cookies)
                if (c != null)
                    this.cookies.add(c);

        return this;
    }

    public Response withHeader(final String name, final String value) {
        if (!isEmpty(value) && !isEmpty(name))
            header(name.trim(), value.trim());

        return this;
    }

    public int size() {
        return payload == null ? -1 : payload.length;
    }

    public Response as(final MimeType mime) {
        if (mime != null)
            header(Headers.ContentType, mime);

        return this;
    }

    public boolean is200() {
        return code == 200;
    }
}

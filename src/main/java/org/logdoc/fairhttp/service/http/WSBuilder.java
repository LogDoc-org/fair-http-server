package org.logdoc.fairhttp.service.http;

import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.tools.websocket.extension.DefaultExtension;
import org.logdoc.fairhttp.service.tools.websocket.extension.IExtension;
import org.logdoc.fairhttp.service.tools.websocket.protocol.IProtocol;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.logdoc.fairhttp.service.tools.websocket.protocol.IProtocol.RFC_KEY_UUID;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 30.07.2023 16:20
 * fair-http-server ☭ sweat and blood
 */
public class WSBuilder {
    private final Request request;
    private IExtension extension;
    private IProtocol protocol;
    private Consumer<WebSocket.ErrorRef> readErrorHandler, writeErrorHandler;
    private Consumer<String> textHandler;
    private Consumer<byte[]> binaryHandler;
    private Consumer<WebSocket> pingHandler, pongHandler;
    private Consumer<WebSocket.CloseReason> closeHandler;

    private WSBuilder(final Request request) {
        this.request = request;
    }

    public static WSBuilder from(final Request request) {
        return new WSBuilder(request);
    }

    public WSBuilder withCloseHandler(final Consumer<WebSocket.CloseReason> closeHandler) {
        this.closeHandler = closeHandler;

        return this;
    }

    public WSBuilder withExtension(final IExtension extension) {
        this.extension = extension;

        return this;
    }

    public WSBuilder withProtocol(final IProtocol protocol) {
        this.protocol = protocol;

        return this;
    }

    public WSBuilder withPingHandler(final Consumer<WebSocket> pingHandler) {
        this.pingHandler = pingHandler;

        return this;
    }

    public WSBuilder withPongHandler(final Consumer<WebSocket> pongHandler) {
        this.pongHandler = pongHandler;

        return this;
    }

    public WSBuilder withReadErrorHandler(final Consumer<WebSocket.ErrorRef> readErrorHandler) {
        this.readErrorHandler = readErrorHandler;

        return this;
    }

    public WSBuilder withWriteErrorHandler(final Consumer<WebSocket.ErrorRef> writeErrorHandler) {
        this.writeErrorHandler = writeErrorHandler;

        return this;
    }

    public WSBuilder withTextHandler(final Consumer<String> textHandler) {
        this.textHandler = textHandler;

        return this;
    }

    public WSBuilder withBinaryHandler(final Consumer<byte[]> binaryHandler) {
        this.binaryHandler = binaryHandler;

        return this;
    }

    public <T> WSBuilder withTextAutoMapping(final Consumer<T> handler, final Function<String, T> mappingFunction) {
        if (handler != null && mappingFunction != null)
            this.textHandler = s -> handler.accept(mappingFunction.apply(s));

        return this;
    }

    public <T> WSBuilder withBinaryAutoMapping(final Consumer<T> handler, final Function<byte[], T> mappingFunction) {
        if ((handler != null && mappingFunction != null))
            this.binaryHandler = bytes -> handler.accept(mappingFunction.apply(bytes));

        return this;
    }

    public WebSocket build() {
        if (!request.isWebsocketUpgradable(extension, protocol))
            return null;

        final WebSocket socket = getWebSocket();
        try {
            socket.header(Headers.SecWebsocketAccept, Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((request.header(Headers.SecWebsocketKey) + RFC_KEY_UUID).getBytes())));
        } catch (final Exception ignore) {}

        if (extension != null)
            socket.header(Headers.SecWebsocketExtensions, extension.getProvidedExtensionAsServer());

        if (protocol != null) socket.header(Headers.SecWebsocketProtocols, protocol.getProvidedProtocol());

        socket.header(Headers.Upgrade, "websocket");
        socket.header(Headers.Connection, Headers.Upgrade);

        return socket;
    }

    private WebSocket getWebSocket() {
        final Consumer<String> txter = textHandler == null ? s -> {} : textHandler;
        final Consumer<byte[]> biner = binaryHandler == null ? bytes -> {} : binaryHandler;
        final Consumer<WebSocket> pinger = pingHandler == null ? unused -> {} : pingHandler;
        final Consumer<WebSocket> ponger = pongHandler == null ? unused -> {} : pongHandler;
        final Consumer<WebSocket.CloseReason> closer = closeHandler == null ? closeReason -> {} : closeHandler;
        final Consumer<WebSocket.ErrorRef> reader = readErrorHandler == null ? eh -> {} : readErrorHandler;
        final Consumer<WebSocket.ErrorRef> writer = writeErrorHandler == null ? eh -> {} : writeErrorHandler;

        return new WebSocket(extension == null ? new DefaultExtension() : extension,
                txter,
                biner,
                pinger,
                ponger,
                closer,
                reader,
                writer
        );
    }
}

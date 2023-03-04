package org.logdoc.fairhttp.service.tools.websocket.extension;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.12.2022 11:23
 * fairhttp ☭ sweat and blood
 */
public class ExtensionError extends Exception {
    public final int code;

    public ExtensionError(final int code) {
        this.code = code;
    }

    public ExtensionError(final int code, final String message) {
        super(message);
        this.code = code;
    }

    public ExtensionError(final int code, final Throwable cause) {
        super(cause);
        this.code = code;
    }
}

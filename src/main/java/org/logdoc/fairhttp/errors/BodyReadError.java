package org.logdoc.fairhttp.errors;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 23.03.2024 14:42
 * fair-http-server ☭ sweat and blood
 */
public class BodyReadError extends Exception {
    public BodyReadError() {
    }

    public BodyReadError(final String message) {
        super(message);
    }

    public BodyReadError(final String message, final Throwable cause) {
        super(message, cause);
    }

    public BodyReadError(final Throwable cause) {
        super(cause);
    }

    public BodyReadError(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}

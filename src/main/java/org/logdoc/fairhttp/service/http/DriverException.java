package org.logdoc.fairhttp.service.http;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.07.2023 12:01
 * fair-http-server ☭ sweat and blood
 */
public class DriverException extends RuntimeException {
    public DriverException() {
    }

    public DriverException(final String message) {
        super(message);
    }

    public DriverException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public DriverException(final Throwable cause) {
        super(cause);
    }

    public DriverException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}

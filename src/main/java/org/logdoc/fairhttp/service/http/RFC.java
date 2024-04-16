package org.logdoc.fairhttp.service.http;

import java.nio.charset.StandardCharsets;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 16.04.2024 16:59
 * fair-http-server ☭ sweat and blood
 */
public interface RFC {
    byte CR = 0x0D, LF = 0x0A, DASH = 0x2D;

    byte[] FEED = new byte[]{'\r', '\n'},
            PROTO = "HTTP/1.1".getBytes(StandardCharsets.US_ASCII),
            EO_STREAM = {DASH, DASH},
            BODY_SEPARATOR = {CR, LF, CR, LF},
            SEPARATOR = {CR, LF},
            BOUNDARY_PREF = {CR, LF, DASH, DASH};

}

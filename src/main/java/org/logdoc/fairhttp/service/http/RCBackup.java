package org.logdoc.fairhttp.service.http;

import org.logdoc.fairhttp.service.tools.ResourceConnect;

import java.util.Map;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.03.2024 14:44
 * fair-http-server ☭ sweat and blood
 */
interface RCBackup {
    boolean canProcess(RequestId requestId);
    void handleRequest(RequestId id, Map<String, String> headers, ResourceConnect rc);

    void meDead(ResourceConnect rc);
}

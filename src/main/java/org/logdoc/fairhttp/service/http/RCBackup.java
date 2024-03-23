package org.logdoc.fairhttp.service.http;

import org.logdoc.fairhttp.service.tools.ResourceConnect;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.03.2024 14:44
 * fair-http-server ☭ sweat and blood
 */
interface RCBackup {
    void panic(Throwable e, ResourceConnect rc);

    void warmUpRequest(ResourceConnect rc);

    boolean canProcess(RequestId requestId);

    void setWeAreReady(ResourceConnect rc);
}

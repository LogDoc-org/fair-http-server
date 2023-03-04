package org.logdoc.fairhttp.service.api.helpers;

import java.util.Collection;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 04.03.2023 13:15
 * fair-http-server ☭ sweat and blood
 */
public interface FairHttpServer {

    void setupDynamicEndpoints(Collection<DynamicRoute> routes);

    void setupConfigEndpoints(Collection<String> raw);

    boolean removeEndpoint(String method, String signature);

    boolean addEndpoint(DynamicRoute route);
}

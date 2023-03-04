package org.logdoc.fairhttp.service;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.logdoc.fairhttp.service.http.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 14:08
 * FairHttpService ☭ sweat and blood
 */
public class StartFairServer {
    private static final Logger logger = LoggerFactory.getLogger("FairServer Starter");

    public static void main(final String[] args) {
        final Config c = ConfigFactory.defaultApplication().withFallback(ConfigFactory.defaultReference());
        DI.init(c);

        final List<String> endpoints = new ArrayList<>(16);
        try (final InputStream is = StartFairServer.class.getClassLoader().getResourceAsStream("routes"); final ByteArrayOutputStream os = new ByteArrayOutputStream(64 * 1024)) {
            if (is != null) {
                final byte[] buf = new byte[1024 * 640];
                int read;

                while ((read = is.read(buf)) != -1)
                    os.write(buf, 0, read);

                os.flush();
                endpoints.addAll(Arrays.stream(os.toString(StandardCharsets.UTF_8).split("\\n")).collect(Collectors.toList()));
            }
        } catch (final Exception e) {
            logger.debug("Cant load routes config: " + e.getMessage(), e);
        }

        final Server s = new Server(c);
        DI.bindProvider(Server.class, () -> s);
        s.setupConfigEndpoints(endpoints);
        s.start();
    }
}

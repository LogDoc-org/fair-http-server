package org.logdoc.fairhttp.service.http.statics;

import com.typesafe.config.Config;
import org.logdoc.fairhttp.service.http.Response;
import org.logdoc.fairhttp.service.tools.ConfigTools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import static org.logdoc.helpers.Texts.isEmpty;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 22.03.2024 13:33
 * fair-http-server ☭ sweat and blood
 */
public interface AssetsRead extends Function<String, Response> {
    String BUNDLED_MARK = ":classpath:/";

    static AssetsRead ofConfig(final Config config) {
        if (config != null) {
            final Config staticsCfg = ConfigTools.sureConf(config, "fair.http.statics");
            final String dir = staticsCfg != null && staticsCfg.hasPath("root") && !staticsCfg.getIsNull("root") ? notNull(staticsCfg.getString("root")) : null;

            if (!isEmpty(dir)) {
                if (dir.startsWith(BUNDLED_MARK)) {
                    String prefix = dir.replace(BUNDLED_MARK, "").trim();
                    if (isEmpty(prefix))
                        prefix = "/";
                    else if (!prefix.endsWith("/"))
                        prefix += "/";

                    if (prefix.equals("/") || AssetsRead.class.getClassLoader().getResource(prefix) != null)
                        return new BundledRead(staticsCfg, dir);
                }

                final Path p = Paths.get(dir);

                if (Files.exists(p) && Files.isDirectory(p))
                    return new DirectRead(staticsCfg, p);
            }
        }

        return new NoStatics();
    }

    boolean canProcess(String path);
}

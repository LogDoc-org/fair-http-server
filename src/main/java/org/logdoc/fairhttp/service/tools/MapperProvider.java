package org.logdoc.fairhttp.service.tools;

import org.apache.ibatis.session.SqlSessionManager;
import org.logdoc.fairhttp.service.DI;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 17.02.2023 18:34
 * FairHttpService ☭ sweat and blood
 */
public final class MapperProvider<T> implements Supplier<T> {

    private final Class<T> mapperType;
    private final String named;

    public MapperProvider(final String named, final Class<T> mapperType) {
        this.named = named;
        this.mapperType = mapperType;
    }

    @Override
    public T get() {
        return DI.gain(named, SqlSessionManager.class).getMapper(mapperType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.mapperType);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        MapperProvider other = (MapperProvider) obj;
        return Objects.equals(this.mapperType, other.mapperType);
    }
}

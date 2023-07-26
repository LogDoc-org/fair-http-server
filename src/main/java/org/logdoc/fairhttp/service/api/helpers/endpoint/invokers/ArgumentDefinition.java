package org.logdoc.fairhttp.service.api.helpers.endpoint.invokers;

import com.fasterxml.jackson.databind.JsonNode;
import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.tools.Form;
import org.logdoc.fairhttp.service.tools.Json;
import org.logdoc.fairhttp.service.tools.MultiForm;

import java.util.List;
import java.util.Map;

import static org.logdoc.helpers.Digits.*;
import static org.logdoc.helpers.Texts.getBoolean;
import static org.logdoc.helpers.Texts.isEmpty;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 23.05.2023 13:29
 * fair-http-server ☭ sweat and blood
 */
public class ArgumentDefinition {
    private final Container container;
    private final Class<?> argClass;
    private final String paramName;

    public ArgumentDefinition(final Container container, final Class<?> argClass, final String paramName) {
        this.container = container;
        this.argClass = argClass;
        this.paramName = paramName;
    }

    Object resolve(final Map<String, String> query, final Map<String, String> cookies, final Form form, final MultiForm multiForm, final Map<String, String> pathMap, final JsonNode jsonBody, final Request request) {
        if (container == Container.Request)
            return request;

        Object value = null;

        switch (container) {
            case Body:
                value = jsonBody == null || argClass.equals(JsonNode.class) ? jsonBody : Json.fromJson(jsonBody, argClass);
                break;
            case Cookie:
                value = isEmpty(cookies) ? null : cookies.get(paramName);
                break;
            case Form:
                value = isEmpty(form) && isEmpty(multiForm) ? null : isEmpty(multiForm) ? form.get(paramName) : multiForm.get(paramName);
                break;
            case Query:
                value = isEmpty(query) ? null : query.get(paramName);
                break;
            case Path:
                value = pathMap.get(paramName);
                break;
            case Unknown:
                value = !isEmpty(pathMap) ? pathMap.get(paramName) : null;
                if (value == null) {
                    value = !isEmpty(query) ? query.get(paramName) : null;

                    if (value == null) {
                        value = !isEmpty(cookies) ? cookies.get(paramName) : null;

                        if (value == null) {
                            List<String> mf = !isEmpty(form) ? form.get(paramName) : null;
                            value = isEmpty(mf) ? null : mf.get(0);

                            if (value == null && !isEmpty(multiForm))
                                try {
                                    value = multiForm.get(paramName).value;
                                } catch (final Exception ignore) {
                                }
                        }
                    }
                }

                break;
        }

        if (value == null || CharSequence.class.isAssignableFrom(argClass) || argClass.isAssignableFrom(value.getClass()))
            return value;

        if (argClass.equals(short.class) || argClass.equals(Short.class))
            return getShort(value);

        if (argClass.equals(int.class) || argClass.equals(Integer.class))
            return getInt(value);

        if (argClass.equals(long.class) || argClass.equals(Long.class))
            return getLong(value);

        if (argClass.equals(double.class) || argClass.equals(Double.class))
            return getDouble(value);

        if (argClass.equals(float.class) || argClass.equals(Float.class))
            return getFloat(value);

        if (argClass.equals(boolean.class) || argClass.equals(Boolean.class))
            return getBoolean(value);

        if (argClass.equals(char.class) || argClass.equals(Character.class)) {
            if (!CharSequence.class.isAssignableFrom(value.getClass()))
                value = String.valueOf(value);

            if (isEmpty(value))
                return argClass.equals(char.class) ? '\0' : null;

            return ((CharSequence) value).charAt(0);
        }

        if (argClass.equals(byte.class) || argClass.equals(Byte.class)) {
            if (!CharSequence.class.isAssignableFrom(value.getClass()))
                value = String.valueOf(value);

            if (isEmpty(value))
                return argClass.equals(byte.class) ? '\0' : null;

            return Byte.parseByte((String) value);
        }

        return value;
    }

    public enum Container {Form, Body, Query, Cookie, Path, Unknown, Request}
}

package org.logdoc.fairhttp.service.tools;

import java.io.IOException;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 15:36
 * FairHttpService ☭ sweat and blood
 */

public class Json {
    private static final ObjectMapper defaultObjectMapper = newDefaultMapper();
    private static volatile ObjectMapper objectMapper = null;

    private static ObjectMapper newDefaultMapper() {
        return JsonMapper.builder()
                .addModules(
                        new JavaTimeModule(),
                        new ParameterNamesModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
                .build();
    }

    public static ObjectMapper mapper() {
        if (objectMapper == null)
            return defaultObjectMapper;

        return objectMapper;
    }

    private static String generateJson(Object o, boolean prettyPrint, boolean escapeNonASCII) {
        try {
            ObjectWriter writer = mapper().writer();
            if (prettyPrint) {
                writer = writer.with(SerializationFeature.INDENT_OUTPUT);
            }
            if (escapeNonASCII) {
                writer = writer.with(JsonWriteFeature.ESCAPE_NON_ASCII);
            }
            return writer.writeValueAsString(o);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode toJson(final Object data) {
        try {
            return mapper().valueToTree(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <A> A fromJson(JsonNode json, Class<A> clazz) {
        try {
            return mapper().treeToValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectNode newObject() {
        return mapper().createObjectNode();
    }

    public static ArrayNode newArray() {
        return mapper().createArrayNode();
    }

    public static String stringify(JsonNode json) {
        return generateJson(json, false, false);
    }

    public static String asciiStringify(JsonNode json) {
        return generateJson(json, false, true);
    }

    public static String prettyPrint(JsonNode json) {
        return generateJson(json, true, false);
    }

    public static JsonNode parse(String src) {
        try {
            return mapper().readTree(src);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static JsonNode parse(java.io.InputStream src) {
        try {
            return mapper().readTree(src);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static JsonNode parse(byte[] src) {
        try {
            return mapper().readTree(src);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void setObjectMapper(ObjectMapper mapper) {
        objectMapper = mapper;
    }
}

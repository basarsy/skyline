package com.basarsy.skyline.common.config;

import com.basarsy.skyline.common.response.ApiResponse;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.Decoder;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * Feign decoder for Skyline services.
 *
 * <p>Every Skyline controller wraps its payload in {@link ApiResponse}. Feign clients declare the
 * payload type itself (e.g. {@code FlightResponse}), so this decoder unwraps the envelope's
 * {@code data} field before binding. Clients that explicitly ask for {@code ApiResponse<T>} get the
 * envelope untouched, and non-envelope bodies are bound as-is.
 */
public class ApiResponseUnwrappingDecoder implements Decoder {

    private static final String SUCCESS_FIELD = "success";
    private static final String DATA_FIELD = "data";

    private final ObjectMapper objectMapper;

    public ApiResponseUnwrappingDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
        if (response.body() == null || type == void.class || type == Void.class) {
            return null;
        }

        JavaType targetType = objectMapper.getTypeFactory().constructType(type);
        JsonNode root;
        try (InputStream body = response.body().asInputStream()) {
            root = objectMapper.readTree(body);
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }

        JsonNode payload = isEnvelope(root) && !targetType.hasRawClass(ApiResponse.class)
                ? root.get(DATA_FIELD)
                : root;
        if (payload == null || payload.isNull()) {
            return null;
        }
        return objectMapper.readerFor(targetType).readValue(payload);
    }

    private static boolean isEnvelope(JsonNode node) {
        return node.isObject() && node.has(SUCCESS_FIELD) && node.has(DATA_FIELD);
    }
}

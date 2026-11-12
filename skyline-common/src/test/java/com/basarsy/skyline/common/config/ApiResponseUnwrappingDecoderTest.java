package com.basarsy.skyline.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.basarsy.skyline.common.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Request;
import feign.Response;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiResponseUnwrappingDecoderTest {

    record Item(UUID id, String name, LocalDateTime at) {}

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ApiResponseUnwrappingDecoder decoder = new ApiResponseUnwrappingDecoder(objectMapper);

    @Test
    void unwrapsEnvelopeIntoPayloadType() throws Exception {
        UUID id = UUID.randomUUID();
        String body = """
                {"success":true,"message":"ok","data":{"id":"%s","name":"IST","at":"2026-01-01T10:00:00"},
                 "timestamp":"2026-01-01T10:00:00","errors":null}
                """.formatted(id);

        Item item = (Item) decoder.decode(response(body), Item.class);

        assertThat(item.id()).isEqualTo(id);
        assertThat(item.name()).isEqualTo("IST");
        assertThat(item.at()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
    }

    @Test
    void unwrapsEnvelopeIntoGenericList() throws Exception {
        String body = """
                {"success":true,"data":[{"name":"A"},{"name":"B"}]}
                """;
        Type listType = new TypeReference<List<Item>>() {}.getType();

        @SuppressWarnings("unchecked")
        List<Item> items = (List<Item>) decoder.decode(response(body), listType);

        assertThat(items).extracting(Item::name).containsExactly("A", "B");
    }

    @Test
    void keepsEnvelopeWhenClientAsksForApiResponse() throws Exception {
        String body = """
                {"success":true,"message":"ok","data":{"name":"A"}}
                """;
        Type envelopeType = new TypeReference<ApiResponse<Item>>() {}.getType();

        @SuppressWarnings("unchecked")
        ApiResponse<Item> envelope = (ApiResponse<Item>) decoder.decode(response(body), envelopeType);

        assertThat(envelope.success()).isTrue();
        assertThat(envelope.data().name()).isEqualTo("A");
    }

    @Test
    void bindsNonEnvelopeBodyAsIs() throws Exception {
        Item item = (Item) decoder.decode(response("{\"name\":\"plain\"}"), Item.class);

        assertThat(item.name()).isEqualTo("plain");
    }

    @Test
    void returnsNullForEmptyBodyOrNullData() throws Exception {
        assertThat(decoder.decode(response(""), Item.class)).isNull();
        assertThat(decoder.decode(response("{\"success\":true,\"data\":null}"), Item.class)).isNull();
    }

    private static Response response(String body) {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://test", Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        return Response.builder()
                .status(200)
                .request(request)
                .headers(Collections.emptyMap())
                .body(body, StandardCharsets.UTF_8)
                .build();
    }
}

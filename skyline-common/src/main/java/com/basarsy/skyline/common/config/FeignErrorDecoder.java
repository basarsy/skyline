package com.basarsy.skyline.common.config;

import com.basarsy.skyline.common.exception.SkylineException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.InputStream;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        try (InputStream bodyIs = response.body().asInputStream()) {
            JsonNode jsonNode = objectMapper.readTree(bodyIs);
            if (jsonNode.has("message")) {
                String message = jsonNode.get("message").asText();
                return new SkylineException(message, HttpStatus.valueOf(response.status()));
            }
        } catch (Exception e) {
            // Fallback to default if body parsing fails
        }
        return defaultErrorDecoder.decode(methodKey, response);
    }
}

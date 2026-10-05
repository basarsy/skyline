package com.basarsy.skyline.common.config;

import com.basarsy.skyline.common.security.InternalServiceAuthenticationFilter;
import com.basarsy.skyline.common.security.InternalServiceProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class FeignClientInterceptor implements RequestInterceptor {

    private final InternalServiceProperties internalServiceProperties;

    @Override
    public void apply(RequestTemplate requestTemplate) {
        ServletRequestAttributes requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            HttpServletRequest request = requestAttributes.getRequest();
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null) {
                requestTemplate.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        }

        String apiKey = internalServiceProperties.apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            requestTemplate.header(InternalServiceAuthenticationFilter.API_KEY_HEADER, apiKey);
        }
    }
}

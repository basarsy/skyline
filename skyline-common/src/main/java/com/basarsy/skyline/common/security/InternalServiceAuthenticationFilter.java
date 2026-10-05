package com.basarsy.skyline.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Skyline-Internal-Key";
    public static final String SERVICE_ROLE = "ROLE_SERVICE";

    private final InternalServiceProperties internalServiceProperties;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (isValidInternalKey(request.getHeader(API_KEY_HEADER))) {
            grantServiceAuthority();
        }
        filterChain.doFilter(request, response);
    }

    private boolean isValidInternalKey(String provided) {
        String expected = internalServiceProperties.apiKey();
        if (provided == null || expected == null || expected.isBlank()) {
            return false;
        }
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        return providedBytes.length == expectedBytes.length && MessageDigest.isEqual(providedBytes, expectedBytes);
    }

    private void grantServiceAuthority() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        SimpleGrantedAuthority serviceAuthority = new SimpleGrantedAuthority(SERVICE_ROLE);

        if (current == null || !current.isAuthenticated()) {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(
                            "internal-service", null, List.of(serviceAuthority)));
            return;
        }

        boolean alreadyHasService = current.getAuthorities().stream()
                .anyMatch(authority -> SERVICE_ROLE.equals(authority.getAuthority()));
        if (alreadyHasService) {
            return;
        }

        List<GrantedAuthority> authorities = new ArrayList<>(current.getAuthorities());
        authorities.add(serviceAuthority);
        UsernamePasswordAuthenticationToken updated = new UsernamePasswordAuthenticationToken(
                current.getPrincipal(), current.getCredentials(), authorities);
        updated.setDetails(current.getDetails());
        SecurityContextHolder.getContext().setAuthentication(updated);
    }
}

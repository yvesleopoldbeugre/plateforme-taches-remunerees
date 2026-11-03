package com.platform.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.exception.ErrorResponse;
import com.platform.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Keeps 401 responses on the same {@code { "error": {...} } } shape as the rest of the API. */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of("UNAUTHENTICATED", "Authentification requise.", traceId));
    }
}

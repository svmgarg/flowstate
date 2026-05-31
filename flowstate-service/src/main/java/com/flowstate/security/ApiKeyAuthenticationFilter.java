package com.flowstate.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Extracts API Key from X-API-KEY header, authenticates, and sets workspaceId attribute.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyAuthenticationProvider apiKeyAuthenticationProvider;

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final Set<String> PUBLIC_PATHS = Set.of("/memory/health", "/v1/health");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PUBLIC_PATHS.contains(path)
                || path.endsWith(".html")
                || path.endsWith(".css")
                || path.endsWith(".js")
                || path.endsWith(".ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && !apiKey.isEmpty()) {
            if (!apiKey.matches("[A-Z0-9]{8}")) {
                log.warn("Invalid API key format from: {}", request.getRemoteAddr());
                filterChain.doFilter(request, response);
                return;
            }

            ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(apiKey);
            log.debug("API Key auth for: {}", request.getRequestURI());

            var authentication = apiKeyAuthenticationProvider.authenticate(token);

            if (authentication != null && authentication.isAuthenticated()) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                String workspaceId = deriveWorkspaceId(apiKey);
                request.setAttribute("workspaceId", workspaceId);
                
                log.debug("Authenticated. workspaceId={}", workspaceId);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String deriveWorkspaceId(String apiKey) {
        return apiKey;
    }
}

package com.flowstate.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiKeyAuthenticationProvider apiKeyAuthenticationProvider;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(apiKeyAuthenticationProvider);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validKeyAuthenticatesAndSetsWorkspaceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/recall");
        request.addHeader("X-API-KEY", "FLOW1234");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(apiKeyAuthenticationProvider.authenticate(any(ApiKeyAuthenticationToken.class)))
                .thenReturn(new ApiKeyAuthenticationToken("FLOW1234", List.of()));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        assertThat(request.getAttribute("workspaceId")).isEqualTo("FLOW1234");
        verify(apiKeyAuthenticationProvider).authenticate(any(ApiKeyAuthenticationToken.class));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidKeyDoesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/recall");
        request.addHeader("X-API-KEY", "ABCD1234");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(apiKeyAuthenticationProvider.authenticate(any(ApiKeyAuthenticationToken.class))).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute("workspaceId")).isNull();
        verify(apiKeyAuthenticationProvider).authenticate(any(ApiKeyAuthenticationToken.class));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingKeySkipsAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/recall");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute("workspaceId")).isNull();
        verify(apiKeyAuthenticationProvider, never()).authenticate(any(ApiKeyAuthenticationToken.class));
        verify(filterChain).doFilter(request, response);
    }
}

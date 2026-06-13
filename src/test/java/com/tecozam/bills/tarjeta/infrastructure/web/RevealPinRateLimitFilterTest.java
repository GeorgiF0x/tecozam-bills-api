package com.tecozam.bills.tarjeta.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RevealPinRateLimitFilterTest {

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username, "x", AuthorityUtils.createAuthorityList("ROLE_CAMPO")));
    }

    @Test
    @DisplayName("Las primeras 10 peticiones pasan; la 11ª recibe 429 con Retry-After")
    void allows10thenRejects11th() throws Exception {
        authenticateAs("campo");
        RevealPinRateLimitFilter filter = new RevealPinRateLimitFilter();

        FilterChain chain = mock(FilterChain.class);
        for (int i = 1; i <= 10; i++) {
            HttpServletRequest req = buildRequest();
            HttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
        }
        verify(chain, org.mockito.Mockito.times(10)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        // 11ª: debería bloquearse
        HttpServletRequest req11 = buildRequest();
        MockHttpServletResponse res11 = new MockHttpServletResponse();
        FilterChain chain11 = mock(FilterChain.class);
        filter.doFilter(req11, res11, chain11);

        assertThat(res11.getStatus()).isEqualTo(429);
        assertThat(res11.getHeader("Retry-After")).isNotNull();

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Filtro NO se aplica a rutas distintas a /pin/reveal")
    void doesNotApplyToOtherPaths() throws Exception {
        authenticateAs("campo");
        RevealPinRateLimitFilter filter = new RevealPinRateLimitFilter();

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tarjetas/1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);

        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest buildRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/tarjetas/99/pin/reveal");
        req.setServletPath("/api/tarjetas/99/pin/reveal");
        return req;
    }
}

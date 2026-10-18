package com.runab.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestRateLimitFilterTest {
    @Test void untrustedForwardingHeaderCannotBypassLimits() throws Exception {
        var filter = new RequestRateLimitFilter();
        for (int i = 0; i < 10; i++) assertThat(login(filter, "198.51.100.2", "203.0.113." + i)).isEqualTo(204);
        assertThat(login(filter, "198.51.100.2", "203.0.113.99")).isEqualTo(429);
    }

    @Test void trustedProxyKeepsDifferentClientsSeparateAndIgnoresSpoofedLeftmostHop() throws Exception {
        var filter = new RequestRateLimitFilter("127.0.0.1");
        for (int i = 0; i < 10; i++) assertThat(login(filter, "127.0.0.1", "spoof, 203.0.113.1")).isEqualTo(204);
        assertThat(login(filter, "127.0.0.1", "different-spoof, 203.0.113.1")).isEqualTo(429);
        assertThat(login(filter, "127.0.0.1", "203.0.113.2")).isEqualTo(204);
    }

    private int login(RequestRateLimitFilter filter, String remote, String forwarded) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remote);
        request.addHeader("X-Forwarded-For", forwarded);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));
        return response.getStatus();
    }
}

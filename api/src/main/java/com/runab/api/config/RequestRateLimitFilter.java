package com.runab.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Single-process safety net. A shared ingress/Redis limiter is required for multiple replicas. */
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_KEYS = 10_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final Set<String> trustedProxyIps;

    public RequestRateLimitFilter() { this(""); }

    public RequestRateLimitFilter(String proxies) {
        trustedProxyIps = Arrays.stream(proxies.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        if (trustedProxyIps.stream().anyMatch(value -> !isIpLiteral(value))) {
            throw new IllegalArgumentException("TRUSTED_PROXY_IPS must contain explicit IP addresses only");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI();
        int limit = 0;
        String identity = clientAddress(request);
        String group = path;
        if ("POST".equals(request.getMethod()) && path.startsWith("/api/v1/auth/")) {
            limit = path.endsWith("/email/send") ? 5 : path.endsWith("/email/verify") ? 30 : 10;
        } else if ("GET".equals(request.getMethod()) && path.matches("/api/v1/policies/[^/]+/summary")) {
            limit = 5;
            group = "paid-summary";
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) identity = "user:" + auth.getPrincipal();
        }
        if (limit > 0 && !acquire(group + ":" + identity, limit)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"요청이 너무 많습니다. 1분 후 다시 시도해주세요\",\"error\":{\"code\":\"RATE_LIMITED\",\"status\":429}}");
            return;
        }
        chain.doFilter(request, response);
    }

    private String clientAddress(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustedProxyIps.contains(remote)) return remote;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 2048) return remote;
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!isIpLiteral(hop)) return remote;
            if (!trustedProxyIps.contains(hop)) return hop;
        }
        return remote;
    }

    private static boolean isIpLiteral(String value) {
        if (value.contains(":")) {
            if (!value.matches("[0-9a-fA-F:.]{2,45}")) return false;
            try { return java.net.InetAddress.getByName(value) instanceof java.net.Inet6Address; }
            catch (java.net.UnknownHostException e) { return false; }
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (!part.matches("[0-9]{1,3}") || Integer.parseInt(part) > 255) return false;
        }
        return true;
    }

    private synchronized boolean acquire(String key, int maximum) {
        long now = System.currentTimeMillis();
        Window current = windows.get(key);
        if (current == null || now - current.startedAt >= WINDOW_MS) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MS);
            if (windows.size() >= MAX_KEYS) return false;
            windows.put(key, new Window(now));
            return true;
        }
        if (current.count >= maximum) return false;
        current.count++;
        return true;
    }

    private static final class Window {
        final long startedAt;
        int count = 1;
        Window(long startedAt) { this.startedAt = startedAt; }
    }
}

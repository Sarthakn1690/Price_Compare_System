package com.pricecomparison.security;

import com.pricecomparison.exception.CustomExceptions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory daily usage limiter.
 * For a real SaaS, replace with Redis + durable counters.
 */
@Component
public class UsageLimitFilter extends OncePerRequestFilter {

    private static final int ANON_DAILY_LIMIT = 5;
    private static final int FREE_DAILY_LIMIT = 30;
    private static final int PRO_DAILY_LIMIT = 500;

    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only limit scraping/search actions (costly endpoints)
        return !(path.endsWith("/products/search") && "POST".equalsIgnoreCase(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null && auth.isAuthenticated() && auth.getName() != null;
        int limit = authenticated ? (isPro(auth) ? PRO_DAILY_LIMIT : FREE_DAILY_LIMIT) : ANON_DAILY_LIMIT;

        String key = usageKey(request, auth);
        int count = counters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        if (count > limit) {
            throw new CustomExceptions.RateLimitExceededException();
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPro(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_PRO".equalsIgnoreCase(a.getAuthority()));
    }

    private String usageKey(HttpServletRequest request, Authentication auth) {
        String date = LocalDate.now().toString();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return "u:" + auth.getName() + ":" + date;
        }
        String ip = request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
        return "ip:" + ip + ":" + date;
    }
}


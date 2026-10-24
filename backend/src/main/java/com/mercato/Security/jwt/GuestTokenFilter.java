package com.mercato.Security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GuestTokenFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private static final Logger logger = LoggerFactory.getLogger(GuestTokenFilter.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String existingToken = jwtUtils.extractJwt(request);

        if (existingToken != null && jwtUtils.validateJwtToken(existingToken)) {
            String tokenType = jwtUtils.getClaimFromToken(existingToken, "type");

            if ("guest".equals(tokenType)) {
                request.setAttribute(JwtUtils.GUEST_TOKEN_ATTRIBUTE, existingToken);
            }
        } else if (request.getRequestURI().startsWith("/api/cart")) {
            logger.debug("Invalid token found, generating new guest token");
            String newGuestToken = jwtUtils.generateGuestTokenValue();

            response.setHeader("X-Guest-Token", newGuestToken);
            request.setAttribute(JwtUtils.GUEST_TOKEN_ATTRIBUTE, newGuestToken);
        }

        chain.doFilter(request, response);
    }
}

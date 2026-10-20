package com.mercato.Security.jwt;

import com.mercato.Security.services.UserDetailsImpl;
import com.mercato.Security.services.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthTokenFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsServiceImpl;

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        logger.debug("AuthTokenFilter called for URI: {}", request.getRequestURI());

        try {
            String jwt = jwtUtils.extractJwt(request);

            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String tokenType = jwtUtils.getClaimFromToken(jwt, "type");

                if ("guest".equals(tokenType)) {
                    request.setAttribute(JwtUtils.GUEST_TOKEN_ATTRIBUTE, jwt);
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = jwtUtils.getUserNameFromJwtToken(jwt);
                UserDetailsImpl userDetails = userDetailsServiceImpl.loadUserByUsername(username);

                String version = jwtUtils.getClaimFromToken(jwt, "accessVersion");
                if (!userDetails.getUserId().equals(jwtUtils.getClaimFromToken(jwt, "userId"))
                        || !Long.toString(userDetails.getAccessVersion()).equals(version == null ? "0" : version)) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired. Sign in again");
                    return;
                }

                if (!userDetails.isEnabled()) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account is deactivated");
                    return;
                }
                if (!userDetails.isAccountNonLocked()) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account is locked");
                    return;
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails,
                                null,
                                userDetails.getAuthorities());
                logger.debug("Roles from JWT: {}", userDetails.getAuthorities());

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}

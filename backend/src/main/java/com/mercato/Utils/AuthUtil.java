package com.mercato.Utils;

import com.mercato.Entity.EcommUser;
import com.mercato.Entity.fulfillment.TransitionTrigger;
import com.mercato.Security.services.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthUtil {

    private final UserDetailsServiceImpl userDetailsService;

    public EcommUser getLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) return null;
        return userDetailsService.getEcommUserByUsername(authentication.getName());
    }

    public TransitionTrigger resolveTransitionTrigger() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return TransitionTrigger.ADMIN;
        }
        if (auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SELLER"))) {
            return TransitionTrigger.SELLER;
        }
        return TransitionTrigger.CUSTOMER;
    }
}

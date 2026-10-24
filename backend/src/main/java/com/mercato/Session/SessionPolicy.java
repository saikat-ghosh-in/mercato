package com.mercato.Session;
import java.util.Set;
public record SessionPolicy(int idleSeconds, long absoluteSeconds) {
    public static SessionPolicy forRoles(Set<String> roles) {
        if (roles.contains("ROLE_ADMIN")) return new SessionPolicy(1800,43200);
        if (roles.contains("ROLE_SELLER")) return new SessionPolicy(3600,86400);
        return new SessionPolicy(86400,604800);
    }
}

package com.mercato.Security.services;

import com.mercato.Entity.EcommUser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailsImpl implements UserDetails {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String userId;
    private String username;
    private String email;
    @JsonIgnore
    private String password;
    private boolean enabled;
    private boolean accountNonLocked;
    private Collection<? extends GrantedAuthority> authorities;
    private long accessVersion;

    public static UserDetailsImpl build(EcommUser user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .filter(role -> role.getRoleName() != com.mercato.Entity.AppRole.ROLE_SELLER
                        || user.getSellerStatus() == null || user.getSellerStatus() == com.mercato.Entity.SellerStatus.APPROVED)
                .map(role -> new SimpleGrantedAuthority(role.getRoleName().name()))
                .collect(Collectors.toList());

        return new UserDetailsImpl(
                user.getId(),
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                user.isEnabled(),
                !user.isAccountLocked(),
                authorities,
                user.getAccessVersion()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }
}

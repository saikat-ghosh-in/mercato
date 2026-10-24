package com.mercato.Security;
import com.mercato.Security.jwt.AuthEntryPointJwt;import com.mercato.Security.jwt.AuthTokenFilter;import com.mercato.Security.jwt.GuestTokenFilter;import com.mercato.Security.services.UserDetailsServiceImpl;import com.mercato.Session.SessionBoundaryFilter;
import lombok.RequiredArgsConstructor;import org.springframework.boot.web.servlet.FilterRegistrationBean;import org.springframework.context.annotation.*;import org.springframework.security.authentication.AuthenticationManager;import org.springframework.security.authentication.dao.DaoAuthenticationProvider;import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.security.web.SecurityFilterChain;import org.springframework.security.web.context.*;import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
@Configuration @EnableWebSecurity @EnableMethodSecurity @RequiredArgsConstructor
public class SpringSecurityConfig {
 private final UserDetailsServiceImpl userDetailsService;private final AuthEntryPointJwt unauthorizedHandler;private final SessionBoundaryFilter sessionBoundary;
 @Bean public FilterRegistrationBean<AuthTokenFilter> authTokenFilterRegistration(AuthTokenFilter filter){var r=new FilterRegistrationBean<>(filter);r.setEnabled(false);return r;}
 @Bean public FilterRegistrationBean<GuestTokenFilter> guestTokenFilterRegistration(GuestTokenFilter filter){var r=new FilterRegistrationBean<>(filter);r.setEnabled(false);return r;}
 @Bean public FilterRegistrationBean<SessionBoundaryFilter> sessionBoundaryRegistration(SessionBoundaryFilter filter){var r=new FilterRegistrationBean<>(filter);r.setEnabled(false);return r;}
 @Bean public DaoAuthenticationProvider authenticationProvider(){var provider=new DaoAuthenticationProvider(userDetailsService);provider.setPasswordEncoder(passwordEncoder());return provider;}
 @Bean public AuthenticationManager authenticationManager(AuthenticationConfiguration config)throws Exception{return config.getAuthenticationManager();}
 @Bean public PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean public SecurityFilterChain filterChain(HttpSecurity http)throws Exception{
  var csrf=new HttpSessionCsrfTokenRepository();csrf.setHeaderName("X-CSRF-TOKEN");
  http.cors(AbstractHttpConfigurer::disable).csrf(c->c.csrfTokenRepository(csrf).ignoringRequestMatchers(new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/public/cashfree/webhook"),new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/public/cashfree/webhook-v2")))
   .securityContext(c->c.securityContextRepository(new NullSecurityContextRepository()))
   .requestCache(AbstractHttpConfigurer::disable).authenticationProvider(authenticationProvider())
   .addFilterAfter(sessionBoundary,SecurityContextHolderFilter.class)
   .exceptionHandling(e->e.authenticationEntryPoint(unauthorizedHandler))
   .authorizeHttpRequests(a->a.requestMatchers("/api/auth/user","/api/auth/user/**").authenticated().requestMatchers("/images/**","/api/auth/**","/api/cart/**","/api/public/**").permitAll().requestMatchers("/api/test/**","/api/admin/**").hasRole("ADMIN").requestMatchers("/api/seller/**").hasAnyRole("SELLER","ADMIN").anyRequest().authenticated());
  return http.build();
 }
}

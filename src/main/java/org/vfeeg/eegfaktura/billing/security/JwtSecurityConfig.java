package org.vfeeg.eegfaktura.billing.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.vfeeg.eegfaktura.billing.logging.LogContextFilter;

// the only security chain, in every profile: the former DevSecurityConfig (profile "dev") left every endpoint open
@Configuration
public class JwtSecurityConfig {

    private final JwtRequestFilter jwtRequestFilter;

    public JwtSecurityConfig(final JwtRequestFilter jwtRequestFilter) {
        this.jwtRequestFilter = jwtRequestFilter;
    }

    @Bean
    public SecurityFilterChain configure(final HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers("/", "/swagger-ui").permitAll()
                        .anyRequest().hasRole(UserRoles.EEG_ADMIN))
                .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)
                // tenant.id and client.id for the log, only after the token check
                .addFilterAfter(new LogContextFilter(), JwtRequestFilter.class)
                .build();
    }

}

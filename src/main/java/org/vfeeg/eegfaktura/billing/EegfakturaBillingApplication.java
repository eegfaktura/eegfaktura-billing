package org.vfeeg.eegfaktura.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;


// Login is by bearer token only (JwtSecurityConfig): the default user was never reachable, its generated password
// only landed in the start-up log.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class EegfakturaBillingApplication {

    public static void main(String[] args) {

        ConfigurableApplicationContext context = SpringApplication.run(EegfakturaBillingApplication.class, args);

    }

}

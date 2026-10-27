package org.vfeeg.eegfaktura.billing.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;


@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {
    private String noReplyTo = "no-reply@eegfaktura.at";
    private String jwtPublicKeyFile = "";
    /** Longest wait of a final run for another final run of the same community; 0 = no limit. */
    private Duration finalRunLockTimeout = Duration.ofMinutes(30);
}

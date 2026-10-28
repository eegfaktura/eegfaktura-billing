package org.vfeeg.eegfaktura.billing.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {
    private String noReplyTo = "no-reply@eegfaktura.at";
    private String jwtPublicKeyFile = "";
}

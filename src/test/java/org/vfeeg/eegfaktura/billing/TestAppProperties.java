package org.vfeeg.eegfaktura.billing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.test")
@Getter
@Setter
public class TestAppProperties {
    private String storeDocumentsPath = "";
}

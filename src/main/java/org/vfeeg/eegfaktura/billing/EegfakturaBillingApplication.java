package org.vfeeg.eegfaktura.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;


@SpringBootApplication
public class EegfakturaBillingApplication {

    public static void main(String[] args) {

        ConfigurableApplicationContext context = SpringApplication.run(EegfakturaBillingApplication.class, args);

    }

}

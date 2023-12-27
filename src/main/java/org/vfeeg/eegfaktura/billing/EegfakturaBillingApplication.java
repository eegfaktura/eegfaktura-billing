package org.vfeeg.eegfaktura.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.vfeeg.eegfaktura.billing.util.IgnoreExpirationTrustManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;

@SpringBootApplication
public class EegfakturaBillingApplication {

    private static SSLContext sslContext;

    static {
        try {
            String fingerprint = "0BA43230D66B6E65D999BA73FCDB146F6CC10D9A";

            TrustManagerFactory factory;
            factory = TrustManagerFactory.getInstance("X509");
            factory.init((KeyStore) null);
            TrustManager[] trustManagers = factory.getTrustManagers();
            for (int i = 0; i < trustManagers.length; i++) {
                if (trustManagers[i] instanceof X509TrustManager) {
                    trustManagers[i] = new IgnoreExpirationTrustManager((X509TrustManager) trustManagers[i], fingerprint);
                }
            }
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);
        } catch (Exception e) {
            e.printStackTrace();
            sslContext = null;
        }
    }

    public static void main(String[] args) {

        if (sslContext!=null) SSLContext.setDefault(sslContext);
        ConfigurableApplicationContext context = SpringApplication.run(EegfakturaBillingApplication.class, args);
        // Add some demo data
        
    }

}

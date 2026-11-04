package org.vfeeg.eegfaktura.billing.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.vfeeg.eegfaktura.billing.rest.TestTokens;
import org.vfeeg.eegfaktura.billing.util.AppProperties;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The public key is read once when the service is built; a missing or broken key fails the start. */
class JwtTokenServiceTests {

    private static AppProperties keyFile(Path file) {
        AppProperties properties = new AppProperties();
        properties.setJwtPublicKeyFile(file.toString());
        return properties;
    }

    @Test
    void aMissingKeyFileFailsTheStart(@TempDir Path dir) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(keyFile(dir.resolve("missing.pem"))));
        assertThat(e.getMessage(), containsString("JWT_PUBLICKEYFILE"));
    }

    @Test
    void aKeyFileThatIsNoCertificateFailsTheStart(@TempDir Path dir) throws Exception {
        Path broken = Files.writeString(dir.resolve("broken.pem"), "not a certificate");
        assertThrows(IllegalStateException.class, () -> new JwtTokenService(keyFile(broken)));
    }

    @Test
    void theKeyIsReadOnceAndStillVerifiesAfterTheFileIsGone(@TempDir Path dir) throws Exception {
        Path copy = Files.copy(TestTokens.CERT_FILE, dir.resolve("cert.der"));
        JwtTokenService service = new JwtTokenService(keyFile(copy));
        Files.delete(copy);

        String token = TestTokens.admin().substring("Bearer ".length());
        assertThat(service.validateTokenAndGetAuthentication(token), is(notNullValue()));
        assertThat(service.validateTokenAndGetAuthentication("Bearer " + token), is(nullValue()));
    }
}

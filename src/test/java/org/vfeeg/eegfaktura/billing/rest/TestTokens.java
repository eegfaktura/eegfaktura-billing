package org.vfeeg.eegfaktura.billing.rest;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Signs bearer tokens for the web-layer tests with the test-only key pair in
 * {@code src/test/resources/jwt/} (see the README there). On {@code master} {@link
 * org.vfeeg.eegfaktura.billing.security.JwtTokenService} reads only the claims {@code tenant[]},
 * {@code access_groups[]} and {@code preferred_username}; no issuer, no client.
 */
public final class TestTokens {

    public static final String CERT_FILE = "src/test/resources/jwt/test-only-cert.pem";
    static final String KEY_FILE = "src/test/resources/jwt/test-only-private-key.pem";

    public static final String OWN = "RC100001";
    public static final String FOREIGN = "RC999999";
    public static final String ADMIN_GROUP = "EEG_ADMIN";

    private static final Algorithm ALGORITHM = Algorithm.RSA256(readPublicKey(), readPrivateKey());

    private TestTokens() {
    }

    /** "Bearer …" of an EEG_ADMIN of the own community. */
    public static String admin() {
        return bearer(token(List.of(OWN), List.of(ADMIN_GROUP)));
    }

    /** "Bearer …" of a user of the own community without the role EEG_ADMIN. */
    public static String userWithoutAdminRole() {
        return bearer(token(List.of(OWN), List.of("EEG_USER")));
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public static String token(List<String> tenants, List<String> accessGroups) {
        return claims(tenants, accessGroups).sign(ALGORITHM);
    }

    /** Valid claims, expired two minutes ago (the service accepts 60 s leeway). */
    public static String expired() {
        return claims(List.of(OWN), List.of(ADMIN_GROUP))
                .withIssuedAt(Instant.now().minusSeconds(600))
                .withExpiresAt(Instant.now().minusSeconds(120))
                .sign(ALGORITHM);
    }

    /** Valid claims, signed with a key the service does not know. */
    public static String signedWithForeignKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Algorithm foreign = Algorithm.RSA256((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        return claims(List.of(OWN), List.of(ADMIN_GROUP)).sign(foreign);
    }

    /** Correctly signed, but without the {@code tenant} claim. */
    public static String withoutTenantClaim() {
        return JWT.create()
                .withArrayClaim("access_groups", new String[]{ADMIN_GROUP})
                .withClaim("preferred_username", "test-admin")
                .withExpiresAt(Instant.now().plusSeconds(600))
                .sign(ALGORITHM);
    }

    private static JWTCreator.Builder claims(List<String> tenants, List<String> accessGroups) {
        return JWT.create()
                .withArrayClaim("tenant", tenants.toArray(String[]::new))
                .withArrayClaim("access_groups", accessGroups.toArray(String[]::new))
                .withClaim("preferred_username", "test-admin")
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plusSeconds(600));
    }

    private static RSAPublicKey readPublicKey() {
        try {
            var certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(Files.readAllBytes(Path.of(CERT_FILE))));
            return (RSAPublicKey) certificate.getPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + CERT_FILE, e);
        }
    }

    private static RSAPrivateKey readPrivateKey() {
        try {
            String pem = Files.readString(Path.of(KEY_FILE))
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            var spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + KEY_FILE, e);
        }
    }
}

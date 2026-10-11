package org.vfeeg.eegfaktura.billing.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.util.AppProperties;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;

@Service
@Slf4j
public class JwtTokenService {

    private final AppProperties appProperties;

    public JwtTokenService(final AppProperties appProperties,
                           final ResourceLoader resourceLoader) {
        this.appProperties = appProperties;
    }

    public JwtAuthentication validateTokenAndGetAuthentication(final String token) {
        JwtAuthentication jwtAuthentication = null;
        try {
            final Algorithm algorithm = Algorithm.RSA256(getRSAPublicKey(), null);
            final JWTVerifier verifier = JWT.require(algorithm).acceptLeeway(60).build();
            DecodedJWT decodedJWT = verifier.verify(token.replace("Bearer ", ""));
            JSONObject header = new JSONObject(decode(decodedJWT.getHeader()));
            JSONObject payload = new JSONObject(decode(decodedJWT.getPayload()));

            ArrayList<Authority> authorities = new ArrayList<>();
            // tenant und access_groups koennen fehlen (z. B. Betreiber-Konten ohne eigene
            // Gemeinschaft); dann bleibt die Liste leer statt das ganze Token zu verwerfen.
            JSONArray authorityJSONArray = payload.optJSONArray("tenant");
            if (authorityJSONArray != null) {
                for (var i=0; i<authorityJSONArray.length(); i++) {
                    authorities.add(new Authority(authorityJSONArray.getString(i)));
                }
            }
            JSONArray accessGroupJSONArray = payload.optJSONArray("access_groups");
            if (accessGroupJSONArray != null) {
                for (var i=0; i<accessGroupJSONArray.length(); i++) {
                    var access_group = accessGroupJSONArray.getString(i).replace("/", "");
                    authorities.add(new Authority("ROLE_"+access_group));
                }
            }

            String username = payload.getString("preferred_username");

            jwtAuthentication = new JwtAuthentication(username, authorities, hasRealmRole(payload, "superuser"));
        } catch (Exception e) {
            log.warn("Failed to validate JWT token: {}", e.getMessage(), e);
        }
        return jwtAuthentication;
    }

    static boolean hasRealmRole(final JSONObject payload, final String role) {
        JSONObject realmAccess = payload.optJSONObject("realm_access");
        JSONArray roles = realmAccess == null ? null : realmAccess.optJSONArray("roles");
        if (roles == null) {
            return false;
        }
        for (var i = 0; i < roles.length(); i++) {
            if (role.equals(roles.optString(i))) {
                return true;
            }
        }
        return false;
    }

    private static String decode(String encodedString) {
        return new String(Base64.getUrlDecoder().decode(encodedString));
    }

    private RSAPublicKey getRSAPublicKey() throws Exception  {
        var jwtPublicKeyFile =  appProperties.getJwtPublicKeyFile();
        final Path path = Paths.get(jwtPublicKeyFile);
        final byte[] publicKeyBytes = Files.readAllBytes(path);
        var certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(publicKeyBytes));
        return (RSAPublicKey) certificate.getPublicKey();
    }

}
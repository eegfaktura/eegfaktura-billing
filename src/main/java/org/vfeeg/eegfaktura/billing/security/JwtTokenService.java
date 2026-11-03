package org.vfeeg.eegfaktura.billing.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.util.AppProperties;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;

@Service
@Slf4j
public class JwtTokenService {

    private final JWTVerifier verifier;

    /** Loads the public key once; a missing or unreadable key file fails the start. */
    public JwtTokenService(final AppProperties appProperties) {
        final Algorithm algorithm = Algorithm.RSA256(readRSAPublicKey(appProperties.getJwtPublicKeyFile()), null);
        this.verifier = JWT.require(algorithm).acceptLeeway(60).build();
    }

    /** @param token the token without the "Bearer " prefix (JwtRequestFilter strips it) */
    public JwtAuthentication validateTokenAndGetAuthentication(final String token) {
        JwtAuthentication jwtAuthentication = null;
        try {
            DecodedJWT decodedJWT = verifier.verify(token);
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

            // email: Empfaenger der Testmail "an mich" (Mail-Texte); fehlt der Claim, bleibt er leer.
            String email = payload.optString("email", null);

            // azp: the Keycloak client that requested the token, only for the log (client.id)
            String clientId = payload.optString("azp", null);

            jwtAuthentication = new JwtAuthentication(username, authorities, hasRealmRole(payload, "superuser"), email,
                    clientId);
        } catch (Exception e) {
            // expired or invalid tokens are routine (the access line shows the refusal): one debug line, type only -
            // the message of a decode error can quote the token's decoded content
            log.atDebug().addKeyValue("error.type", e.getClass().getName()).log("Token rejected");
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

    private static RSAPublicKey readRSAPublicKey(final String jwtPublicKeyFile) {
        try {
            final byte[] publicKeyBytes = Files.readAllBytes(Paths.get(jwtPublicKeyFile));
            var certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(publicKeyBytes));
            return (RSAPublicKey) certificate.getPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read the JWT public key (an X.509 certificate with an RSA key) from"
                    + " app.jwtpublickeyfile (env JWT_PUBLICKEYFILE) '" + jwtPublicKeyFile + "'", e);
        }
    }

}
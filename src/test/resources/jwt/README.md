# Test-only JWT key pair — generated locally, never committed

The web-layer tests (M2, `org.vfeeg.eegfaktura.billing.rest.TestTokens`) sign and verify the bearer
tokens of the `@WebMvcTest` slices with a test-only key pair in this directory. The key pair is
**not committed**: the secret scan of the repository refuses any private key, test-only or not.
No environment trusts this certificate.

- `test-only-cert.pem`: self-signed X.509 certificate, the format `JwtTokenService` reads from
  `app.jwt-public-key-file` (set by `WebSliceTest` through `@TestPropertySource`).
- `test-only-private-key.pem`: the matching RSA private key, PKCS#8 PEM, read by `TestTokens`.

Create both here before running the web-layer tests:

```bash
openssl req -x509 -newkey rsa:2048 -nodes -days 36500 \
  -keyout test-only-private-key.pem -out test-only-cert.pem \
  -subj "/CN=eegfaktura-billing test-only - NOT A SECRET"
```

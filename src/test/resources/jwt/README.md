# Test-only JWT key pair — not a secret

`test-only-private-key.pem` and `test-only-cert.pem` exist **only for the web-layer tests** (M2,
`org.vfeeg.eegfaktura.billing.rest.TestTokens`). They sign and verify the bearer tokens of the
`@WebMvcTest` slices. No environment trusts this certificate; a secret scanner that flags the
private key can ignore it.

- `test-only-cert.pem`: self-signed X.509 certificate, the format `JwtTokenService` reads from
  `app.jwt-public-key-file` (set by `WebSliceTest` through `@TestPropertySource`).
- `test-only-private-key.pem`: the matching RSA private key, PKCS#8 PEM, read by `TestTokens`.

Created 2026-10-02, valid 100 years:

```bash
openssl req -x509 -newkey rsa:2048 -nodes -days 36500 \
  -keyout test-only-private-key.pem -out test-only-cert.pem \
  -subj "/CN=eegfaktura-billing test-only - NOT A SECRET"
```

package com.appause.android.data.pro

/**
 * Server-side public key used to VERIFY Appause Pro license tokens (JWT, RS256).
 *
 * Security model (Plan B):
 * - This is a VERIFICATION-ONLY key. It can confirm a token was signed by the
 *   server, but it CANNOT mint new tokens. That secret (the RSA private key)
 *   lives only in the Cloudflare Worker's secret environment and is never
 *   shipped in the app or committed to the repo.
 * - Because the signing key stays server-side, forking the open-source repo
 *   gives you the "inspector", not the "ticket printer" — you can validate
 *   tokens but not create valid Pro tokens.
 *
 * Deployment:
 * - [SERVER_PUBLIC_KEY_PEM] now holds the **PRODUCTION** public key. The
 *   matching RSA private key lives ONLY in the Cloudflare Worker secret
 *   `APPAUSE_PRIVATE_KEY` and is never shipped in the app or committed to the
 *   repo. Do not paste the private key anywhere in this project.
 * - The DEV token (used during early testing) is intentionally NOT device-bound
 *   and is now rejected by any build compiled with `IS_PRODUCTION_KEY = true`,
 *   because production tokens MUST include a "device" claim equal to the device
 *   fingerprint (see [DeviceKeyStore]).
 */
object ServerKeys {
    const val SERVER_PUBLIC_KEY_PEM = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqwwP+me3Ld+uuqT3gwcU
BosqCjoSCO2dS2/yH0xjb+YXXVPtZx/RToTZQaZa8IP2zCqt3konhutOMj+orAoO
IO3v5oe+lBq2ezcRerqeIS1pdaSA3Pzthpty5EwUQd3hZ9Pjf3IGuKgQsghZmqDy
MyCdnbe72OP/NLMXK0nuI9nj6175ARbf+kjnBWgqWXSzV0UZd8ecL++N12Gm68rn
moeVUp80ygV2KsNrlNvICR53qOvltkvG8M+tIPksJzOiqPtEjHFJQr3H2qDNkyXR
fPrtbPQDaDJgfJPHadEta+McZ+HjhWJ1ZCN/zDEp8sV9iW82qsy1SbM3yg5jjxQu
NwIDAQAB
-----END PUBLIC KEY-----"""

    /**
     * Set to true once [SERVER_PUBLIC_KEY_PEM] has been replaced with the real
     * production key. Used only for clarity/logging; the verifier always uses
     * the embedded PEM above.
     */
    const val IS_PRODUCTION_KEY = true
}

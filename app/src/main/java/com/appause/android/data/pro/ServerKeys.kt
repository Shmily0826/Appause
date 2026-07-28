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
 * - [SERVER_PUBLIC_KEY_PEM] currently holds a DEV key so the client can be
 *   tested before the Worker exists. Generate a fresh production RSA key pair,
 *   paste ONLY the public half here, and keep the private half in the Worker.
 * - The DEV token is intentionally NOT device-bound, so it verifies on any
 *   device for local testing. Production tokens MUST include a "device" claim
 *   equal to the device fingerprint (see [DeviceKeyStore]).
 */
object ServerKeys {
    const val SERVER_PUBLIC_KEY_PEM = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyWFacYAtSxr47o0YJ32V
oxcxEBpVLZO5pHyrghY3zLz0uV0tOLgpGflKfoJIcs/q/SQShfOTph/ZqdAl7l+3
6SAoIep60PJl56SsO/lDHDizXXLppjy/fzf6gsCoSuw6WRRScuf8XCIrItfndG35
ANkkgLX84d+aJtn92z1N75w86fbMMSS0Zimu7Mjf0h9xOypzA6uvgXYDjFabPe3n
CqAqXc6/VWY2wb4IMNAA7/r4lxjEhiXWD+/3pp7mpQ5UJ86YzpUt0lemh65qtPwY
EKkhCeie85UIRaSkMINqUmDAG0Tz+58G69MiwGHxSnntU7xukbgu84W2QDYq9dtU
WQIDAQAB
-----END PUBLIC KEY-----"""

    /**
     * Set to true once [SERVER_PUBLIC_KEY_PEM] has been replaced with the real
     * production key. Used only for clarity/logging; the verifier always uses
     * the embedded PEM above.
     */
    const val IS_PRODUCTION_KEY = false
}

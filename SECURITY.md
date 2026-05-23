# Security policy

## Reporting a vulnerability

If you believe you have found a security issue in `kmp-targets`, please **do not** open a public issue. Instead, report it privately via GitHub's security advisory flow:

- <https://github.com/rsicarelli/kmp-targets/security/advisories/new>

I will acknowledge receipt within 7 days and work with you on a coordinated fix. Once a patch is available, the advisory will be published with credit (unless you prefer to remain anonymous).

## Supported versions

The project is in early pre-release (`0.x`). Only the latest published version is supported. Once `1.0.0` ships, this section will be updated with a versioning policy.

## Scope

Security-relevant areas of this repository:

- The Gradle plugin code under `gradle-plugin/` — anything that could affect a consumer's build (e.g. arbitrary code execution during plugin apply, classpath leakage, malicious task wiring).
- GitHub Actions workflows under `.github/workflows/` — particularly write-permission grants, secret usage, or third-party action references.

Out of scope:

- Vulnerabilities in upstream dependencies (Gradle, Kotlin, etc.) — report those to the respective project. We will track and bump versions via Dependabot.
- Issues only reproducible by an authenticated maintainer (e.g. settings misconfiguration).

Thanks for helping keep `kmp-targets` and its consumers safe.

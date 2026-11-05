# Security Policy / セキュリティポリシー

## Supported Versions / サポート対象バージョン

| Version | Supported |
| :--- | :--- |
| `1.0.x` | :white_check_mark: Yes |

## Security Architecture Highlights / セキュリティ設計の概要

- **Minimal Android Permissions**: MasuSheet requests only `android.permission.INTERNET` (used exclusively when the user configures an optional AI API endpoint). All local file access goes through user-consented Storage Access Framework (SAF) and Scoped Storage (`MediaStore`).
- **Hardware-Backed Secret Storage**: AI API keys are encrypted at rest via `AndroidKeyStore` (`AES/GCM/NoPadding`, 256-bit key, 128-bit authentication tag) and excluded from cloud backup and device-to-device transfer.
- **Transport Security**: Cleartext HTTP is disabled globally (`android:usesCleartextTraffic="false"`), with a strict loopback-only exception (`127.0.0.1` / `localhost`) for optional on-device local LLM servers.
- **Memory & Panic Safety**: Rust calculation crates forbid `unsafe` code (`unsafe_code = "forbid"`), and every JNI FFI boundary in `crates/jni` is isolated with `std::panic::catch_unwind` and bounded input limits.

## Reporting a Vulnerability / 脆弱性の報告方法

Please **do not** report unpatched security vulnerabilities in public GitHub Issues. Instead, use **[GitHub Private Vulnerability Reporting](https://github.com/ray-dev-light/MasuSheet/security/advisories/new)** on this repository so we can investigate and publish a coordinated fix.

未修正のセキュリティ脆弱性は公開 Issue ではなく、本リポジトリの **[GitHub Private Vulnerability Reporting](https://github.com/ray-dev-light/MasuSheet/security/advisories/new)** から非公開でご報告ください。

# Third-party components

Runtime dependencies are obtained from their official Maven repositories; upstream source is not vendored into this repository.

| Component | Version | License / upstream |
|---|---|---|
| Kotlin standard library | 2.1.21 | Apache-2.0 — https://github.com/JetBrains/kotlin |
| AndroidX Core and transitive AndroidX libraries | 1.15.0 | Apache-2.0 — https://android.googlesource.com/platform/frameworks/support/ |
| NanoHTTPD core | 2.3.1 | BSD-3-Clause — https://github.com/NanoHttpd/nanohttpd |
| ZXing core | 3.5.3 | Apache-2.0 — https://github.com/zxing/zxing |

JUnit 4.13.2 and JSON-java 20240303 are test-only dependencies and are not packaged as runtime libraries. Gradle and the Android/Kotlin build plugins are build-time tools. Refer to each upstream distribution for its full notices and license terms.

TVInbox is a new, minimal implementation. It does not copy the UI, business logic, or assets of Universal Installer, FBS, or a full file manager.

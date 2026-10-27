# Local test APK — 5.2.8-test

Built successfully with Gradle 9.7.1, JDK 21 and Android SDK 37.0 / Build Tools 37.0.0. Package `tv.own.owntv.tdt2`, versionCode 100007, minSdk 26, targetSdk 36. Standard debug variant contains arm64-v8a and armeabi-v7a. All current local changes are included; no GitHub push or release.

APK: Channelplus-5.2.8-test.apk
Size: 96256526 bytes
SHA-256: 825378832b33efbfd5c1ee18dc35365c651c92dbe9d4118a33d027b52b151657

APK Signature Scheme v2 verified with the retained TCL Tuner Probe Test certificate. Certificate SHA-256: 801bde35b2bcefcfe4fa65348b59d8e9b7053600b88842116e22f1979724be2f. ZIP integrity and manifest/package/version checks passed. TV installation and behavior are not tested here.

Seventeen targeted tests passed with JUnit 4.13.2: eight editor domain tests, six tuner/timeline domain tests, and three grouped-paging tests (including stable M3U rename identity, cross-page collapse and invalidation). The grouped-paging harness includes the Android SDK stub classes required by PagingLogger. The editor SQL/resource checks and both app/core translation-inventory checks passed. Added technical literals were explicitly classified rather than expanding the translation-debt baseline.

Older notes describing the Gradle download blocker are superseded by this successful build. The configured proxy must be read from the current command environment rather than reusing a port from an earlier execution. Toolchains and dependencies were downloaded locally; no remote build was used.

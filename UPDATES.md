# Updates
- Package name is fixed: com.mawjalbarq.app. Every APK is signed with the same committed key (release.keystore), so Android installs each build as an update over the old one.
- Never change the keystore or package name; doing so forces a full reinstall.
- In-app flow: check release `demo` -> one tap downloads the APK in-app -> system install prompt opens directly.
- update.json is attached to each release and always points at the latest APK.
- Truly silent updates (no prompt at all) are only possible through the Play Store. Outside it Android always asks the user to confirm.

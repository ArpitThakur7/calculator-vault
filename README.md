# Calculator Vault

A working calculator that secretly hides a **private, offline, encrypted** photo &
video vault. To anyone else it's just a calculator — type your secret code and
press **=** to open the hidden gallery.

Built and tested for a Xiaomi 14 on HyperOS (Android 15); runs on any Android 8+
phone (fingerprint unlock needs Android 11+).

> ⚠️ **Personal project, not audited security software.** It's designed for
> everyday privacy (keeping photos out of the gallery and behind strong
> encryption), not for defeating a determined forensic attacker. Use a strong
> code.

---

## Features

- **Disguised as a calculator** — fully functional (`+ − × ÷`, decimals, `%`),
  with the vault hidden behind a secret code.
- **Secret code you set yourself** — on first launch the app asks you to create
  it. Type the code and press `=` to unlock.
- **AES‑256‑GCM encryption** — every file is encrypted at rest with a random
  master key, which is itself wrapped by a key derived from your code
  (PBKDF2, 120k iterations). On disk, files have random names and scrambled
  contents.
- **Fingerprint / face unlock** — bound to the hardware Keystore, as a second way
  in and a recovery path if you forget the code.
- **In‑app photo picker** — browse and import photos/videos from your gallery
  without the flaky system file picker. Imported files are **copied** in;
  originals are left untouched.
- **Save back to gallery** — decrypt and export any item back to the normal
  gallery whenever you want.
- **Recycle bin** — deleting moves items to a trash folder first; restore or
  permanently delete later.
- **Multi‑select** — long‑press to select many items, then bulk save or delete.
- **Rich viewer** — swipe between items, pinch‑to‑zoom photos, and play videos
  in‑app.
- **Fully offline** — the app declares **no `INTERNET` permission**, so it is
  physically incapable of sending anything off the device. No accounts, no cloud,
  no analytics.

---

## Security model

- Files are **encrypted at rest** (AES‑256‑GCM). Without the key the bytes are
  meaningless, which protects against file managers, other apps, and someone
  poking around an unlocked phone.
- Your **code is never stored** (not even a hash). It's verified by successfully
  decrypting the key file.
- The **master key lives only in memory** while the vault is unlocked and is
  cleared on lock / process death.
- **No backdoor:** if you lose *both* the code and your fingerprint, the files
  cannot be recovered.

### Storage & the uninstall trade‑off

The vault lives in the app's own external folder
(`Android/data/com.example.calcvault/files/vault`), which needs **no storage
permission** and is ignored by the gallery. The one cost: **uninstalling the app
removes the vault**. Use **Save to gallery** to export anything you want to keep
before uninstalling.

The app requests read access to **photos & videos** only so you can pick which
ones to import.

---

## Build from source

Requires **Android Studio** (or the Android SDK + JDK 17).

```bash
git clone https://github.com/ArpitThakur7/calculator-vault.git
cd calculator-vault
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Or open the project
in Android Studio and press **Run**.

### Install on a phone

1. Copy the APK to the phone (USB, Drive, email, etc.).
2. Open it in a file manager and allow "install from unknown sources" for that app.
3. It installs as **Calculator**.

---

## First run

1. Open **Calculator** → the setup screen appears.
2. Create your secret code (twice) — this is the encryption key, so make it good.
3. Turn on fingerprint unlock (optional) → **Create vault**.
4. Tap **Add photo / video** to import, type your code + `=` to get back in later.

---

## Project structure

```
app/src/main/java/com/example/calcvault/
  MainActivity.kt       app shell, navigation, permissions, unlock orchestration
  CalculatorScreen.kt   calculator + secret-code / long-press unlock
  Evaluator.kt          math engine
  VaultCrypto.kt        AES-256-GCM encryption (unit-tested)
  VaultManager.kt       create/open vault, change code, biometric wrap
  VaultSession.kt       in-memory master key (cleared on lock)
  SecurityPrefs.kt      biometric-wrapped key (no code stored)
  KeystoreBio.kt        hardware Keystore key for fingerprint unlock
  BiometricAuth.kt      BiometricPrompt bound to a crypto cipher
  VaultRepository.kt    encrypt on import, decrypt on view, trash, export
  VaultScreens.kt       setup, gallery, viewer, recycle bin, settings, picker
app/src/test/java/.../VaultCryptoTest.kt   crypto round-trip tests
```

---

## Tech

Kotlin · Jetpack Compose (Material 3) · AndroidX Biometric · MediaStore ·
Android Keystore · AES‑256‑GCM / PBKDF2 (javax.crypto).

## License

Released under the [MIT License](LICENSE).

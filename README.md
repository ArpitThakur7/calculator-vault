<div align="center">

# 🧮 Calculator Vault

### It's a calculator. Until it isn't.

**A fully working calculator that hides an offline, AES‑256 encrypted photo & video vault behind a secret code.**

<br/>

![Platform](https://img.shields.io/badge/platform-Android%208%2B-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Crypto](https://img.shields.io/badge/AES--256--GCM-encrypted-black)
![Offline](https://img.shields.io/badge/internet-%E2%9D%8C%20none-red)
![License](https://img.shields.io/badge/license-MIT-blue)

<br/>

```
┌─────────────────────┐        type secret code        ┌─────────────────────┐
│   7  8  9  ÷        │   ───────────────────────►     │   🔒  YOUR  VAULT    │
│   4  5  6  ×        │          press  =              │   🖼  🎞  🖼  🎞       │
│   1  2  3  −        │   ◄───────────────────────     │   encrypted · offline│
│   0  .  =  +        │        (looks like math)       │   fingerprint locked │
└─────────────────────┘                                └─────────────────────┘
```

</div>

---

## 🎭 The pitch

Everyone who picks up your phone sees a boring calculator. They can add, subtract, do their taxes — whatever.

But type **your** secret code and hit `=`, and the calculator melts away into a private gallery that **no one else knows exists**. Your photos and videos live there **encrypted**, **offline**, and **invisible to the normal gallery**.

No cloud. No account. No internet permission in the entire app — it is *physically incapable* of phoning home. 🛜🚫

> ⚠️ **Real talk:** this is a personal project, not audited security software. It's built for everyday privacy — keeping photos out of the gallery and behind strong encryption — not for beating a determined forensic lab. Pick a strong code and you're in great shape.

---

## ✨ What it does

| | Feature | |
|---|---|---|
| 🎭 | **Perfect disguise** | A genuinely functional calculator (`+ − × ÷ %`, decimals). Nobody suspects a thing. |
| 🔑 | **Your own secret code** | You set it on first launch. It's not just a password — it's the encryption key. |
| 🔐 | **AES‑256‑GCM encryption** | Every file scrambled at rest with a random master key, wrapped by your code via PBKDF2 (120k iterations). |
| 👆 | **Fingerprint / face unlock** | Hardware‑Keystore‑bound. A fast second way in, and your recovery path. |
| 🖼 | **In‑app importer** | Pick photos/videos straight from your gallery. Imports are **copied** — your originals stay put. |
| 📤 | **Export back out** | Changed your mind? Decrypt and drop any item back into the normal gallery. |
| 🗑 | **Recycle bin** | Delete → trash first. Restore or nuke permanently later. |
| ✅ | **Multi‑select** | Long‑press to grab a bunch, then bulk save or delete. |
| 🔎 | **Rich viewer** | Swipe between items, pinch‑to‑zoom photos, play videos in‑app. |
| 🛜 | **Zero internet** | No `INTERNET` permission. No cloud, no accounts, no analytics, no tracking. Ever. |

---

## 🛡 How the lock actually works

```
   your secret code
          │
          ▼
   ┌──────────────┐     PBKDF2 (120,000 rounds)
   │     KEK      │  ◄──────────────────────────  random salt
   └──────┬───────┘
          │ unwraps
          ▼
   ┌──────────────┐
   │  master DEK  │  ◄── random 256‑bit key, lives ONLY in memory while unlocked
   └──────┬───────┘
          │ encrypts / decrypts
          ▼
   🖼 🎞  your files  →  random names + scrambled bytes on disk
```

- 🧠 **Your code is never stored.** Not even a hash of it. It's verified by whether it can actually *decrypt* the key file.
- 💨 **The master key lives only in RAM** while the vault is open, and is wiped on lock / process death.
- 🚪 **No backdoor.** Lose *both* your code and your fingerprint and the files are gone forever. That's the point.
- 🎲 **On disk it's noise.** File managers and other apps see randomly‑named files full of meaningless bytes.

### 📦 Storage & the one honest trade‑off

The vault lives in the app's own folder (`Android/data/com.example.calcvault/files/vault`) — **no storage permission needed**, and the gallery never sees it. The catch: **uninstalling the app deletes the vault.** Use **Save to gallery** to export anything you want to keep first.

The app asks for photo/video read access for *one* reason only: so you can pick which ones to import.

---

## 🚀 Build it yourself

Needs **Android Studio** (or the Android SDK + JDK 17).

```bash
git clone https://github.com/ArpitThakur7/calculator-vault.git
cd calculator-vault
./gradlew assembleDebug
```

Your APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Or just open the project in Android Studio and smash **Run** ▶️.

### 📲 Put it on a phone

1. Copy the APK over (USB, Drive, email — whatever).
2. Open it in a file manager, allow *"install from unknown sources"* for that app.
3. It installs as a humble **Calculator**. 🧮

---

## 🏁 First run

1. Open **Calculator** → the setup screen appears.
2. Create your secret code (twice). **This is the encryption key — make it good.** 💪
3. Optionally turn on fingerprint unlock → **Create vault**.
4. Hit **Add photo / video** to import. Later, just type `code` + `=` to get back in.

---

## 🧩 Under the hood

```
app/src/main/java/com/example/calcvault/
  MainActivity.kt       app shell · navigation · permissions · unlock orchestration
  CalculatorScreen.kt   the calculator + secret-code / long-press unlock
  Evaluator.kt          the math engine
  VaultCrypto.kt        AES-256-GCM encryption  (unit-tested ✅)
  VaultManager.kt       create/open vault · change code · biometric wrap
  VaultSession.kt       in-memory master key (cleared on lock)
  SecurityPrefs.kt      biometric-wrapped key (no code stored)
  KeystoreBio.kt        hardware Keystore key for fingerprint unlock
  BiometricAuth.kt      BiometricPrompt bound to a crypto cipher
  VaultRepository.kt    encrypt on import · decrypt on view · trash · export
  VaultScreens.kt       setup · gallery · viewer · recycle bin · settings · picker
app/src/test/java/.../VaultCryptoTest.kt   crypto round-trip tests
```

**Stack:** Kotlin · Jetpack Compose (Material 3) · AndroidX Biometric · MediaStore · Android Keystore · AES‑256‑GCM / PBKDF2 (`javax.crypto`).

Built & tested on a **Xiaomi 14 (HyperOS, Android 15)**; runs on any Android 8+ phone (fingerprint needs Android 11+).

---

## 📜 License

[MIT](LICENSE) — do whatever you want with it. 🤝

<div align="center">
<br/>
<sub>Looks like a calculator. Guards like a vault. 🧮🔒</sub>
</div>

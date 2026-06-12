# 🎵 Nexora Player

> **Your universe of sound & vision** — A sleek, modern media player for Android.

![Android](https://img.shields.io/badge/Android-24%2B-brightgreen)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue)
![Build](https://github.com/YOUR_USERNAME/nexora-player/actions/workflows/build.yml/badge.svg)

## ✨ Features

- 🎵 Audio player with MediaStore integration
- 📹 Video file browser
- 🔍 Real-time search across all media
- ❤️ Favourite tracks
- 🌙 Dynamic greeting (morning / afternoon / evening)
- 🎨 Deep purple + cyan dark theme
- 📱 Animated splash screen
- 🔔 Background playback service (Media3 / ExoPlayer)

## 🏗️ Build locally

```bash
# Clone the repo
git clone https://github.com/YOUR_USERNAME/nexora-player.git
cd nexora-player

# Debug APK
./gradlew assembleDebug

# Release APK (needs keystore env vars — see below)
./gradlew assembleRelease
```


## 🔐 GitHub Actions – Release signing

Add these **Repository Secrets** (`Settings → Secrets → Actions`):

| Secret               | Value                                          |
|----------------------|------------------------------------------------|
| `KEYSTORE_BASE64`    | `base64 -i your-keystore.jks` output          |
| `KEYSTORE_PASSWORD`  | Store password                                 |
| `KEY_ALIAS`          | Key alias inside the keystore                  |
| `KEY_PASSWORD`       | Key password                                   |

### Generate a keystore (one-time):

```bash
keytool -genkey -v \
  -keystore nexora-release.jks \
  -alias nexora \
  -keyalg RSA -keysize 2048 \
  -validity 10000

# Then encode it:
base64 -i nexora-release.jks | pbcopy   # macOS
base64 nexora-release.jks               # Linux – paste output into secret
```

### Trigger a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions will build, sign, and attach the APK to the release automatically.

## 📁 Project structure

```
nexora-player/
├── app/
│   └── src/main/
│       ├── java/com/nexora/player/
│       │   ├── SplashActivity.kt
│       │   ├── MainActivity.kt
│       │   ├── PlayerActivity.kt
│       │   ├── adapters/MediaAdapter.kt
│       │   ├── fragments/{Home,Library,Search,Settings}Fragment.kt
│       │   ├── models/MediaItem.kt
│       │   ├── services/NexoraPlaybackService.kt
│       │   ├── utils/{MediaScanner,TimeUtils}.kt
│       │   └── viewmodels/{Home,Library}ViewModel.kt
│       └── res/  (layouts, drawables, navigation, values…)
└── .github/workflows/build.yml
```

## 📄 License

MIT © 2024 Nexora Player


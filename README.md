# 🎵 FLAC Audio Player for Android

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)

A modern, lightweight, and high-fidelity local FLAC audio player built natively for Android using **Kotlin**, **Jetpack Compose**, and **Jetpack Media3 (ExoPlayer)**. Designed with Material 3 guidelines for an aesthetic, dynamic audio listening experience.

---

## Features

- **High-Resolution FLAC Playback:** Powered by Jetpack Media3 (ExoPlayer) with full support for high sample rates and bit depths.
- **Embedded Album Art Extraction:** Automatically reads and displays cover art embedded inside FLAC Vorbis Comments / metadata blocks.
- **Dynamic Material 3 UI:** Adaptive color scheme automatically derived from the album art palette.
- **5-Band Built-in Equalizer:** Integrated `AudioFx` equalizer with manual frequency gain control and presets (Flat, Bass Boost, Rock, Pop, etc.).
- **Modern Dark Theme:** Minimalist and clean aesthetic focused on visual clarity and low power consumption.

---

## Tech Stack & Dependencies

- **Language:** [Kotlin](https://kotlinlang.org/)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material Design 3)
- **Audio Engine:** [Android Jetpack Media3 (ExoPlayer)](https://developer.android.com/guide/topics/media/media3)
- **Image Loading & Palette:** [Coil](https://coil-kt.github.io/coil/) & [Android Palette API](https://developer.android.com/training/material/palette-colors)
- **Equalizer Engine:** Android `AudioFx` Equalizer API

---

## Getting Started

### Prerequisites
- Android Studio / AndroidIDE / Code On The Go
- JDK 17 or higher
- Android SDK 24 (Android 7.0) or higher (Target SDK 34+)

### Building from Source

1. **Clone the repository:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
   cd YOUR_REPOSITORY
   ```

2. **Build Debug APK via Gradle:**
   ```bash
   ./gradlew assembleDebug
   ```

3. The generated APK will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## Download & Installation

You can download the latest pre-release APK directly from the [GitHub Releases](../../releases) section:

1. Go to the **Releases** tab in this repository.
2. Under the latest **Pre-release**, download `base.apk` (or `app-debug.apk`).
3. Install the APK on your Android device (ensure "Install from Unknown Sources" is enabled in your device settings).

---

## Contributing

Contributions, bug reports, and feature requests are welcome! Feel free to check the [Issues](../../issues) page.

---

## License

This project is licensed under the [MIT License](LICENSE).
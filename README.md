<div align="center">

# Neyon

**Neyon is a free and open-source manga reader for Android with built-in online content sources.**  
*A revival and continuation of the project formerly known as Futon.*

![Android 6.0](https://img.shields.io/badge/android-6.0+-brightgreen)
[![License](https://img.shields.io/github/license/AppFuton/Futon)](./LICENSE)

</div>

## Architecture & Tech Stack

Neyon is a multi-module Android application architected using the standard **MVVM (Model-View-ViewModel)** design pattern, backed by dependency injection and comprehensive background task synchronization.

- **Language:** Kotlin (100%)
- **Dependency Injection:** [Hilt](https://dagger.dev/hilt/)
- **Local Persistence & ORM:** Room
- **Asynchronous Programming:** Kotlin Coroutines & Flow
- **Network & Parsing:** OkHttp3, Retrofit, and [neyon-parsers-redo](https://github.com/san9-dunes/neyon-parsers-redo)
- **Image Loading:** Coil 3 (Network, GIF, SVG, AVIF support)
- **Background Processing:** AndroidX WorkManager (powered by Hilt-Work)
- **UI:** ViewBinding, Fragment-KTX, ViewPager2, Material Design Components
- **Error Tracking:** Sentry (Android)
- **Code Quality:** Detekt & Ktlint

### Main Features

* Online manga catalogues (with 500+ manga sources)
* Search manga by name, genres and more filters
* **New:** Personalized "Smart" Suggestions feed powered by a client-side recommendation engine
* **New:** "Find Similar" fallback in alternatives dialogue to instantly pivot to global search
* **New:** "Pin for suggestion" feature on genre and tag chips to customize your feed
* **Optimized:** Quick unpinning and removal of saved tags directly from the Suggestions feed UI
* **Optimized:** History "Fix Sources" feature cleanly rerouted to prevent network rate-limiting
* **Optimized:** Interleaved caching feed aggregator for ultra-fast, stutter-free Suggestions tab browsing
* Favorites organized by user-defined categories
* Reading history, bookmarks and incognito mode support
* Download manga and read it offline. Third-party CBZ archives are also supported
* Clean and convenient Material You UI, optimized for phones, tablets and desktop
* Standard and Webtoon-optimized customizable reader, gesture support on reading interface
* Notifications about new chapters with updates feed, manga recommendations (with filters)
* Integration with manga tracking services: Shikimori, AniList, MyAnimeList, Kitsu
* Password / fingerprint-protected access to the app
* Automatically sync app data with other devices on the same account
* Support for older devices running Android 6.0+

## Development Setup

### Prerequisites

- **JDK 17** (recommended: [Temurin](https://adoptium.net/temurin/releases/) distribution)
- **Android SDK** (compile SDK 36, build tools 35.0.0, minimum SDK 23)
- **Android Studio** (recommended) or Android SDK command-line tools

### Building the Project
#### Currently working on dev branch
1. **Clone the repository:**
   ```bash
   git clone https://github.com/AppNeyon/Neyon.git -b dev
   cd Neyon
   ```

2. **Build debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```

## License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

You may copy, distribute and modify the software as long as you track changes/dates in source files. Any modifications
to or software including (via compiler) GPL-licensed code must also be made available under the GPL along with build &
install instructions.

## DMCA disclaimer

The developers of this application do not have any affiliation with the content available in the app and does not store
or distribute any content. This application should be considered a web browser, all content that can be found using this
application is freely available on the Internet. All DMCA takedown requests should be sent to the owners of the website
where the content is hosted.

---

### Acknowledgments

**Neyon is a revival of the Futon project, which was built upon the exceptional work of the [Kotatsu](https://github.com/KotatsuApp/Kotatsu) project.**

We are deeply grateful to:
* **The original Futon and Kotatsu developers** for creating such an outstanding manga reader and making it open source.
* **The Kotatsu community** for their contributions, testing, and support.
* **Parser contributors** who maintain the extensive library of manga sources.

This project stands on the shoulders of giants. The previous teams' dedication to creating a feature-rich, user-friendly manga reader has provided an incredible foundation for Neyon to build upon.

# CookLink AI

Android app that turns cooking videos, links, photos and notes into structured recipes. Kotlin + Jetpack Compose, Material 3, Room, DataStore.

## Status: Phase 1 of 7

| Phase | Scope | Status |
|---|---|---|
| 1 | UI, navigation, recipe storage | **Done** |
| 2 | URL and text import (AI) | Next |
| 3 | Image and video analysis | — |
| 4 | AI recipe generation, confidence indicators | — |
| 5 | Interactive cooking mode | — |
| 6 | Personal AI chef, smart shopping | — |
| 7 | Testing, optimisation, release APK | — |

### What works in Phase 1

- Five-tab navigation: Home · Recipes · **Import** (raised centre button) · Shopping · Profile
- Recipe library stored on the device (Room), works offline
- Create and edit recipes: cover photo, times, difficulty, servings, ingredients, steps with minutes and temperature, equipment, tags, nutrition, source links, personal notes
- Recipe page with servings scaling (quantities recalculate, fractions shown as ½ ¼ ⅓)
- Confirmed / AI-estimated / missing markers on ingredients and steps (data model + UI, ready for Phase 4)
- Favourites, custom collections, search across titles, tags and ingredients, recently opened
- Shopping list: manual items ("2 kg potatoes" is parsed), add a recipe's ingredients at the chosen servings, duplicates merged, grouped by aisle, tick-off, clear
- Share a link from YouTube, TikTok, Instagram or a browser into CookLink AI → opens Import with the link; save it as a draft recipe
- Light / dark / system theme
- AI provider choice (Gemini or Claude) and API keys stored encrypted with the Android Keystore

Import methods for text, photos, video and combined sources are visible but marked with the phase they arrive in; nothing is faked.

## Building

### On GitHub (no computer needed)
Every push to `main` runs `.github/workflows/build-apk.yml`, which builds a debug APK and attaches it to the **latest** pre-release on the repo's Releases page. Open that page on your phone, download `CookLinkAI-debug.apk` and allow your browser to install apps.

### Locally
Requirements: JDK 17, Android SDK 35, Gradle 8.11+ (or open the folder in Android Studio Ladybug or newer).

```
gradle :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## API keys

Profile → paste a key for Google Gemini (https://aistudio.google.com/apikey) and/or Anthropic Claude (https://console.anthropic.com/settings/keys). Keys are encrypted with an AES-256-GCM key held in the Android Keystore, and app backup is disabled. Because the key lives on the phone, this setup is meant for personal use; a shared/public release should move calls behind a small server.

## Project layout

```
app/src/main/java/com/cooklink/ai/
  model/          Recipe, Ingredient, Step, Certainty, formatting helpers
  data/db/        Room entities, DAOs, database
  data/           RecipeRepository, ShoppingRepository, ImageStore
  data/settings/  DataStore settings, SecureKeyStore (Keystore-encrypted keys)
  ui/             Navigation root, theme, shared components, screens
```

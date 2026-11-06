# CookLink AI

Android app that turns cooking videos, links, photos, screenshots and notes into structured recipes with AI, then helps you cook them.
Kotlin + Jetpack Compose (Material 3), Room, DataStore, OkHttp. Works with **Google Gemini** or **Anthropic Claude**, switchable in the app.

**Download:** [CookLinkAI.apk](https://github.com/lunaxoxone2025-beep/cooklink-ai/releases/download/latest/CookLinkAI.apk) (rebuilt on every push to `main`).

## Status: all 7 phases done (v1.0.0)

| Phase | Scope | Status |
|---|---|---|
| 1 | UI, navigation, recipe storage | Done |
| 2 | URL and text import (AI) | Done |
| 3 | Image and video analysis, combined input | Done |
| 4 | AI recipe generation, smart editor, compare with original | Done |
| 5 | Interactive cooking mode | Done |
| 6 | Personal AI chef, smart shopping list | Done |
| 7 | Tests, backup/sync, stable signed APK, docs | Done |

## Look

“Neon night” interface: always dark, one electric accent colour (lime, orange, pink or cyan — chosen in Profile), glowing highlights, a floating pill navigation bar and two bundled typefaces: **Unbounded** for titles and **Space Grotesk** for text (both SIL Open Font License).

## Features

**Import (Import tab, or Share → CookLink AI from any app)**
- **Links.** YouTube and Shorts are watched by Gemini (public videos only). Recipe sites are read from their schema.org data and page text. TikTok is read through its official oEmbed caption, and Instagram and Facebook through the post's public preview tags. The app never downloads protected videos; when a platform only exposes the caption, it says so and suggests uploading the video or screenshots instead.
- **Text.** Paste notes, a caption or a transcript.
- **Photos & screenshots.** Up to 10 per recipe, downscaled on the phone before sending.
- **Phone videos.** Up to 500 MB or 30 min. Gemini receives the whole video through the resumable Files API, with an upload progress bar, and watches the frames, audio, on-screen text and subtitles. The uploaded file is deleted right afterwards. Claude receives 16 evenly spaced frames instead, and the app says that audio was not used.
- **Combined.** Photos, video, notes and a link in one request. The AI cross-references them and prefers amounts that are written or spoken.
- Every ingredient and step is marked **confirmed**, **AI estimate** or **missing**. Missing amounts are never filled in with numbers.
- Recipes get a title, a cover photo (thumbnail, page image, first photo or video frame), description, prep/cook/total time, difficulty, servings, ingredients with amounts and units, equipment, steps with times and temperatures, estimated nutrition, source links and tags.
- Imports keep running while you use the rest of the app, and the recipe opens when it's ready. Error messages are clear: bad key, quota, private video, no caption, file too large, and so on.

**Library & editor**
- Favourites, custom collections, search across titles, tags and ingredients, recently opened. Works offline.
- Full editor with AI help: rewrite a step (✨), replace an ingredient (⇄; affected steps are updated too), and scale all amounts ½× or 2×. Editing an AI estimate marks it as confirmed.
- **Compare with original** shows a line-by-line diff between the AI's import and your edited version, with a "restore original" option.
- Servings scaler on the recipe page, with nice fractions (½, ¼, ⅓).

**Cooking mode**
- Full screen, one step per page, large text, swipe or big Back/Next buttons, and a progress bar.
- Ingredient checklist (first page, plus a sheet available at any time).
- Read-aloud using the phone's text-to-speech.
- A timer for every step that has a duration. Timers keep running across steps, and an alarm with a dialog sounds when one ends.
- The screen stays on and the system bars are hidden.

**AI chef**
- Chat from Home (general questions) or from a recipe, where it knows that recipe. Suggestions include "make it cheaper", "adapt for 4 people", "vegetarian version" and "what can I cook with…".
- Any longer answer can be saved as a new recipe.

**Shopping list**
- Add one recipe from its page, or plan several recipes with servings each.
- Ingredients from different recipes are combined across compatible units (g/kg, ml/l, tsp/tbsp/cup, oz/lb), grouped by aisle, ticked off, and you can add items manually ("2 kg potatoes" is parsed).

**Profile**
- Neon accent colour: lime, orange, pink or cyan.
- AI provider (Gemini or Claude) and API keys.
- Recipe language: same as source, Spanish, English, Italian, Slovenian, German or French.
- **Backup & sync:** export the whole cookbook, with photos, to a single JSON file (keep it in Google Drive, send it to another phone) and restore it anywhere. Duplicates are skipped and API keys are never included.

## Setup: API keys ("backend configuration")

The app calls the AI providers directly from the phone; there is no server to run.

1. Get a key:
   - Gemini: https://aistudio.google.com/apikey. The free tier works; recommended because it can watch videos and listen to audio.
   - Claude: https://console.anthropic.com/settings/keys. Needs API credits.
2. Open **Profile**, paste the key and tap **Save**. Then choose the provider.

Keys are encrypted with an AES-256-GCM key held in the Android Keystore, app backup is disabled, and keys only ever go to the provider's own API. Models used: `gemini-3.8-flash` and `claude-sonnet-5-5` (constants in `data/ai/AiClients.kt`).

> For a public release with many users, move the AI calls behind your own server so keys are not on users' phones. The `AiClient` interface is the single place to swap in a server-backed implementation.

## Building

### On GitHub (no computer needed)
Every push to `main` runs `.github/workflows/build-apk.yml`. It runs the unit tests, builds a release APK and attaches it as `CookLinkAI.apk` to the **latest** pre-release.

### Locally
Requirements: JDK 17, Android SDK 35, Gradle 8.11+ (or Android Studio Ladybug or newer).
First download the two fonts used by the neon interface (the CI does this automatically):

```
mkdir -p app/src/main/res/font
curl -L -o app/src/main/res/font/unbounded.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/unbounded/Unbounded%5Bwght%5D.ttf"
curl -L -o app/src/main/res/font/space_grotesk.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/spacegrotesk/SpaceGrotesk%5Bwght%5D.ttf"
```

```
gradle :app:testDebugUnitTest      # unit tests
gradle :app:assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

### Signing
APKs are signed with the committed `app/debug.keystore` (standard public debug credentials), so every build has the same signature and updates install over each other. To publish on Google Play, create a private keystore, store it as CI secrets and point `signingConfigs` in `app/build.gradle.kts` at it.

## Limits

| Thing | Limit |
|---|---|
| Images per recipe | 10 (sent at max 1568 px) |
| Phone video | 500 MB, 30 minutes |
| YouTube | Public videos only (Gemini); Claude uses title and description only |
| TikTok / Instagram / Facebook | Caption only (platform restriction); use video upload or screenshots for the video itself |
| Page text sent to AI | 30 000 characters |

## Project layout

```
app/src/main/java/com/cooklink/ai/
  model/            Recipe, Ingredient, Step, Certainty, RecipeSnapshot, formatting
  data/db/          Room entities, DAOs, database + migrations
  data/ai/          Gemini & Claude clients, extraction prompt/parser, RecipeAssistant (edits + chat)
  data/importer/    LinkFetcher, MediaPrep (images/frames/video), RecipeImporter, ImportController
  data/             Repositories, ImageStore, BackupManager
  data/settings/    DataStore settings, SecureKeyStore (Keystore-encrypted keys)
  ui/               Navigation root, theme, components, screens
app/src/test/       Unit tests (formatting, parsing, extraction honesty rules, unit merging)
```

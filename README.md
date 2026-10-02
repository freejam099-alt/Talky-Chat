# A2UI Chat Thread — Android / Kotlin / Jetpack Compose

Native Android reimplementation of the UIArc `ChatThread` interaction model, driven by an A2UI-style JSONL surface.

Source reference:
- UIArc ChatThread: https://uiarc.dev/components/chat-thread
- UIArc source: https://github.com/kuratlielia/arc-library/blob/89230849054711509c025410e392f631850c0969/registry/components/chat-thread/chat-thread.tsx
- A2UI v1.0 protocol: https://a2ui.org/reference/messages/

## What is implemented

- A2UI v1.0 envelope handling for `createSurface`, `updateComponents`, `updateDataModel`, and `deleteSurface`.
- Local trusted `ChatThread` catalog entry. The payload controls data; native Kotlin owns the rendering implementation.
- Grouped messages by author/day/time window.
- Day separators (`Today`, `Yesterday`, weekday/date labels).
- Incoming/outgoing chat bubbles with the same grouped-corner rhythm as the web component.
- Reactions and reaction picker with touch-friendly Android interaction.
- Read receipts with participant avatars.
- Native `BotAvatar` profile photos for participants without a URL, following the Libraries.dev `bot-avatars` shape/state/shading model.
- Deterministic per-user avatar roster (`type`, `face`, `state`, `seed`) so newly logged-in users do not receive identical profile art.
- Sending states and failed-message retry callback.
- Typing indicator.
- Composer with six-line maximum, attachments, image previews, send-button animation, and local optimistic sends.
- Scroll-to-latest pill when new messages arrive out of view.
- Light/dark theme adaptation.
- Reduced-motion-friendly transitions through Compose animation primitives.

## Architecture

`A2UIRenderer.kt` parses JSONL into `SurfaceState` and flat `A2UIComponent` definitions.

`MainActivity.kt` loads `assets/chat_thread.a2ui.jsonl` and mounts the `root` component.

`ChatThread.kt` is the native Jetpack Compose renderer and interaction layer. `BotAvatar.kt` is the Canvas-based Android renderer for the Libraries.dev bot-avatar visual model.

The implementation is intentionally not a literal TSX-to-Kotlin translation. Browser-only details such as CSS hover behavior, DOM `ResizeObserver`, HTML drag/drop, and browser tab links are replaced with Android-native equivalents while preserving the visual/interaction intent.

## Build

Open the `A2UIChatThreadAndroid` folder in Android Studio with a recent Android Gradle Plugin 8.13.x-compatible environment.

The project uses:
- compileSdk 36
- minSdk 24
- targetSdk 36
- Kotlin 2.3.21
- Jetpack Compose BOM 2026.06.00
- Android Gradle Plugin 8.13.2
- JDK 21 toolchain

This workspace did not contain an Android SDK or a usable Gradle installation, so the APK itself could not be compiled here. The source tree and Gradle configuration are included for Android Studio sync/build.

## A2UI payload

Edit `app/src/main/assets/chat_thread.a2ui.jsonl` to drive the demo. The root component uses A2UI data paths such as:

```json
{
  "version": "v1.0",
  "createSurface": {
    "surfaceId": "chat-thread-demo",
    "catalogId": "local://catalog/chat-thread",
    "components": [
      {
        "id": "root",
        "component": "ChatThread",
        "messages": { "path": "/messages" }
      }
    ],
    "dataModel": {
      "messages": []
    }
  }
}
```

The app is a renderer shell: an upstream agent can produce these JSONL messages, while this APK remains the trusted catalog/renderer boundary.

## Bot avatars

The original `bot-avatars` package targets React, so Android does not install the npm package directly. The project ports its visual contract to native Compose Canvas: 100×100 normalized body geometry, per-type silhouettes/colors, custom SVG body paths, face modes, deterministic seeds, default/working/sleeping motion, shading presets, brightness/saturation controls, and tap-to-hop interaction. Libraries.dev documents the package as a React 18+ library with zero runtime dependencies.

For participants without `avatar`, `Avatar()` automatically selects a deterministic bot shape. The sample A2UI payload assigns `droid`, `clover`, and `flower` to the demo users, with Sam in `working` state to match the typing indicator.

Python precision pass used for the sample profile sizing: 36dp corresponds to 36/54/72/108/144 px at mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi respectively; 28dp corresponds to 28/42/56/84/112 px.

## Bencho VoiceNote → Jetpack Compose

`VoiceNote.kt` is a native Kotlin + Jetpack Compose conversion of the supplied Bencho Voice note component. It preserves the important interaction model and the source comments: hold to enter recording, synthetic speech-shaped levels, slide-left cancellation, clip playback, playhead progress, and scrubbing.

This is intentionally a **concept, not a recorder**, matching the supplied source: it does not request microphone permission and does not capture audio. `lucide-react` is not installed because this target is Android/Kotlin and the project already standardizes on Hugeicons. The X/play/pause/mic glyphs are Hugeicons vector drawables.

### Bencho token mapping

The supplied CSS tokens are not added as global CSS. In Compose they are mapped locally from the project's existing Material 3 theme:

- `--card` → `MaterialTheme.colorScheme.surface`
- `--fill-on` / `--ink` → `MaterialTheme.colorScheme.onSurface`
- `--fill-slab` → `MaterialTheme.colorScheme.surfaceVariant`
- `--font-ui` → `MaterialTheme.typography.bodyLarge.fontFamily`
- `--ink-3` → `onSurface` at 60% opacity
- `--pane-edge` → `MaterialTheme.colorScheme.outlineVariant`
- `--signal` → `MaterialTheme.colorScheme.primary`

### A2UI

The local catalog accepts `voiceNote: boolean` on `ChatThread`. The demo JSONL sets `voiceNote` to `true`, and the renderer places the VoiceNote component below the composer whenever the text field is empty and there are no pending attachments.

### Precision checks

`tools/voice_note_precision.py` and `tools/voice_note_precision.mjs` independently calculate the port's logical dimensions, sample cadence, cancellation travel, bar geometry, gain mapping, inner radius, and Android density conversion. Their current outputs match on all checked values.

Source attribution: Bencho Voice note component supplied in the project request; license reference: https://bencho.dev/licence.
## ReorderList

`ReorderList` is the native Jetpack Compose/A2UI conversion of Bencho's liquid reorder interaction.

Source-level mapping:
- `framer-motion` spring → Compose `spring(stiffness = 220f, dampingRatio = 0.67f)`; `mass = 0.5` is retained in the precision calculation and the resulting damping ratio matches the source at ~0.6674.
- SVG `#arr-goo` → Android 31+ `RenderEffect` blur on an opaque blob layer, with an unfiltered ink layer above it. Android 24–30 use the crisp fallback because the DOM SVG filter has no direct safe equivalent across those API levels.
- `AVATARS` stub → this project's existing native `BotAvatar` renderer; no Bencho photographs are bundled or fetched.
- Bencho CSS tokens are mapped locally to `MaterialTheme.colorScheme` and `MaterialTheme.typography`, so no new global theme variables leak into the app.

A2UI support was added to `catalog.json`. A standalone example surface is in `app/src/main/assets/reorder_list.a2ui.jsonl`.

### Reference dependency
The original React component specifies `npm i framer-motion`; the Android port does not add an npm runtime because the production component is Kotlin/Compose. The web dependency is documented here only as a source/reference requirement.

### Precision checks
Run both scripts and compare their JSON output:

```bash
python3 tools/reorder_list_precision.py
node tools/reorder_list_precision.mjs
```

The current cross-check passes exactly for the shared geometry and spring calculations.



## IconBar — Bencho port

`components/IconBar.kt` is the native Jetpack Compose/A2UI port of Bencho's `IconBar` floating navigation component.

- Horizontal row is the default; vertical column is supported.
- The two-phase active indicator keeps Bencho's `dilate`, `bounce`, and `speed` controls.
- Bencho's CSS design tokens are mapped locally to the project's Material surface/onSurface/outline palette; no new app-wide CSS globals are required.
- The glass treatment is rendered natively with a translucent surface, directional rim, and soft shadow. Android does not expose CSS `backdrop-filter`, so the port preserves the visual hierarchy rather than importing a web runtime.
- Icons use local Hugeicons vector assets: `home-01`, `search-01`, `folder-01`, `bookmark-01`, and `user-02`.
- The ChatThread A2UI surface mounts the IconBar as `floating-nav`, positioned above the composer.
- Calculation parity is checked by `tools/icon_bar_precision.py` and `tools/icon_bar_precision.mjs`.

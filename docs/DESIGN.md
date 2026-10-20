# Music Magic — Apple-inspired Android visual system

Music Magic is an **Android app**, not an iOS clone. Its custom Compose UI borrows
interaction and hierarchy principles from Apple Music and iOS while respecting Android
accessibility, navigation, back gestures, MediaSession and SAF file permissions.

## Design language

| Layer | Role | Implementation |
| --- | --- | --- |
| Canvas | Quiet background | `#F2F2F7` light / `#09090C` dark |
| Content | iOS-style grouped settings | White / `#1C1C1E` neutral section cards, 24dp corners |
| Controls | Lightweight glass-like surfaces | Bordered translucent circular menu controls, 46dp touch target |
| Actions | Quiet by default | Soft blue secondary buttons; vivid blue only for the save action |
| Type | Readable, large-title hierarchy | System sans-serif, 42sp headline, compact uppercase section labels |
| Now Playing | Artwork-first immersive layer | Always-dark player, per-album accent gradient, translucent control sheet |
| Navigation | Clear and predictable | Left drawer for editor / player; retaining Android back behavior |
| Motion | Purposeful transitions | Existing lazy spring carousel and 160–240ms crossfade |

### Album color and motion

1. The selected/playing audio file's *embedded* cover is read by the existing
   bounded decoder/caching pipeline, not a remote search.
2. A small pixel sample selects a representative saturated color without expensive
   live image processing.
3. The fullscreen Now Playing backdrop crossfades toward that accent.
4. The existing `LiquidCardCarousel` retains page snapping, parallax and
   low-memory / reduced-animation safeguards.

Avoid using translucent material for every content row; functional navigation and
transport controls get the glass treatment, while text editing remains simple.
This matches the separation of controls and content in Apple's design guidance.

### Immersive liquid portal (player-only)

The carousel is now deliberately more dramatic than the calm editor:
- **Continuous vortex:** five rotating spiral ribbons (soft wide-body glow and a
  thin luminous edge) and a breathing center are drawn with a Compose Canvas.
  This is live rendering, not a GIF, video, stock image or looping screenshot.
- **Gesture-driven card depth:** during horizontal drags and spring settling,
  album cards shrink toward a vanishing point, tilt in two axes, move vertically
  and grow back to full size. Artwork parallax and motion blur get stronger
  during the transition. The ordinary editor carousel keeps gentler motion.
- **Real lens deformation:** Android 13+ uses a tiny AGSL RuntimeShader to
  twist the album-cover sampling coordinates as cards cross the screen. Android
  10–12 falls back to spring transforms, parallax, and its normal artwork.
- **Cover palette:** the existing bounded art extractor drives the vortex's
  color with an animated crossfade when the currently playing song changes.
- **Playback behavior:** playing makes the vortex brighter and stronger; pausing
  eases it down. This is a time-based ambient effect, not microphone capture,
  DSP analysis, or frequency/beat synchronization.

No new permissions or networking are required. The system's reduced-motion
setting and Android's low-RAM flag disable the animated vortex; the cards keep
their navigation functions. Shader distortion is only used on Android 13+.

### Music Magic 1.2 interaction suite

- **Liquid Morph Bridge** connects departing and arriving album cards using
  a finger-position-driven Bézier fluid neck, changing cover colors, soft
  highlights and breaking droplets, drawn above the pager with no touch blocking.
  Playback-only; editor cards remain calm.
- **Mini Player expansion** uses a bottom-origin spring transition, and upward
  swiping the mini player stretches and opens full-screen Now Playing.
- **Cover editor** offers a square crop, pinch-like zoom slider, 90-degree
  rotation, removal, plus selectable real PNG or JPEG output. The encoded
  image's MIME is written correctly in FLAC/MP3/WAV/APIC tags, including
  transparent PNG. The crop preview is bounded to avoid huge bitmaps.
- **24/32-bit high-resolution** integer PCM WAV preserves original PCM during
  tagging; optional fallback FLAC attempts these depths only if the Android
  device's native FLAC encoder accepts them. Output FLAC STREAMINFO depth is
  checked before saving; unsupported hardware fails safely rather than
  dropping to 16-bit. Floating-point WAV and >2-channel fallback conversion
  remain unsupported.
- **Batch editing** selects subsets of scanned files and can write a shared
  cover with common artist or album metadata. Processing is sequential,
  reporting the number of successes/errors and retaining original backups.
- **Edit history** stores up to eight records with before/after metadata and
  private local backups for files <=40 MiB, capped at 120 MiB total. Larger
  files keep only an audit summary; restoring requires an actual backup and
  a still-valid SAF document URI. Clear history requires confirmation.
- **Playlist sorting** persists the order mode: manual insertion order,
  title, artist, album, recent file modification time and favorites first.
  Dynamic artist and folder groups use scan metadata, not duplicated audio.

Important: these are native Android components inspired by the reference,
not copied copyrighted site content or a claim of pixel-perfect parity.
Native-device frame pacing and third-party WAV artwork compatibility still
need validation before a production release.

### Accessibility & performance

- Icons for navigation/playback have Korean content descriptions.
- Player text is predominantly high-contrast white; editor adapts to device dark mode.
- Icon touch targets are 46–48dp or larger.
- Artwork is decoded off the UI thread and only for nearby carousel pages.
- Continuous full-screen blur and proprietary Apple assets are intentionally avoided.
- Test normal/dark themes, large-font devices, reduce-motion settings and Android 10+.

## Build and release

This design is on `feat/fluid-music-player` / PR #1. The currently linked
`v1.0.0-liquid-ui` APK does **not** contain this redesign. Run
`gradle :app:assembleDebug :app:assembleDebugAndroidTest` and device checks before
publishing a new release; do not imply that an unverified build is ready to install.

Inspiration:
- https://developer.apple.com/design/human-interface-guidelines/materials
- https://www.inspora.design/posts/fluid-liquid-card-carousel

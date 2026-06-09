# Glyph Pomodoro

A Pomodoro timer that lives on the **Glyph Matrix** of the Nothing **Phone (4a) Pro** —
controlled entirely by shaking the phone, no touch needed.

<!-- 📸 Add a photo of the toy running on the matrix here -->
<p align="center">
  <img src="docs/demo.jpg" alt="Glyph Pomodoro running on a Phone (4a) Pro" width="320">
</p>

## What it does

- Runs as an **Always-on Glyph Toy** (Flip to Glyph). Lay the phone face-down and the
  matrix shows the timer; flip it up and the matrix blanks while the timer keeps running.
- **Work** blocks show the **minutes remaining** in a custom pixel font; **breaks** show the
  number plus a lit outer ring. Classic **25 / 5 / 15** (long break every 4th block).
- **Shake to start**, shake again to **pause/resume**, and a **long, hard shake to reset** —
  each with a play / pause / reset icon (or animation) flashed on the matrix.
- An ongoing **notification** with a live countdown and Pause/Resume + Reset buttons.
- Exact phase-end **buzz** via `AlarmManager` (fires even if the phone is face-up).

## Make it yours (in-app)

- **Digit font** — draw each digit 0–9 on a grid (drag to paint); auto-cropped per width.
- **Icons** — draw the play / pause / reset icons; 1 frame = static, 2+ = an animation with
  per-frame timing and first/last-frame holds (onion-skin preview while editing).
- **Brightness** — separate running / paused levels (LEDs are dark below ~65, so that's the floor).
- **Shake** — tune the start/pause strength, plus the reset strength and hold time, with a
  live shake-strength meter to calibrate against.

## Build & install

Requires JDK 17+ and the Android SDK (platform 35). The Glyph SDK (`glyph-matrix-sdk-2.0.aar`)
is bundled in `app/libs/`.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or trigger the **Build APK** GitHub Action (manual) to produce a draft release with the APK.

### Enable on the phone

1. Install the APK and grant the mic/notification prompts.
2. **Settings → Glyph Interface → Flip to Glyph → Always-on Glyph Toy** → select **Pomodoro**.
3. Lay the phone face-down → shake → the timer starts.

## Project layout

```
app/                     Android app (Kotlin + Compose)
  libs/                  bundled Glyph Matrix SDK (.aar)
  src/main/java/.../      toy service, shake detector, renderer, editors, notification
docs/                    Glyph SDK reference + license (from the official dev kit)
PLAN.md                  design notes
.github/workflows/       CI to build the APK + draft release
```

The official Glyph Matrix SDK documentation is preserved in
[`docs/glyph-sdk-reference.md`](docs/glyph-sdk-reference.md).

---

*Built for the Nothing Phone (4a) Pro · `Glyph.DEVICE_25111p` · 13×13 matrix.*

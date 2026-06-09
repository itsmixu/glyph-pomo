# Pomodoro Glyph Toy — Build Plan

A Pomodoro timer that lives on the **Glyph Matrix** of a **Nothing Phone (4a) Pro**,
controlled without touching the screen.

> Status: planning. No code written yet. This document is the agreed design.

---

## 1. Target & concept

- **Device:** Phone (4a) Pro only — `Glyph.DEVICE_25111p`, **13×13** matrix, monochrome
  (per-LED brightness 0–255), **AOD-only** toys, **no Glyph Touch**.
- **Mode:** Always-On Display via **Flip to Glyph** (phone face-down).
  Requires `com.nothing.glyph.toy.aod_support = 1`.
- **Concept:** A glanceable Pomodoro. The matrix shows a depleting ring for the current
  block. You control it without flipping the phone over — primarily by **shaking** it.
- **Cycle:** Classic **25 / 5 / 15** — 25 min work, 5 min short break, and a 15 min long
  break after every 4th completed work block.

---

## 2. Rendering model (corrected)

There is **no 1-minute frame cap.** While the toy service is bound/active we can call
`setMatrixFrame` as fast as we like — this is the same realtime path the community games
use. `EVENT_AOD` is a **wakeup heartbeat** (~once/min) for passive toys, not a frame-rate
limit.

**Design:**
- Drive the display from **our own render loop** (~1 fps for the depleting ring; faster
  during the start/arming animations).
- Use `EVENT_AOD` only as a **fallback heartbeat** in case the system parks our loop in
  deep low-power.
- **Always compute `remaining = endTime − now()`** from a stored end-timestamp. The frame
  is therefore correct at *any* repaint rate — 10 fps or 1/min — and survives the service
  being rebound, throttled, or a tick being skipped. We never count ticks.

**Verify on device:** whether the 4a Pro suspends our render loop in deep AOD (screen
fully off, phone idle). If it does, the heartbeat + compute-from-endtime keeps it correct
at coarse cadence; if it doesn't, we get smooth realtime updates. Either way: correct.

---

## 3. State machine

```
        select / flip-to-glyph
   ┌──────────────────────────────► ARMED (blank display, 10s window, sensors listening)
   │                                   │ shake within 10s        │ 10s elapsed, no shake
   │                                   ▼                          ▼
   │                              RUNNING_WORK ◄──┐            DORMANT (blank, release
   │                                   │          │             listeners, idle; stop our
   │                shake / vol / tap  │ toggle   │             foreground svc if owned)
   │                                   ▼          │
   │                                PAUSED ───────┘
   │                                   ▲
   │           phase-end alarm (buzz)  │
   │                                   ▼
   │              RUNNING_BREAK (short, or long every 4th) ──► back to RUNNING_WORK
   │
   └── system unbind (user cycles away / AOD ends) ──► release all, turnOff(), unInit()
```

**Key behaviors:**
- On activation the toy starts **DORMANT/blank** — no ring shown.
- **First shake** arms→starts the first work block and the ring appears.
- **10-second arming window:** if no shake arrives, go **DORMANT** (release sensor/mic
  listeners, cancel alarms, blank the matrix; if we own a foreground service, `stopSelf`).
  > Note: the toy Service is *bound by the Glyph system*, so we can't guarantee a hard
  > self-kill of that component — the system tears it down when the user cycles away. What
  > we *can* do on timeout is release resources, blank the display, and stop any
  > foreground service we started ourselves.
- While **RUNNING**: shake (and the experimental inputs) toggle **pause/resume**.
- Phase end is signalled by an **on-time buzz** (see §6), then auto-advances to the next
  block.

---

## 4. Architecture / components

| Component | Type | Responsibility |
|-----------|------|----------------|
| `PomodoroToyService` | Glyph toy `Service` (system-bound) | Lifecycle (`init`/`register(DEVICE_25111p)`/`unInit`), receive `EVENT_*` via `Messenger`, host the render loop, draw the ring. |
| `TimerEngineService` | our **foreground** `Service` | Owns the authoritative clock (endTime/phase/state, persisted), schedules the `AlarmManager` alert, and holds the always-on **sensor + mic** listeners (foreground privileges needed for mic + reliable sensors in AOD). |
| `KeyControlService` | `AccessibilityService` | **Experimental** — captures **volume-key** events globally (the only viable path; user must enable it). |
| `MainActivity` | companion `Activity` (**core**) | Settings + **calibration hub**: tune mic/shake thresholds with live readouts, toggle each input, manual start/pause/reset, and the “Activate toy” intent into the Glyph Toys manager. |
| `SettingsStore` | persisted **DataStore** | Live config the services observe as a `Flow`: mic threshold, shake threshold, per-input enable toggles, durations. UI writes, services read — changes apply without restarting the toy. |
| `PomodoroState` | persisted model | Runtime state: `endTimeMillis`, `phase`, `runningState`, `completedWorkBlocks`. Survives rebind. |

---

## 5. Control inputs

All three requested inputs are in scope (personal use — privacy/battery cost accepted).

| Input | Status | Mechanism | Notes / risk |
|-------|--------|-----------|--------------|
| **Shake** | **Core** | `SensorManager` linear-acceleration listener in the foreground service; magnitude-threshold + debounce. | SDK explicitly endorses accelerometer/gyroscope. Maps to **start** (in ARMED) and **pause/resume** (in RUNNING). Most likely to work in AOD. |
| **Companion app** | **Core** | Buttons in `MainActivity`. | Reliable fallback control + the activation entry point. |
| **Volume keys** | **Experimental** | `AccessibilityService` with key-event filtering. | Background services can't capture volume keys otherwise. May not fire while screen is in AOD low-power — **verify on device**. Heavy permission (accepted). |
| **Mic back-tap** | **Experimental** | Foreground service, `foregroundServiceType="microphone"`, `RECORD_AUDIO`; detect knock transient in the audio stream. | Always-on mic = battery cost; knock-vs-noise detection is false-positive prone. Tune threshold on device. |

Single shake / volume press / tap all map to the same **toggle** action (start when armed,
pause/resume when running). We can later assign distinct gestures (e.g. tap = skip phase)
once we see what's reliable on hardware.

---

## 6. Timekeeping & alerts

- **Source of truth:** `endTimeMillis` for the current block + `phase` + `runningState`,
  persisted so it survives the service being rebound.
- **Pause:** store `remainingAtPause` and clear `endTime`; on resume set
  `endTime = now + remainingAtPause`.
- **On-time alert (decoupled from the visual):** schedule the exact phase-end with
  `AlarmManager` (`setExactAndAllowWhileIdle`) → **vibration + notification + brightness
  flourish** on the matrix. This fires on time even if the ring's visual refresh is coarse,
  and even if the toy was unbound when you flipped the phone back up.
- On alarm: advance phase (work→break / break→work, long break every 4th), recompute
  `endTime`, reschedule.

---

## 7. Display design (13×13)

- **Depleting perimeter ring** = fraction of the current block remaining. Build via
  `GlyphMatrixUtils.generateMatrixProgress(...)` → `int[]` → `frame.addTop(int[])`, or draw
  to a 13×13 `Bitmap` → `GlyphMatrixObject`. (Confirm exact ring helper params + circular
  masking on device.)
- **Center glyph** = phase indicator: solid dot = work, hollow/dimmer = break. A glance
  tells you which block and how far in.
- **Arming (10s):** blank per spec. *Option to evaluate:* a faint breathing dot to signal
  “listening for shake” — default blank unless on-device UX says otherwise.
- **Start & phase-change flourishes:** brief brightness pulse so transitions read at a
  glance.
- Always size to `Common.getDeviceMatrixLength()` (= 13), never hard-code 25.

---

## 8. Permissions & manifest

- `com.nothing.ketchum.permission.ENABLE` — Glyph SDK.
- `VIBRATE` — phase-end buzz.
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — precise alerts.
- `RECORD_AUDIO` + `foregroundServiceType="microphone"` — mic back-tap (experimental).
- `FOREGROUND_SERVICE` (+ specific types) — the engine service.
- `BIND_ACCESSIBILITY_SERVICE` — volume-key capture (experimental).
- `<service>` registration for the toy with: `toy.name`, `toy.image` (preview drawable),
  `aod_support = 1`, and the `com.nothing.glyph.TOY` intent-filter.

---

## 9. Build & deploy steps

1. Scaffold an Android project in this folder (Kotlin; minSdk 33).
2. Add `glyph-matrix-sdk-2.0.aar` to `app/libs/` and wire it as a `flatDir` dependency.
3. Manifest: permissions + the toy `<service>` (`aod_support=1`) + preview drawable.
4. Implement `PomodoroState` (persisted) + `TimerEngineService` (clock + AlarmManager).
5. Implement `PomodoroToyService` (lifecycle, render loop, ring + center glyph).
6. Implement control inputs: shake (core), then volume-key `AccessibilityService` and mic
   back-tap (experimental).
7. Implement the ARMED 10s window + blank-until-first-shake flow.
8. `SettingsStore` (DataStore) + `MainActivity` calibration hub: threshold sliders with live mic/shake readouts, per-input toggles, manual controls, and the “Activate toy” intent.
9. Build → sideload to the Phone (4a) Pro → enable under **Settings → Glyph Interface →
   Flip to Glyph → Always-on Glyph Toy** → verify.

---

## 10. On-device verification checklist (the real unknowns)

- [ ] Does our render loop run in realtime while the AOD toy is active, or is it parked
      between `EVENT_AOD` heartbeats?
- [ ] Does the accelerometer deliver events (un-throttled) while flipped/AOD?
- [ ] Does the `AccessibilityService` receive **volume-key** events with the screen in AOD?
- [ ] Is background **mic** capture allowed via the foreground mic service in this mode, and
      can we reliably distinguish a back-tap from ambient noise?
- [ ] Confirm the 13×13 `int[]` layout / circular masking and the `generateMatrixProgress`
      parameters.
- [ ] Does `EVENT_CHANGE` (long-press) fire at all on the 4a Pro in AOD? (bonus toggle)

---

## 11. Open questions / future

- Distinct gestures for distinct actions (e.g. tap = skip block) once we know what's
  reliable.
- Configurable durations in the companion app (v1 is fixed 25/5/15).
- Session/stats (blocks completed today) — possible later center-glyph or app view.

---

## Reference

- SDK: `glyph-matrix-sdk-2.0.aar` (package `com.nothing.ketchum`), README.md in this repo.
- Example project: https://github.com/KenFeng04/GlyphMatrix-Example-Project

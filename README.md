# Fit Check — AI-Personal Wardrobe OS

Native Android application written entirely in **Kotlin + Jetpack Compose**.
Gemma runs locally on the device via **Google AI Edge LiteRT-LM**.
There is no backend, no cloud AI, and no analytics — your wardrobe stays on your phone.

> **Phase 1 status:** Project scaffold + local Gemma runtime wired in.
> No wardrobe features yet — those ship in subsequent milestones.

---

## Architecture

```
ui/                   Compose screens, theme, navigation
  screens/ailab/      AI Lab dev screen (model status, init, prompt, response)

ai/                   AI abstraction (AiRuntime interface)
  AiRuntime           public contract used by the rest of the app
  LiteRtLmRuntime     production implementation (Google AI Edge LiteRT-LM)
  AiRuntimeProvider   singleton accessor

data/                 (Phase 2: Room + DataStore)
domain/               (Phase 2: use cases)
```

The rest of the application only ever talks to `AiRuntime`. Swapping in a
different runtime, a fallback stub, or a test double is a one-line change.

---

## Tech stack

| Concern              | Choice                                |
| -------------------- | ------------------------------------- |
| Language             | Kotlin 2.1.20                         |
| Build                | Android Gradle Plugin 8.9.2          |
| Gradle               | 8.14.3                                |
| UI                   | Jetpack Compose (BOM 2025.02.00)      |
| Architecture         | MVVM with StateFlow                   |
| Async                | kotlinx.coroutines 1.9.0              |
| Local AI             | `com.google.ai.edge.litertlm:0.14.0`  |
| minSdk / targetSdk   | 26 / 35                                |

---

## Prerequisites

- Android Studio Ladybug (2024.2) or newer
- JDK 17 (bundled with Android Studio works)
- Android SDK Platform 35, Build-Tools 35.0.0+
- For GPU-accelerated inference: a device with Vulkan / OpenCL drivers
- For NPU-accelerated inference: a device whose SoC is supported by
  LiteRT-LM (Tensor G5, recent Snapdragon, recent Exynos, recent MediaTek)

---

## Running

1. Open the project in Android Studio. Let it sync.
2. Make sure a `.litertlm` model file is present (see below).
3. Select a physical device (recommended) or an emulator with arm64.
4. Run the **`app`** configuration.

The first launch opens the **AI Lab** tab (development surface) — that is
intentional. It shows you the runtime state, the accelerator that was
selected, model size, last inference latency, and a prompt → response stream.
Production UI for wardrobe / stylist / planning lands in later phases.

---

## Providing a Gemma model

LiteRT-LM consumes models in the `.litertlm` format. Files are not committed
to the repo (`.gitignore` blocks `*.litertlm`).

**Option A — pre-converted from HuggingFace**

Download a `.litertlm` build of Gemma 3 from the `litert-community` org:
https://huggingface.co/litert-community

Place it in one of the locations the runtime scans:

```
app/src/main/assets/models/gemma-3-4b-it.litertlm   (bundled into APK)
```

OR push it to the device at runtime:

```
adb push gemma-3-4b-it.litertlm \
  /sdcard/Android/data/com.fitcheck.app/files/models/
```

**Option B — convert your own**

```bash
pip install litert-torch-nightly
litert-torch export_hf \
  --model=google/gemma-3-4b-it \
  --output_dir=./gemma-3-4b-it-litertlm
```

Drop the resulting `model.litertlm` into `assets/models/` (rename to
`gemma-3-4b-it.litertlm`) or into the app's `files/models/` directory on
device.

The runtime prefers `files/models/` first, then `assets/models/`.

---

## AI Lab screen

The AI Lab is the dev surface for verifying local inference end-to-end. It
shows:

- **Engine** — `Not initialized` / `Initializing…` / `Ready` / `Failed`
- **Accelerator** — `CPU` / `GPU` / `NPU` (negotiated by the runtime)
- **Model** — display name and absolute path
- **Size** — human-readable model size
- **Last inference** — wall-clock latency in milliseconds
- **Prompt** — editable text field
- **Response** — streamed tokens, with a thinking indicator while warm

Use it to verify:

- The model file is discovered.
- The accelerator is the one you expect (if you expected GPU, did you get
  it, or did the runtime fall back to CPU because the device lacks the
  required native libs?).
- Inference completes end-to-end without internet.

---

## Roadmap (post Phase 1)

### Phase 2 — Data layer
- Room database (wardrobe items, outfits, wear history, purchases)
- DataStore for preferences and AI memory
- Wire the recommendation UseCases to real data

### Phase 3 — Product surfaces
- Home with "Dress Me Today" compact row → premium bottom sheet
- Wardrobe list / detail / search
- AI Stylist chat
- Should-I-Buy-This scanner

### Phase 4 — Context engine
- Weather, calendar, location
- Wear-history driven reasoning

### Phase 5 — Commerce layer (online-only)
- Product search
- Price tracking
- Budget + spending forecast

### Phase 6 — Platform integration
- Home screen widgets (Today's Fit, Wardrobe Pulse)
- Smart notifications
- Biometric-gated private sections

---

## Privacy

- **No data ever leaves the device** for wardrobe / outfit / purchase data.
- Internet usage is opt-in, only for product search and price lookups.
- LiteRT-LM inference is 100% on-device.
- Android `allowBackup="false"` and explicit data-extraction exclusions are
  configured to keep personal data out of cloud backups.

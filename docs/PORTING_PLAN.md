# Shadock: multi-version porting plan

Goal: ship Shadock for every Minecraft line from 1.16.5 to 26.3, each paired with the Iris line that exists for it, from **one codebase**, with the same features and look as 1.0.0 for 26.3.

Status: **plan only.** Nothing is built yet. Each phase starts only after your go-ahead.

---

## 1. Verified target matrix

Checked against Modrinth's live data on 2026-11-08 (latest Fabric build of each project for each Minecraft version). A few rows **differ from the table you sent**. Those are marked ⚠ and the real version is used.

| Shadock jar | Minecraft | Iris (latest real build) | Iris line | Sodium | Fabric API | Java |
|---|---|---|---|---|---|---|
| `+mc1.16.5` | 1.16.5 | 1.4.5 ⚠ (not 1.6.x) | coderbot 1.4 | 0.2.0 | 0.42.0 | **8** |
| `+mc1.17.1` | 1.17.1 | 1.2.7 ⚠ (named `1.17.x-v1.2.7`) | coderbot 1.2 | 0.3.4 | 0.46.1 | 16 |
| `+mc1.18.2` | 1.18.2 | 1.6.11 | coderbot 1.6 | 0.4.1 | 0.77.0 | 17 |
| `+mc1.19.2` | 1.19.2 | 1.6.11 | coderbot 1.6 | 0.4.4 | 0.77.0 | 17 |
| `+mc1.19.3` | 1.19.3 | 1.5.2 ⚠ (1.5.x, not 1.4.x) | coderbot 1.5 | 0.4.9 | 0.76.1 | 17 |
| `+mc1.19.4` | 1.19.4 | 1.6.11 | coderbot 1.6 | 0.4.10 | 0.87.2 | 17 |
| `+mc1.20.1` | 1.20.1 | 1.7.6 | irisshaders 1.7 | 0.5.13 | 0.92.12 | 17 |
| `+mc1.20.4` | 1.20.4 | 1.7.2 | irisshaders 1.7 | 0.5.8 | 0.97.3 | 17 |
| `+mc1.20.6` | 1.20.6 | 1.7.2 | irisshaders 1.7 | 0.5.11 | 0.100.8 | 21 |
| `+mc1.21.1` | 1.21 – 1.21.1 | 1.8.8 ⚠ (1.8.x, not 1.7.x) | irisshaders 1.8 | 0.6.13 | 0.116.17 | 21 |
| `+mc1.21.4` | 1.21.2 – 1.21.4 | 1.8.0 / 1.8.1 / 1.8.8 | irisshaders 1.8 | 0.6.13 | 0.119.4 | 21 |
| `+mc1.21.5` | 1.21.5 | 1.8.11 ⚠ (1.8.x, not 1.9.x) | irisshaders 1.8 | 0.6.13 | 0.128.2 | 21 |
| `+mc1.21.8` | 1.21.6 – 1.21.8 | 1.9.6 | irisshaders 1.9 | 0.7.3 | 0.136.1 | 21 |
| `+mc1.21.10` | 1.21.9 – 1.21.10 | 1.9.7 ⚠ (1.9.x, not 1.10.x) | irisshaders 1.9 | 0.7.3 | 0.138.4 | 21 |
| `+mc1.21.11` | 1.21.11 | 1.10.8 | irisshaders 1.10 | 0.8.15-beta | 0.141.6 | 21 |
| `+mc26.1` | 26.1 – 26.1.2 | 1.11.4 | irisshaders 1.11 | 0.9.2 | 0.155.3 | 25 |
| `+mc26.2` | 26.2 | 1.11.4 | irisshaders 1.11 | 0.9.2 | 0.161.0 | 25 |
| `+mc26.3` | 26.3 | 1.11.7 | irisshaders 1.11 | 0.9.3 | 0.165.2 | 25 |

That's **18 jars**. Rows that share a range (1.21–1.21.1, 1.21.2–1.21.4, 1.21.6–1.21.8, 1.21.9–1.21.10, 26.1.x) each get **one jar** that declares the whole range. Each needs a quick check on every patch version before release.

---

## 2. What changes between versions

### 2a. Iris: only two adapters needed
I inspected the real Iris jars for every line (1.2.7, 1.4.5, 1.5.2, 1.6.11, 1.7.2/1.7.6, 1.8.8/1.8.11, 1.9.6/1.9.7, 1.10.8, 1.11.x). The calls Shadock relies on (`Iris.reload()`, `getIrisConfig()`, `setShaderPackName`, `setShadersEnabled`, `save()`, `getCurrentPackName`, `isValidShaderpack`, `getShaderpacksDirectory`, `clearShaderPackOptionQueue`) exist in **all** of them, with the same signatures.

| Difference | Affected Iris | Plan |
|---|---|---|
| Package `net.coderbot.iris` → `net.irisshaders.iris` | ≤1.6 vs ≥1.7 | Two adapter classes: `IrisCompatLegacy` (coderbot) and `IrisCompatModern` (irisshaders). The rest of Shadock never sees the difference. |
| `handleException` (error capture) | only ≥1.8 | For Iris 1.2–1.7, show "Iris could not load this shader pack." plus the reason Shadock already detects (invalid, corrupt, missing). The mixin has `require = 0`, so it silently skips older versions. |
| `isFallback()` | missing in 1.2 (1.17.1) | Treat "pack name set, but no pack loaded" as failure instead. |
| `isValidToShowPack()` | missing ≤1.5 | Shadock does its own ZIP/folder check, as the importer already does. |

The runtime check in `IrisCompatFactory` changes from "exactly 1.11.7" to "the Iris line this jar was built for". Each jar still refuses to drive an Iris it wasn't tested with.

### 2b. Minecraft: where the porting work really is

| Area | Breakpoints | Impact |
|---|---|---|
| **Name mappings** | ≤1.21.11 obfuscated, 26.x not | Build every version with **Mojang's official names**, so most code reads the same as 26.3. Loom remaps the obfuscated versions. |
| **Java** | 8 (1.16.5) · 16 (1.17.1) · 17 (1.18–1.20.4) · 21 (1.20.6–1.21.11) · 25 (26.x) | Java 17+ versions are easy. **Java 8 and 16 force rewrites** (records, `switch ->`, `var`, `List.of`, `Files.readString`…). See decision D1. |
| **GUI drawing** | ≤1.19.4 `PoseStack` + `GuiComponent` · 1.20–1.20.1 `GuiGraphics` without sprites · 1.20.2–1.21.5 `blitSprite` (signature changes at 1.21.2) · 1.21.6–1.21.11 render pipelines · 26.x `GuiGraphicsExtractor` | Biggest job. Shadock draws through one small **`DockCanvas`** facade (fill, text, sprite, nine-slice, scissor, translate) with **5 implementations**, one per era. |
| **32x art** | GUI sprite atlas + `nine_slice` only from **1.20.2** | 1.20.1 and older use one generated texture sheet (`gui/dock_sheet.png`, still 32x) and Shadock's own nine-slice code. `make_assets.py` writes both formats. |
| **Input events** | 1.21.9 introduced `KeyEvent`/`MouseButtonEvent` records · 26.3 SDL (left button = 1) | A small **`DockInput`** bridge turns raw events into one internal form. The screen keeps one per-era override layer. |
| **Keybind registration** | `KeyMapping.Category` records from 1.21.9 (string categories before) · Fabric `KeyBindingHelper` vs 26.x `KeyMappingHelper` | Per-era `ShadockKeybinds`. |
| **Text** | `TranslatableComponent` (≤1.18) vs `Component.translatable` (1.19+) | Helper `Texts.tr(key, args)`. |
| **`ResourceLocation` vs `Identifier`** | renamed in recent versions | Helper `Ids.of(path)`. |
| **Screen access** | `minecraft.screen` / `setScreen` vs 26.x `minecraft.gui.screen()` | Helper in the screen bridge. |
| **File picker** | GLFW era: LWJGL TinyFD (bundled) · SDL era: `SDL_ShowOpenFileDialog` | Two `NativeFilePicker` implementations. TinyFD blocks, so it runs off the render thread on Windows/Linux and on the main thread on macOS. |
| **Default key** | vanilla 26.3 uses O · old Iris versions used O for their pack screen | Keep **H** everywhere. The key-clash guard runs for every version that supports game tests. |

**Unchanged on every version:** config, scanner, folder watcher, importer, ZIP validation, order store, controller logic and unit tests. These are plain Java with no Minecraft code, so they're shared as-is (written to Java 17 at first, see D1).

---

## 3. How the codebase changes

- **Stonecutter** (a widely used multi-version tool for Fabric): one source tree with version-specific code marked by comment conditions like `//? if >=1.21.9 {`, and one Gradle "node" per target in the table. Each node has its own `gradle.properties`: MC, Fabric Loader/API, Iris/Sodium versions and Java target.
- **Loom:** the remapping plugin (`fabric-loom`, official Mojang mappings) for 1.16.5–1.21.11, and the current non-remapping plugin for 26.x. Gradle itself keeps running on Java 25, and each node compiles for its own Java level.
- **Layers:**
  - `core/`: shared and version-independent
  - `iris/`: Legacy and Modern adapters
  - `platform/`: `DockCanvas`, `DockInput`, `Texts`, `Ids`, `ScreenBridge`, `FilePicker`, `Keybinds`, one implementation per era
  - `ui/`: dock and settings logic, drawn only through the platform layer
- **Assets:** one generator, two outputs: sprites + `.mcmeta` (1.20.2+) and a texture sheet (≤1.20.1).
- **CI:** a GitHub Actions matrix builds and unit-tests all 18 nodes on every push. A release produces all 18 jars, named `shadock-1.0.x+mc<version>.jar`.

---

## 4. Phases (each one needs your go-ahead)

| Phase | Scope | Result | Effort |
|---|---|---|---|
| **P0: Restructure** | Move to Stonecutter + layers. **Only 26.3** as a node. | 26.3 behaves exactly the same. Unit tests and the in-game test still pass. | Medium |
| **P1: 26.x family** | 26.1.x, 26.2 | 3 jars | Small |
| **P2: Modern 1.21** | 1.21.11, 1.21.9–10, 1.21.6–8 (render pipelines; old/new input split at 1.21.9; GLFW + TinyFD) | 3 jars | Medium |
| **P3: Sprite era** | 1.21.5, 1.21.2–4, 1.21–1.21.1, 1.20.6, 1.20.4 (`blitSprite` + `RenderType`, `PoseStack`) | 5 jars | Medium |
| **P4: GuiGraphics without sprites** | 1.20.1 (sheet texture + own nine-slice) | 1 jar (the most-played old version) | Medium |
| **P5: Legacy Iris** | 1.19.4, 1.19.3, 1.19.2, 1.18.2 (`PoseStack` + `GuiComponent`, coderbot adapter) | 4 jars | Medium-large |
| **P6: Oldest** *(optional, see D1)* | 1.17.1 (Java 16), 1.16.5 (Java 8, Iris 1.4.5) | 2 jars | Large |
| **P7: Release** | Version-specific changelogs, upload all files to CurseForge (and Modrinth if wanted) with correct game versions and dependencies | public | Small-medium |

Order: newest to oldest, because each step back changes a little more than the last, and the most-played versions (1.21.x, 1.20.1) land earliest.

---

## 5. How each version gets tested

- **Every node, automatically:** build, all 29+ unit tests, and a metadata check (Iris range, MC range, Java level).
- **Versions with the Fabric client game-test API** (recent versions; exact cutoff confirmed in P0): the same automated in-game run as 26.3. It opens the dock with the key, applies 3 real packs through Iris and checks each switch, drags a row, opens settings, checks for key clashes, and takes screenshots.
- **Older versions:** `runClient` plus a short scripted check of the same flows. You'd play-test a few key versions (suggested: 1.20.1, 1.19.2, 1.18.2). That's the same split as before: I automate what can be automated, and you check what needs a person.
- **Shader packs:** the 6 packs you used are recent, and some may not support very old Iris. Each node gets a pack known to work on its Iris line.

---

## 6. Risks

| Risk | Mitigation |
|---|---|
| **Java 8 for 1.16.5** means removing records, `switch ->`, `var` and Java 9–16 APIs from the shared core | Option A: keep the core at Java 17 and drop 1.16.5/1.17.1. Option B: compile old nodes with **Jabel** (modern syntax, old bytecode) and swap the few newer library calls. Records still need converting. See D1. |
| Iris 1.2–1.7 can't report the exact Iris error | A clear generic message plus Shadock's own checks. It never claims success. |
| Very old Iris (1.2.7, 1.4.5) may reload differently while in a world | Covered by the per-version play test. If unreliable, that version shows "Shader switching is unavailable" instead of risking a crash. |
| 18 jars per release is a maintenance load | CI builds everything. Releases are batch-uploaded. Old nodes only get bug fixes. |
| Old Sodium versions (0.2 / 0.3) may load differently with old Iris | Each node pins the exact Sodium build Iris lists as compatible. |
| Patch versions inside one jar's range can differ slightly | Smoke-test the first and last patch of every range before release. |

---

## 7. Decisions needed before P0

- **D1: 1.16.5 and 1.17.1.** These need Java 8/16 and have the oldest Iris builds (from 2022), with few current players. **(A)** skip them, ending at 1.18.2 (16 jars). **(B)** include them in P6 using Jabel plus converting records. *Recommended: A for now, B later if people ask.*
- **D2: Coverage.** One jar per row (18), as above. *Recommended.*
- **D3: Iris version ranges.** Pin each jar to its Iris line (e.g. `>=1.6 <1.7`) rather than one exact build, so Iris patch updates keep working. The runtime check still blocks other lines. *Recommended.*
- **D4: Where to publish.** CurseForge only (same project, more files), or Modrinth too.

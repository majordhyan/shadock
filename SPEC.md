# Shadock — v1 Specification

Shadock is a client-side Fabric mod for Minecraft Java Edition that provides a fast, compact shader-pack dock for Iris. It prioritizes reliability, compatibility and smooth interaction over experimental features.

> One-line pitch: *Shadock — the fastest way to switch, organize, and import Minecraft shaders.*

## 1. Targets

| Item | v1 target |
|---|---|
| Minecraft | 26.3 (only) |
| Loader | Fabric Loader |
| Iris | `1.11.7+26.3-fabric` (required, pinned) |
| Sodium | Latest compatible with Iris 1.11.7+26.3 (Iris requires it) |
| Fabric API | Latest build for 26.3 |
| Java | 25+ |
| Mod ID | `shadock` |
| Package | `dev.shadock.shadock` |
| License | MIT |
| Author | whynotdhyan |

Compatibility policy:
- Iris is a **required** dependency in `fabric.mod.json`; Fabric Loader blocks launch without it.
- Narrow constraint. `1.11.7+26.3-fabric` is only Modrinth's label: inside the jar, Iris reports `1.11.7+mc26.3`. So `fabric.mod.json` declares `"iris": ">=1.11.7 <1.11.8"`, and the runtime check accepts `1.11.7+…` only. Widen the range only after verifying Iris internals stay stable across patches.
- No claims of support for 26.2, 26.1.x or older.
- All Iris internals live behind an `IrisCompat` interface (`isAvailable`, `getShaderPacks`, `getActiveShaderPack`, `setActiveShaderPack(name)`, `reloadShaders`) with version-specific adapters. No direct Iris internal calls elsewhere.
- Startup validation of the Iris version. On an unsupported version: disable switching, show "This Iris version is not supported". No speculative reflection on unknown builds.
- If Iris is present but integration fails: "Shadock could not connect to Iris. Check that you are using a supported Iris version."

## 2. Interaction model

- The key opens a custom **non-pausing `Screen`** (does not pause singleplayer). Cursor is free for click/drag; gameplay input pauses while open, like chat or inventory.
- No "hold key for mouse overlay" mode in v1.
- Closing returns control to gameplay immediately. Works in singleplayer and multiplayer.
- **v1 opens in-game only** (keybinds don't fire on the title screen). A main-menu/pause-menu button may come later.
- **Toggle:** pressing the Shadock key while the dock is open closes it. Handled inside the screen's input handling by matching the `KeyMapping`, and skipped if a focused widget accepts text input.

## 3. Keybind

- Exactly one binding in vanilla Options → Controls → Key Binds.
- Category **Shadock**, name **Open Shader Dock**, default **H** (changed from O: vanilla 26.3 binds O to the Friends list, and Iris uses R/K/I), single key, no modifiers, fully rebindable, participates in vanilla conflict detection.
- Mouse-button binding is optional and low priority.
- In-dock **Key** button opens the vanilla Key Binds screen (no auto-scroll/highlight, no custom key-capture UI).

## 4. Shader pack discovery

- Scan the active instance's `shaderpacks` directory (create it if missing).
- Include `.zip` files **and** unzipped folders with a valid shader-pack structure (a `shaders/` directory).
- Cache the list. Refresh on: first dock open of the session, after an import, on manual refresh, and on file-system change if reliably detectable. Never rescan per frame or per open.
- Invalid packs: show as Invalid/Unsupported only if safely identifiable. Never activate a pack that fails basic validation. Packs removed externally must not be selectable.

## 5. Shader switching

- Click → status shows `Applying…` immediately → hand off to Iris with no artificial delay, sleeps or extra loading screens.
- The game/dock may freeze briefly while Iris compiles on the main thread. That is accepted. No async compilation, no "preload" claims.
- Selecting the already-active pack: do nothing, show `Already active`.
- Never reload during a drag.
- On success → `Ready`. On failure → `Iris error: <Iris message>`, or `Iris could not load this shader pack.` if Iris gives no usable message. Keep the dock open, do not mark the failed pack active, never delete the file.

## 6. Drag-to-reorder

- Sort modes in v1: **Custom Order** (default) and **Alphabetical**. No "Recently Used".
- Custom Order: click-hold-drag rows. 4–6 px threshold before a press becomes a drag; a drag never activates a pack. Dragged row lifts and turns semi-transparent, following the cursor vertically. Other rows ease aside (80–120 ms). An insertion line or gap shows the drop target.
- Save on mouse release only; persist across restarts. Key the order by the **exact file/folder name** (e.g. `ComplementaryUnbound_r5.3.zip`), not cleaned-up slugs (they can collide and can't map back) and not indexes. Drop missing packs from the saved order without crashing.
- Alphabetical: dragging disabled, with a lock icon/tooltip "Switch to Custom Order to rearrange shaders."
- No pinning/favorites in v1.
- Efficient rendering for 100+ packs: no per-frame widget rebuilding, minimal allocation, smooth wheel scrolling.

## 7. Import (`+` button)

- Native file picker via **SDL3's `SDL_ShowOpenFileDialog`**: Minecraft 26.3 uses SDL3 for its window, and the dialog is asynchronous, so the game never blocks and SDL handles macOS's main-thread rules itself. Filtered to `.zip`, one file at a time.
- Validate that the archive has a shader-pack structure. Otherwise show: "This file does not appear to be a valid shader pack." No nested-ZIP support, extraction or repair.
- Copy asynchronously into `shaderpacks`; never freeze the game. Refresh the dock on completion.
- Duplicate filename, per the setting:
  - **Ask** (default): Replace / Keep Both / Cancel dialog.
  - **Replace**: overwrite only after explicit confirmation. If the file is the active pack, warn first, then: turn shaders off (Iris holds the ZIP open, which locks it on Windows) → replace → re-activate and reload. Otherwise no reload.
  - **Keep Both**: `Name.zip` → `Name (2).zip` → `Name (3).zip` … (first unused).
  - **Cancel**: abort without touching files.
- No drag-and-drop file import in v1.

## 8. Auto-hide

- Default 5 s. Options: 0, 3, 5, 10 s, Never. (`0`/Never = stays open until closed or toggled.)
- Timer runs only when the mouse is not over the dock, no drag is active, no file picker/dialog is open, and no activation/reload is in progress.
- Hovering resets/pauses it. Starting a drag or opening the picker cancels it.

## 9. UI

Fixed **bottom-left** with a margin clear of the hotbar and chat. **Vanilla-faithful 32x style** (decided after the Phase 2 play-test, replacing the earlier dark-glass direction):
- Shadock's own double-resolution textures, drawn from vanilla's palette: a popup-style panel, stone buttons (normal / highlighted / pressed / disabled), a scrollbar, and a pixel "SHADOCK" wordmark. The list background and separators are vanilla's, so they follow resource packs.
- Vanilla selection box (white outline, black fill) for the active pack, vanilla text colors with shadow, vanilla tooltips.
- A 120 ms slide-in and a 90 ms slide-out (both can be disabled), a short pressed state on buttons, and an animated spinner while busy.
- Keyboard: ↑/↓ select, Enter apply, 1–9 quick apply, Shift+↑/↓ move a pack.
- All art comes from `tools/make_assets.py` + `tools/pixelkit.py` (original, generated). Branding files (logo variants, 1200×400 banner, icons) are written to `branding/`.

The dock contains only:
- Shader list (scrollable; long names truncated with an ellipsis, full name on hover)
- Active-shader indicator (accent line/checkmark)
- `+` import button
- Close button
- Gear (settings) button
- Key button
- Status line

**Excluded from v1:** icons/thumbnails, shader option editing, profiles, auto dimension/biome switching, favorites/pinning, search/filter, right-click context menu (open folder, remove, rename, pin), drag-and-drop import, Recently Used sort, custom key overlay, metadata parsing, movable dock position, Mod Menu integration, third-party config libraries.

## 10. Settings

Tiny built-in screen opened from the gear icon. Stored as JSON under `config/shadock/`.

| Setting | Default | Options |
|---|---|---|
| Open Shader Dock | O | Vanilla keybind (via the Key button) |
| Dock position | Bottom-left | Bottom-left only |
| Sort mode | Custom Order | Custom Order, Alphabetical |
| Auto-hide delay | 5 s | 0, 3, 5, 10 s, Never |
| Dock animation | Enabled | Enabled, Disabled |
| Duplicate import behavior | Ask | Ask, Replace, Keep Both, Cancel |

## 11. Messages

Inside or beside the dock, never chat spam or silent failure: `No shader packs installed` · `No shader packs found` · `Invalid shader pack` · `File already exists` · `Import failed` · `Could not connect to Iris` · `This Iris version is not supported` · `Shader switching is unavailable` · `Already active` · `Applying…` · `Loading shaders…` · `Ready` · `Importing shader…` · `Imported: <name>` · `Iris error: <message>`.

Localize all strings via lang files (`en_us` first).

## 12. Build plan (three phases)

### Phase 1: Build every feature
1. Project setup: Fabric Loom, MC 26.3, latest Fabric Loader/API for 26.3, Iris `1.11.7+26.3-fabric` required, Sodium in `suggests`, Java 25 toolchain, Gradle wrapper, MIT `LICENSE`, `fabric.mod.json` (also `"java": ">=25"` and the real minimum loader version; author stays a marked placeholder until provided), placeholder `icon.png`, `en_us.json`.
2. Package layout under `dev.shadock.shadock`:
   ```
   Shadock, ShadockClient
   config/  ShadockConfig, ShadockConfigManager, DockPosition
   iris/    IrisCompat, IrisCompatFactory, IrisPackInfo, impl/Iris_1_11_7_26_3_Compat
   shader/  ShaderPackScanner, ShaderPack, ShaderPackSortMode, ShaderPackOrderStore,
            ShaderImporter, ShaderImportResult
   ui/      ShadockDockScreen, ShaderListWidget, ShaderListEntry, DockLayoutManager,
            DockAnimations, DockStatusRenderer
   input/   ShadockKeybinds, DockToggleHandler
   util/    FileNames, JsonUtils, TextUtils
   ```
3. `IrisCompat` (`isAvailable`, `getShaderPacks`, `getActiveShaderPackName`, `setActiveShaderPack`, `reloadShaders`, `getLastError`). All Iris references, reflection and mixins live only in `iris/`.
4. Discovery → 5. Keybind → 6. Dock screen → 7. Switching → 8. Drag reorder → 9. Import → 10. Settings + JSON config → 11. Auto-hide → 12. Error messages.

Config example (`config/shadock/shadock.json`):
```json
{
  "sortMode": "CUSTOM_ORDER",
  "autoHideDelaySeconds": 5,
  "dockAnimation": true,
  "duplicateImportBehavior": "ASK",
  "customPackOrder": ["ComplementaryUnbound_r5.3.zip", "BSL_v8.4.zip", "Photon"]
}
```
Create the file if missing, fall back to defaults on malformed JSON, and save only after meaningful changes.

### Phase 2: Testing and fixes (no new features)
- **Automated (by Claude):** clean build, game launch with log check, unit tests (duplicate naming, malformed config, order store, pack validation), and an audit that no Iris code is outside `iris/`.
- **Manual (by the owner, from a checklist):** singleplayer/multiplayer, creative/survival, with chat/inventory/other screens open, during compilation, during the file picker, during drag, at the moment auto-hide triggers, rapid clicks, repeated open/close, external file removal, resize/GUI scale, and the import edge cases (invalid, resource pack, long/special names, duplicate, in-use, huge, read-only folder).
- Performance: no noticeable open delay, smooth with 50+ packs, no per-frame scans or config writes, no leaks across open/close cycles.

### Phase 3: Visuals and assets
- Dark glass dock: translucent charcoal, soft gray-blue border, cyan primary / violet secondary accent, soft green/amber/red for status, near-white/muted-gray text. Open/close 100–150 ms, reorder 80–120 ms, no overshoot, can be disabled, no animation while Iris compiles.
- Clear hover/pressed/disabled/active/drag/loading/error states, tooltips, truncation with full name on hover, behavior on focus loss.
- Assets (original, custom-drawn wordmark, no third-party fonts):
  - `assets/shadock/icon.png` (128×128, readable at 16 px)
  - `textures/gui/shadock_logo.png`, `_dark`, `_light`, `_icon_only`, `_banner`
  - `shadock_banner.png` (1200×400, "Switch. Organize. Import.")
  - `textures/gui/icons.png` atlas: gear, key, plus, close, lock, warning, error, success, loading
  - Panel, row, scrollbar and insertion-line textures if needed

### Phase exit criteria
Each phase ends with its checklist from the build plan. The final delivery checklist is §13 below plus: original icon, logo and banner; polished dock UI; stable final build.

## 13. Definition of done (v1)

- [ ] Loads only with a supported Iris version
- [ ] "Open Shader Dock" appears in vanilla Key Binds, default `H`, no modifier
- [ ] Key opens a non-pausing bottom-left dock; pressing it again closes the dock
- [ ] Lists ZIP and folder packs; the active pack is clearly marked
- [ ] Click activates via `IrisCompat`; re-selecting the active pack doesn't reload
- [ ] Drag reorder in Custom Order persists across restarts; Alphabetical disables dragging
- [ ] `+` imports a valid ZIP; duplicates ask before replacing; invalid archives give a clear error
- [ ] Auto-hide pauses during drag, import, dialogs and shader loading
- [ ] Shadock adds no delay of its own around Iris loading
- [ ] No Iris internals are used outside the compat adapter

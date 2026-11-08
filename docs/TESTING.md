# Shadock — Phase 2 in-game test checklist

These are the checks only a person playing the game can do. Tick each box, and for anything that fails, write what you did, what you expected and what happened (a screenshot or `run/logs/latest.log` helps).

## Setup

1. Put **2–3 real shader packs** (e.g. Complementary, BSL, Photon) in `run/shaderpacks/`. One should be a ZIP and one an unzipped folder.
   The folder already holds three test entries: `TestFolderPack` (valid structure but empty, so Iris will fail to load it), `NotAShaderFolder` (invalid), and `Broken Resource Pack.zip` (invalid).
2. Start the game from the project folder:
   ```
   set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot
   gradlew runClient
   ```
3. Create a singleplayer world.

## 1. Opening and closing

- [ ] **H** opens the dock in the bottom-left corner. The world keeps running (not paused) and nothing dims.
- [ ] The dock slides/fades in quickly (about 0.1 s) with no visible lag.
- [ ] **H** again closes it, and it does **not** reopen by itself.
- [ ] **Esc** and the **✕** button both close it.
- [ ] After closing, mouse look, movement and attacking work normally (the mouse is not stuck).
- [ ] Open/close it 20+ times quickly: no slowdown, no errors.
- [ ] With chat open, typing **h** types a letter and does not open the dock. Same in a sign or book editor.
- [ ] With the inventory or pause menu open, **H** does nothing.
- [ ] Options → Controls → Key Binds has a **Shadock** category with **Open Shader Dock = H**. Rebind it to another key (e.g. J), and the new key opens/closes the dock. Bind H to something else too and check the red conflict warning shows.
- [ ] Binding it to a mouse side button works (optional feature).

## 2. Pack list

- [ ] ZIP and folder packs both appear. `NotAShaderFolder` and `Broken Resource Pack.zip` show **Invalid** in red with a tooltip.
- [ ] A very long pack name is cut with "…", and hovering shows the full name.
- [ ] With 10+ packs, the mouse wheel scrolls smoothly and a thin scrollbar appears.
- [ ] Copy a new ZIP into `run/shaderpacks/` in File Explorer while the dock is open: it appears within a second or so.
- [ ] Delete a pack in File Explorer while the dock is open: it disappears. Clicking where it was causes no error.
- [ ] Empty `run/shaderpacks/` (move everything out): the dock says **No shader packs installed**, and the **+** button is highlighted.

## 3. Switching shaders

- [ ] Clicking a real pack shows **Applying…**, then the game freezes briefly while Iris compiles, then the status says **Ready**, and the pack has the cyan bar and ✔.
- [ ] The **Active:** line in the footer shows the pack's name.
- [ ] Clicking the active pack shows **Already active**, with no reload or freeze.
- [ ] Clicking an **Invalid** pack shows **Invalid shader pack**, with no reload.
- [ ] Clicking `TestFolderPack` shows an **Iris error: …** message (or "Iris could not load this shader pack."). The dock stays open, the pack is **not** marked active, and the file is not deleted.
- [ ] Clicking several packs rapidly: only the first switch runs, and there is no crash.
- [ ] Close the dock immediately after clicking a pack: the switch still finishes.
- [ ] Switching packs in Iris's own screen (**H** is Shadock, Iris's shader screen is its own key) is reflected in the dock the next time it opens.

## 4. Drag to reorder

- [ ] A normal click (no movement) activates the pack and never starts a drag.
- [ ] Moving the mouse a few pixels while pressing does **not** start a drag. Moving about 5+ pixels does.
- [ ] While dragging: the row follows the cursor, other rows slide aside, and a highlighted gap with a cyan line shows the drop position.
- [ ] Releasing places the pack there. Dragging never activates a pack or reloads shaders.
- [ ] Dragging near the top/bottom edge scrolls the list.
- [ ] **Esc** during a drag cancels it (the order is unchanged and the dock stays open).
- [ ] Releasing the mouse outside the game window (or alt-tabbing mid-drag) cancels the drag cleanly, with no stuck row.
- [ ] The new order is still there after closing the dock, and after **restarting the game**.
- [ ] Settings → Sort: **Alphabetical**: the list is A–Z (case-insensitive), the header shows **A–Z**, and trying to drag shows "Switch to Custom Order to rearrange shaders." Switching back to Custom Order restores your order.

## 5. Importing

- [ ] **+** opens the Windows file picker filtered to .zip. The game keeps rendering while it's open.
- [ ] Cancelling the picker shows **Import cancelled**.
- [ ] Importing a valid shader ZIP shows **Importing shader…**, then **Imported: name.zip**, and the pack appears at the top of the list.
- [ ] Importing a resource-pack ZIP, a ZIP that contains another ZIP, or a renamed non-ZIP file each shows "This file does not appear to be a valid shader pack." No file is added.
- [ ] A ZIP with spaces/special characters in its name imports correctly.
- [ ] A large ZIP (100 MB+) imports without freezing the game.
- [ ] Importing a ZIP whose name already exists shows **Replace / Keep Both / Cancel**:
  - [ ] **Keep Both** saves `Name (2).zip`; doing it again gives `Name (3).zip`.
  - [ ] **Replace** overwrites the file.
  - [ ] **Cancel** (or Esc) changes nothing.
- [ ] Replacing the **active** pack: the message warns it's active, shaders turn off, the file is replaced, and the pack loads again (no "file in use" error).
- [ ] Settings → Duplicate imports = **Keep Both** / **Cancel** / **Replace** each behave as named (Replace still asks for confirmation).

## 6. Auto-hide and settings

- [ ] With the default 5 s, the dock closes 5 s after the mouse leaves it.
- [ ] It does **not** close while the mouse is over it, during a drag, while the file picker or a prompt is open, during a shader switch, or while the game window is in the background.
- [ ] Settings → Auto-hide = **Never**: the dock stays open until closed.
- [ ] Settings → Dock animation = **OFF**: the dock appears instantly.
- [ ] The gear (⚙) opens settings, and **Done** returns to the dock. The **Key** button opens Key Binds, and Esc there returns to the dock.
- [ ] Settings survive a restart (`run/config/shadock/shadock.json`).

## 7. Other situations

- [ ] Creative and Survival both work.
- [ ] Multiplayer server: the dock opens and switching works (it is client-side only).
- [ ] Change GUI scale (Options → Video) and resize the window while the dock is open: it stays bottom-left, readable, and doesn't overlap the hotbar/hearts on small windows.
- [ ] With 50+ packs (copies are fine) the dock still opens instantly and scrolls smoothly.

## 8. Vanilla-style dock (redesign)

- [ ] The dock uses Minecraft's own popup frame, stone buttons, list background, separators and scrollbar, so it looks like part of the game.
- [ ] Header: prism logo, **Shadock**, pack count, and the vanilla ✕ close button (highlights on hover).
- [ ] Toolbar buttons, left to right: **Import (+)**, **Open Folder**, **Sort** (the icon shows the current mode; click to switch), **Key Binds**, **Settings**. Each has a tooltip.
- [ ] Rows: a purple box icon for ZIP packs and a folder icon for folder packs. The active pack has the white vanilla selection box and green checkmark. Hovering shows a drag handle (or a lock in A–Z mode).
- [ ] The status line shows **Active: <pack>** (or **Shaders off**) when idle, and messages in vanilla colors otherwise.
- [ ] The vanilla scrollbar can be dragged, and the mouse wheel scrolls.
- [ ] Keyboard: **↑/↓** select a pack, **Enter** applies it, **1–9** apply that row directly, **Shift+↑/↓** moves the selected pack (Custom Order only).
- [ ] The **Open Folder** button opens `run/shaderpacks` in File Explorer.

## Known behavior (not bugs)

- The game freezes for a moment while Iris compiles a pack. Shadock can't avoid this; it only makes sure it adds no delay of its own.
- When a pack fails to compile in a world, Iris itself also posts a message in chat. That message is Iris's, not Shadock's.
- The header icons (⚙ ✕ ≡ ✔) are font characters for now. Phase 3 replaces them with proper art.

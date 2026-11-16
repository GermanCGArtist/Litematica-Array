# Litematica Array 2.0: editor architecture and work packages

Source of truth for implementation. Product decisions are in `DECISIONS.md`. The visual spec is `design/DESIGN.md` (from the design agent); where the two disagree on looks, DESIGN.md wins, and on behaviour this file wins. Base branch: `2.0-dev` (off `26.1.1`).

## 1. Shape of the system

```
            ImGui windows (Scene, Properties, Top view, Toolbar, Status, Menus)
                  │ read model / send commands
                  ▼
 Input ──► EditorController ──► Commands (undoable) ──► Model (projects, nodes)
 (keys,        │ camera, tools, gizmo drag                  │
  mouse)       ▼                                            ▼ observed by
          WorldOverlay (gizmo, outlines, overlaps)     Reconciler (every client tick)
                                                            │ creates/moves/removes
                                                            ▼
                         Litematica placements  ◄── DerivedFiles (async .litematic writer)
                                 │
                                 ▼ unchanged Litematica: render, print, material list, verifier, Syncmatica
```

- **The model is the source of truth for everything the editor manages.** Litematica placements are its output.
- **The Reconciler** generalises today's `ArraySync`. Each tick it makes the set of Litematica placements match the model: one placement per visible schematic object, per array cell, using the derived file when the object is trimmed or Booleaned.
- **It also reads back.** If a managed placement was moved outside the editor (Litematica's own hotkeys or GUI, or a Syncmatica peer), it writes that move back into the model as an undoable "external change" instead of fighting it.
- **Pure logic has no Minecraft imports:** model, transforms, arrays, overlaps, the cycle check and serialisation. It lives in `dev.schemarray.editor.core` and is unit-tested like the shape generator (`src/test`).

## 2. Packages (new code)

| Package | Contents | MC imports? |
|---|---|---|
| `editor.core` | `Node` hierarchy, `Transform` (pos, rot 0–3, mirrorX), `Box`, `ArrayStack` (reuses `ArrayStore.Mod` semantics), `Overlaps`, `FrontOrder`, `CycleCheck`, `Ids` | no |
| `editor.io` | `ProjectIo` (project.json), `AssemblyIo` (.lasm), `ZipExport`, `Migration1x` (folders.json + arrays.json → Loose) | no (Gson + NIO only) |
| `editor.ops` | `Command` interface, `UndoStack`, all edit commands (Move, Rotate, Mirror, Duplicate, LinkedDuplicate, Realise, BreakUp, MakeLocal, SetFront, AddModifier, ApplyBoolean, Reparent, Rename, Hide, Delete, CreateAssembly, LinkProject), plus file-side effects with undo (create/delete files under the project) | no |
| `editor.mc` | `Reconciler`, `LitematicaAdapter` (the **only** class that touches Litematica placement APIs), `DerivedFiles` (async mask + write), `SchematicInfo` (size, block counts, cached), `Picking` (ray vs schematic world + boxes) | yes |
| `editor.cam` | `EditorCamera` (orbit + fly state), camera mixin, spectator follow | yes |
| `editor.view` | ImGui windows: `SceneWindow`, `PropertiesWindow`, `TopViewWindow`, `Toolbar`, `StatusBar`, `ContextMenus`, `Hud`, `Toasts`, `EditorTheme` | ImGui only |
| `editor.world` | `WorldOverlay`: gizmo, selection/ghost/overlap/area/wire outlines, name tags, box-select rect (modelled on Heavymatica's `WireframeRenderer` for 26.1.1) | yes |
| `editor.api` | new object-type API for Heavymatica (see §7) | no |
| `uihost` (separate Gradle subproject `ui-host/`, its own mod id `cg-uihost`, jar-in-jar) | ImGui context, natives for all platforms, GLFW input routing, font atlas, frame hook, `UiHost.register(UiLayer)` | yes |

The old malilib list mixins stay. The placement list keeps working for people who never press F6, and it also shows projects as folders.

## 3. Data model

```
Node { id (uuid), name, hidden, children? }
 ├─ ProjectNode   { path, world, dim, origin, active, sync: local|shared }    (top level only)
 ├─ FolderNode    { open }                                                   (no transform)
 ├─ LooseNode                                                                (top level, one, not saved in a project)
 ├─ ObjectNode    { file (relative to project), transform, front, mods[], wire, placementIds{cell→hash} }
 ├─ AssemblyNode  { transform, front, mods[], source: local | link(path) }   children = parts
 └─ ExternalNode  { typeId, transform, data(JsonObject) }                    (Heavymatica areas and others via API)
Modifier = Array { enabled, count, relative[3], useRelative, constant[3], useConstant }
         | Boolean { enabled, op: DIFFERENCE|INTERSECT, targetId }
```
- **Transforms compose from the root down.** Folders and projects have none. Rotation is about the node's origin; the editor keeps the pivot visually centred by adjusting `pos` when it rotates.
- **`front`** is a global integer per world (1 = front), shared by all open projects. Ties break by node id, so the result is always the same.
- **Linked assemblies:** the `children` of a linked AssemblyNode are loaded from the `.lasm` and not saved in the project; they are read-only. Make Local copies them in.
- **Files on disk:** `schematics/Projects/<name>/project.json`, `files/**`, `.cache/derived/<objectId>-<hash>.litematic`. A `.lasm` has the same JSON shape as a project's node list, with no world/dim.
- **IDs:** every node has a UUID. Litematica placement hash ids are kept in `placementIds` and are never used as model ids.

## 4. Reconciler rules
1. Walk the model; for every visible ObjectNode, compute its world cells (base + array cells) and the file each cell uses (original, or derived when an enabled Boolean or a lost overlap applies).
2. For each cell, find its placement by stored hash id. If it's missing or uses the wrong file, create it with `SchematicPlacement.createFor`; set origin, rotation and mirror (same transform rule as `ArraySync.transform`).
3. Remove managed placements that are no longer wanted. Never touch placements that aren't managed, except Loose ones.
4. **Drift:** if a managed base placement's origin, rotation or mirror differs from the model, and the editor didn't cause it this tick, record an `ExternalChange` command (undoable) and update the model.
5. **Budget:** at most 64 placement creations per tick, and rebuilds batch with `markChunksForRebuild`, like `ArraySync`'s delayed rebuild.

`ArraySync` keeps running only for 1.x arrays that haven't migrated yet. Migration moves them into ObjectNodes under Loose, and from then on the Reconciler owns them.

## 5. Derived files
- **Trigger:** a model change that affects an object's mask (its Boolean target, its front order, or the transform of anything overlapping it). It's debounced, and while a gizmo drag is active it waits for the release.
- **Job (worker thread):** load the source `LitematicaSchematic`, copy each sub-region's block container, and set masked positions to air.
  - Difference: the target's non-air blocks.
  - Intersect: everything outside them.
  - Trims: blocks inside the volumes of objects with a better front value, counting only their non-air blocks.
  - Then write `.cache/derived/<id>-<maskHash>.litematic`.
  - Writing the same mask hash again is a no-op (it's a cache).
- **Swap:** the Reconciler moves the cells to the new file on the client thread when the job finishes. The status bar shows the queue.
- **Apply Boolean:** copy the current derived file to `files/<name>_bool.litematic`, point the object at it, and remove the modifier (undoable: the file is moved to `.trash/` on undo, never deleted).
- **Cache cleanup:** on project save, delete derived files that no node references.

## 6. Editor mode, camera, input
- **F6 toggles editor mode** (rebindable). On enter: release the mouse, start the editor camera at the player's eye, and set the ImGui windows visible. On exit: restore the camera and grab the mouse again.
- **Camera:** a mixin on `Camera.setup` (or the 26.1.1 equivalent) replaces position and rotation while the editor is active.
  - Orbit: target, distance, yaw and pitch.
  - Fly: position, yaw and pitch, WASD/QE, Shift ×3, wheel speed.
  - Hold the right mouse button to fly, with the mouse grabbed while held. A right click under 4 px of movement opens the context menu.
- **Spectator:** each tick, send the player to the camera position (client-side movement is accepted in spectator), so chunks load. In other game modes the player stays put.
- **Input priority:** ImGui windows (when hovered or focused) > typed-transform modal > gizmo > viewport tools > camera. Vanilla keybinds are blocked while the editor is active, except chat and F-keys.
- **Picking:**
  1. Raycast against Litematica's schematic world. Map the hit block to the cells whose box contains it, and choose by front order.
  2. Fall back to ray-vs-box.
  3. Alt cycles through every owner hit along the ray.

## 7. Heavymatica object API (`editor.api`)
```java
public interface SceneObjectType {
  String id();                                       // "heavymatica:area"
  String displayName();
  JsonObject create();                               // new default data
  Box bounds(JsonObject data);                       // local bounds, block units
  void renderOutline(JsonObject data, Transform world, OverlayDrawer d, boolean selected);
  void drawProperties(JsonObject data, PropertyUi ui); // ImGui section; returns via ui.changed()
  default boolean affectsBlocks() { return false; }  // Heavymatica: false (never trims, never Booleans)
}
SchematicArrayApi.registerObjectType(SceneObjectType)
SchematicArrayApi.objectsOfType(String typeId) -> List<SceneObjectView> // world transform + data, for Manhattan export
```
The `API_VERSION` goes to 2. The 1.5 list-entry API keeps working: its entries show under Loose as read-only rows.

## 8. Syncmatica (M6)
- **Detection:** reflection only, so there's no compile dependency. If Syncmatica isn't present, the Push button is hidden.
- **Push changes:**
  - Each visible cell is shared as a placement, using the derived file where one applies.
  - A manifest placement is shared too: a 1×1×1 air schematic named `[LA2] <project>` whose description holds the project's node tree (gzip+base64 when it's over 30 kB).
  - Placements this project pushed earlier that no longer exist are removed.
- **Receive:** a manifest seen in Syncmatica's list is offered as "Open shared project". The placements that come with it are adopted as the cells of that project.

## 9. Work packages

**Rules for every package:**
- Branch `2.0-dev`. One package per agent, and only the files listed.
- Build with `cmd /c "F:\workflow\SchematicArray\build.bat --console=plain -q"`, and `gradlew test` for `core`/`io`/`ops`.
- Never start, stop or kill Minecraft, and never copy jars into a profile. The architect installs.
- Commit with the noreply email `344753632+GermanCGArtist@users.noreply.github.com`, one commit per package, and don't push.
- Before calling a Litematica API you haven't used before, check its descriptor with `javap` on the 0.27.14 jar in the Gradle cache.

**Agent column:** S = Sonnet, G = Gemini (Antigravity CLI, Hydrogen), H = Haiku, A = architect review only.

| WP | Milestone | Agent | Owns | Done when |
|---|---|---|---|---|
| 0.1 | M0 | S | `ui-host/**`, root `settings.gradle`, `build.gradle` include | A subproject builds a `cg-uihost` jar with imgui-java 1.90 binding + lwjgl3 backend + natives for win/linux/mac x64+arm64. `UiHost.register(UiLayer)`, frame hook via a `RenderSystem.flipFrame` mixin, keyboard/mouse mixins that ask registered layers `wantsInput()`, a `UiHost.theme()` copy of Manhattan's UiTheme tokens + fonts. Litematica Array nests it with `include`. A demo layer (Ctrl+F6) shows ImGui's demo window. |
| 0.2 | M0 | S | `editor/cam/**`, camera mixin, `EditorMode` | F6 toggles editor mode. Orbit, pan, zoom, fly and frame work. Spectator follow. Vanilla movement is blocked in editor mode. HUD placeholder text drawn via uihost. |
| 0.3 | M0 | H | `editor/core/Transform`, `Box`, tests | Pure transform/box maths matching `ArraySync.transform`, 100% unit-tested (rot × mirror × offsets). |
| 1.1 | M1 | S | `editor/core/**` (except 0.3), `editor/io/**`, tests | Node model, project.json/.lasm round-trip, Migration1x from real 1.6 configs (fixtures in `src/test/resources`), cycle check, front order. Tests green. |
| 1.2 | M1 | S | `editor/ops/**`, tests | UndoStack + all commands in §2 as pure model ops (file effects through a `FileSystemPort` interface, faked in tests). |
| 1.3 | M1 | S | `editor/mc/LitematicaAdapter`, `Reconciler`, `SchematicInfo` | §4 implemented. Opening a project shows its placements; closing removes them; drift is adopted. |
| 1.4 | M1 | G or S | `editor/view/**` (Scene, Properties, ContextMenus, Toolbar, Status, Toasts) | Built from `design/DESIGN.md`: tree with badges, rename, drag-reparent, filter, multi-select; properties for each node type; every menu entry wired to a command (disabled when not available). |
| 1.5 | M1 | S | `editor/mc/Picking`, `editor/world/WorldOverlay` (selection only) | Click/Shift/Alt selection in the world, selection outlines, name tags. |
| 2.1 | M2 | S | `editor/world/Gizmo`, drag logic, snapping | Gizmo per DESIGN.md, ghost while dragging, commit on release, face snapping, multi-select pivot modes. |
| 2.2 | M2 | H | typed-transform modal + HUD | G/R + axis + number + Enter/Esc, with live preview. |
| 2.3 | M2 | H | duplicate/linked duplicate/hide/local view keys | Shortcuts wired to existing commands; local view filters the Reconciler's visibility. |
| 3.1 | M3 | S | arrays on Object/Assembly nodes in the Reconciler; Realise wiring | Arrays of assemblies work, Realise produces a folder of objects, and they can be arrayed again. |
| 4.1 | M4 | S | `.lasm` link/append/make local/break up/realise, project-as-assembly, exports (`ZipExport`, bake) | Per §3/§5; Bake writes one multi-region `.litematic`. |
| 5.1 | M5 | S | `editor/mc/DerivedFiles`, overlap + Boolean masks, Apply | §5; the material list reflects trims (verified on a fixture). |
| 6.1 | M6 | G or S | `TopViewWindow` | Per DESIGN.md: pan/zoom, select, move camera, overlaps shown. |
| 6.2 | M6 | S | Syncmatica bridge (§8) | Push/receive against a local test server with Syncmatica. |
| 6.3 | M6 | S | `editor/api/**` + Heavymatica branch `2.0-api` | Heavymatica areas are scene objects; Manhattan export still works. |
| 6.4 | M6 | S | Manhattan: move to `cg-uihost` (separate worktree off Manhattan `main`, **after** the user's `feat/spatial-chunking` lands) | Manhattan registers its Shell as a UiLayer; both mods run together. |
| 7.x | M7 | S/H | ports to 26.1.2 / 26.2 / 26.3, releases | Per the 1.x porting notes (scancode keys on 26.3, `mc.gui.screen()` on 26.2+). |

**Known risk:** until WP 6.4 lands, Manhattan's own ImGui and `cg-uihost` both claim the GLFW callbacks when both mods are installed. During development we test Litematica Array 2.0 without Manhattan, or the host stays off while Manhattan's workspace is open. WP 6.4 removes the conflict.

## 10. Notes from implementation
- **(WP 1.1) Base placement key:** `ObjectNode.BASE_CELL = "base"` is the key of the object's own placement in `placementIds`. Array cells keep their 1.x "i,j,k" keys.
- **(WP 1.1) Front order:** `front = 0` means unnumbered and sorts behind everything; ties break by id.
- **(WP 1.1) Session state:** `ProjectNode.path` and `active` are not saved.
- **(WP 1.1) Linked assemblies:** children are not saved. Whoever resolves links (WP 1.3/4.1) loads the `.lasm` and must give each linked child an **instance-scoped id**, `Ids.derived(instanceId + ":" + childId)`, because the same `.lasm` linked twice would otherwise give duplicate ids.
- **(WP 1.3) Structure:** `ReconcileCore` (planning and writing, no Minecraft) talks to a `PlacementPort`; `LitematicaAdapter` is the real port and `Reconciler` the per-tick wrapper (join sequence, world key, ownership queries for `ArraySync`). `ProjectManager` (no Minecraft) owns the `Scene`.
- **(WP 1.3) Drift rule:** a base placement counts as moved outside the editor when it differs from the transform the reconciler last wrote or saw, so a change of the model is never drift. On the first sight after a join the model wins in a project and the placement wins in Loose. `ExternalChange(node, before, after)` is emitted after the model was updated; WP 1.2 hooks in with `Reconciler.setDriftListener`.
- **(WP 1.3) Placement ids:** the stored hash id is reused as the UUID when a placement has to be created again (hide/show, close/open, wrong file), so project.json does not churn. Hidden nodes keep their ids.
- **(WP 1.3) Loose and session:** after the migration Loose lives in `config/schemarray/editor-loose/<world>.lasm`; `config/schemarray/editor-session.json` holds per world the `migrated` flag, the open project folders and the active one. A Loose object whose placement is not in Litematica is never deleted or recreated: it gets the runtime flag `ObjectNode.missing()` (not saved; the UI greys it out with a "placement not found" hint), is skipped, and the flag clears when the placement is back. Only an explicit Delete removes it. Migrated nodes are never hidden: 1.x folder hiding stays with `Folders.tick`, which switches rendering.
- **(WP 1.3) Ownership bridge:** once an array is owned by an ObjectNode, `ArraySync` skips it, and its helpers (`isCopy`, `original`, `mods`, `setMods`, `copyCount`, `copyIds`) read and write the node's modifiers. `arrays.json` and `folders.json` are never written by the editor.
- **(WP 1.3) Holder:** the editor never removes anything from Litematica's `SchematicHolder`. Placements load files through `getOrLoad`; `SchematicInfo` uses a private copy (`LitematicaSchematic.createFromFile`, cached by path and mtime). Known limitation: if an original is edited while Litematica holds a copy, Litematica shows the old copy until it reloads it.
- **(WP 1.3) Not yet:** assembly-level arrays (WP 3.1), derived files (WP 5.1), adopting placements the player makes in Litematica after the migration.
- **(WP 2.1) Gizmo split:** the maths is in `editor/gizmo` (`AxisProjection`, `FaceSnap`, `Pivots`, `TypedTransform`, `BoxSelect`, `GizmoLayout`, `TurnMath`; no Minecraft or ImGui types, all unit-tested). `editor/world/Gizmo` is the glue: it takes mouse and key events from `EditorInputLayer`, draws arrows, ring, snap label and box rectangle with ImGui's background draw list, and hands a `Preview` to `WorldOverlay`, which draws the dashed x-ray ghost, tether and snap face with Minecraft gizmos. A gesture never moves the placement; release or Enter commits one `Move`/`Rotate`/`Duplicate` inside one undo transaction.
- **(WP 2.1) Pivot:** always a block: the block holding the centre (an even size takes the lower middle block). `EditorActions` asks the host for it, so Rotate, Mirror and the Properties rotation buttons follow the toolbar's pivot mode too. Snap and pivot mode are saved in `editor-ui.properties`.
- **(WP 2.1) Snapping:** within 1.5 blocks along the drag axis, other axes within 1.5 blocks; butting beats flush on a tie; targets are the owners (object or top assembly) that are not part of the selection. A plane drag snaps X, then Z.
- **(WP 2.2) Typed transform:** G/R/Shift D/Alt D start it; the key handling lives in `Gizmo.onKey`, checked before the other shortcuts (DESIGN 12.4). Shift D/Alt D run one `Duplicate`/`LinkedDuplicate` with the typed delta on Enter (not a duplicate at once, then a move), so Esc leaves no stray copy and one Ctrl Z undoes it.
- **(WP 2.3) Local view:** a runtime filter in `ReconcileCore.setLocalView(owners)`: owners in projects that are not listed are treated as hidden for the pass (their placements are removed and come back when it is cleared). Nothing is saved and Litematica's render toggles are untouched. Loose objects are exempt (removing their placement would mark them missing). `EditorSession.boxes()` applies the same filter, so picking and the gizmo only see what is visible. It switches off when the world changes.
- **(WP 2.3) Block count:** `SchematicInfo.Info.blockCount()` now returns -1 until a single daemon worker thread has counted the (privately loaded) schematic; the Properties window shows an ellipsis meanwhile.

# Mario Kart 8 v4.2 (Turbo_v42_fixed.rpx) — Reverse-Engineering Findings

Target: `D:\wiiU\...\Turbo_v42_fixed.rpx` (Wii U, PPC32 big-endian).
All addresses are **decompressed virtual addresses** (RPX sections are zlib-compressed with a
4-byte BE size prefix; landmarks only exist after decompression — see `work\decomp_rpx.py`,
output `work\Turbo_v42_plain.rpx`, imported+analyzed in Ghidra project `work\ghidra-proj\mk8`
as `/Turbo_v42_plain.rpx`, 33396 functions).

Raw evidence: `re-out\step1.txt`, `step2.txt`, `step3.txt`, `step4.txt`;
symbol table: `re-out\symbols.txt` (769 symbols);
complete GX2-import call-site map from `.rela.text`/`.rela.data`: `re-out\import_calls.json`.

---

## 0. How imports are linked (important for hooking)

- Import stub sections (`.imp10`–`.imp40`) contain **all-zero placeholder bodies in the file**;
  the RPL loader fills them and rewrites every `bl` via the RELA tables (type 10, R_PPC_REL24).
- In the file, a call to `GX2Foo` (stub `0xC0008xxx`) decodes as `bl 0x04008xxx` — the
  low 24 bits match the stub; the loader relocates it. Verified: `bl 0x04008870` at
  `0x0273951C` ↔ symbol `GX2CopyColorBufferToScanBuffer` (`st_value 0xC0008870`).
- Therefore: **patch the `bl` at the call site** (or replace it with `bla` to pack code and
  call the import from there, exactly like SM3DW-VR's `rrCopyMarker` does).
- Key GX2 stub addresses (from `symbols.txt`): ClearBuffersEx `0xC0008858`,
  ClearColor `0xC0008860`, CopyColorBufferToScanBuffer `0xC0008870`, CopySurface `0xC0008878`,
  CallDisplayList `0xC0008850`, DrawDone `0xC0008888`, Flush `0xC00088B8`,
  InitColorBufferRegs `0xC0008918`, SetContextState `0xC0008A18`, SetColorBuffer `0xC0008A00`,
  SetDRCBuffer `0xC0008A28`, SetDefaultState `0xC0008A48`, SetScissor `0xC0008B38`,
  SetTVBuffer `0xC0008B80`, SetTVPosition `0xC0008B98`, SetViewport `0xC0008BE8`,
  SwapScanBuffers `0xC0008BF8`.

## 1. Frame present / flip flow (near landmark 0x024AF384)

### 1.1 The landmark is inside the per-frame camera/viewport update
- `FUN_024AF330` `[0x024AF330–0x024AF7B3]` — per-frame loop over 5 viewport slots; the
  landmark instruction `0x024AF384 = bne .+0x54` skips a 4-iteration flag-write loop
  (`0x024AF388–0x024AF3D4`). MK8's 60FPSMultiplayer pack replaces it with `b .+0x54`
  (`patch_60FPSFullDraw.asm`; same pattern at `0x024AF000`, `0x02416B74`).
- Caller chain (all confirmed by call refs):
  - `FUN_024B0060` `[0x024B0060–0x024B03E7]` — **the key parent**: picks TV/DRC mode into
    `obj+0xAF0` (0..3), fills 5 viewport-slot descriptors at `obj+0x890+i*0x20`, then calls
    in order: `FUN_024AEE78` (projection, §2) at `0x024B038C`, `FUN_024AF330` (landmark) at
    `0x024B03B8`, then `FUN_024AF7B4`, `FUN_024AF8F4`, `FUN_024AFD28` (surface/rect setup).
  - `FUN_0250E364` `[0x0250E364–0x0250E41B]` — master per-frame update dispatcher;
    calls `FUN_024B0060` only when global `DAT_1018999C != 0` (camera-system enable flag).
  - `FUN_0250E41C` `[0x0250E41C–0x0250E74B]` — scene render-mode switch (8 modes 0–7,
    configures 1–4 render targets via `FUN_0250DD30`/`FUN_0250DD74`); calls the update
    dispatcher at 7 sites.
  - `FUN_0250E74C` → `FUN_0250E8E8` → many scene callers (`FUN_020A5DB4`, `FUN_02433CBC`,
    `FUN_024CFFAC`, `FUN_025D7884`, `FUN_025E051C`, `FUN_0261BF6C`, …).

### 1.2 Actual present (swap) functions — reached **via vtables**, no static call refs
- **`FUN_024D4EE0` `[0x024D4EE0–0x024D4FC3]` — TV present/flip** (decompiled):
  callback(`obj+0x370`, 0) → `GX2DrawDone` @ **`0x024D4F20`** → wait/flag handling →
  `GX2Flush` @ `0x024D4F88` → callback(1) → if `obj+0x28 == 2`: `GX2SwapScanBuffers` @
  **`0x024D4FA8`** → `GX2DrawDone` @ `0x024D4FAC`.
  Virtual method in vtable at **`.rodata 0x100C7320`** (neighbors `0x02734B5C`, `0x024D4CC8`,
  `0x02734D20`).
- **`FUN_02734C3C` `[0x02734C3C–0x02734D1F]` — second present path** (clone): `GX2Flush` @
  `0x02734CE4`, `GX2SwapScanBuffers` @ **`0x02734D04`**, `GX2DrawDone` @ `0x02734D08`.
  Vtable at **`.rodata 0x10100DB0`**.
- **`FUN_02739424` `[0x02739424–0x02739537]` — minimal copy-to-scan**:
  `GX2SetViewport` → `GX2SetScissor` @ `0x02739500` → `GX2CopyColorBufferToScanBuffer` @
  **`0x0273951C`** with target r4 = 1 (TV) or 4 (DRC) chosen by `(*(param+0x1c)+0xc == 0)`.
  Vtable slots at **`.rodata 0x10101354`** (copy) and **`0x1010135C`** (clear → below).
- **`FUN_027AFB74` `[0x027AFB74–0x027AFD9B]` — full copy-to-scan**: reads color-buffer
  descriptor from `param+0x1c` (w/h at +0xA4/+0xA8, surface at +0xA0, format +0xC4/+0xD0),
  `GX2SetViewport` @ `0x027AFC68`, `GX2SetScissor` @ `0x027AFC7C`, `GX2InitColorBufferRegs`,
  then `GX2CopyColorBufferToScanBuffer` @ **`0x027AFD58`** (stack-built descriptor, TV/DRC
  via `iVar6` 4 vs 1) or @ **`0x027AFD78`** (`device+0xA0`), then `FUN_0275EAE8`
  (`GX2CallDisplayList` or `GX2SetContextState(DAT_1018C7C0+0x138)`).
  Vtable pointer at **`.rodata 0x1012771C`** (table base `DAT_101276D8`, written into
  global `DAT_101B39D0` by the function itself at first call).

### 1.3 Clear functions
- **`FUN_0273929C` `[0x0273929C–0x02739423]`** — clear dispatcher: flag bits (r4 & 1/2/4)
  select `GX2ClearBuffersEx` @ `0x027392F0/328/350` or `GX2ClearColor` @ **`0x02739368`**,
  always followed by `GX2SetContextState(0x02739360)`. Same vtable as `FUN_02739424`
  (`0x1010135C`).
- **`FUN_027AFE24`** — `GX2ClearColor` @ `0x027AFE24` + `FUN_0275EAE8` + ISYNC (mid-function
  entry; clear2 sites also at `0x027AFF88/0x027AFFA8`).
- Other ClearColor sites: `0x0271F5A8`, `0x02A97068` (see `import_calls.json`).

## 2. Projection setup (landmark 0x024AEF70)

The pipeline is **screen-rect / aspect based** (no standalone 4×4 perspective builder found
in this path; MK8 feeds rects + aspect to surfaces/shader constants).

- **`FUN_024AEE78` `[0x024AEE78–0x024AF32F]`** — projection landmark function:
  - reads a **14-entry × 16-byte rect table at `obj+0x930`** (index < 0xE; per-mode screen
    rects: TV/DRC/mirror etc.);
  - copies 4 floats (x1,y1,x2,y2) into viewport slot `obj+0x890+idx*0x20` at +0x18..+0x24;
  - computes aspect `(x2-x1)/(y2-y1)` — the `fdivs` at **`0x024AEF70`** (`eced6024`,
    `fdivs f7,f13,f12`) — and stores it to the **device object at `+0xAC`**.
  - A second aspect division exists at **`0x024AEBEC`** (both hooked by the AspectRatio pack).
  - Aspect constants (16:9 = 1.7777778) live at `.rodata 0x100C359C` and `0x10121E30`.
- **`FUN_024AF8F4` `[0x024AF8F4–0x024AFD27]`** — recomputes `device+0xAC = (x2-x1)/(y2-y1)`
  from rect sources `obj+0x940` (TV) / `obj+0xA00` (DRC); writes a 5-float surface
  descriptor to `device+0x18..+0x28` via `FUN_027384A8` + `FUN_027373DC`; saves device
  pointer at `obj+0x124`.
- **`FUN_024AFD28` `[0x024AFD28–0x024B005F]`** — selects a rect entry (index from mode table
  `.rodata DAT_100C2F1C` + `obj+0xAF0` + `obj+0xAF7`) from `obj+0xA10` or `obj+0x930`
  (14 × vec4) and copies 5 floats to **`device+0x440..0x450`** (the device viewport rect
  later consumed by the present/copy functions of §1.2).
- **Per-eye projection-offset hook candidates (ranked):**
  1. The rect tables themselves: `obj+0x930` / `obj+0xA10` / `obj+0x940` / `obj+0xA00`
     (patch x1/x2 per eye — SM3DW-VR style: keep native rect, hand the consumer an
     eye-shifted copy);
  2. `device+0x440` write-back in `FUN_024AFD28` (single choke point for the final rect);
  3. `device+0xAC` aspect result (FOV scaling only, no asymmetric offset);
  4. The `fdivs` sites `0x024AEF70` / `0x024AEBEC` (already proven patchable — AspectRatio pack).
- SM3DW-VR reference approach (for porting): it snapshots the whole **perspective object**
  per eye (matrix at perspective-object **+0x44**, vtable check before copy), swaps the
  camera's projection pointer (`camera+0xC`), and restores the native object before
  present (`mtProjectionRestore`); `rrCopyMarker` wraps `GX2CopyColorBufferToScanBuffer`
  and encodes eye/frame markers via clear colors (`rrValueTV/DRC`).

## 3. GX2 import call sites (from `.rela` tables — complete)

| Import | Call sites (vaddr) | Containing function(s) |
|---|---|---|
| GX2CopyColorBufferToScanBuffer | `0x0273951C`, `0x027AFD58`, `0x027AFD78` | FUN_02739424, FUN_027AFB74 |
| GX2SwapScanBuffers | `0x024D4FA8`, `0x02734D04`, `0x027B8928` | FUN_024D4EE0, FUN_02734C3C, FUN_027B88D0 |
| GX2DrawDone | 19 sites incl. `0x024D4C84`, `0x024D4F20`, `0x024D4FAC`, `0x02734C7C`, `0x02734D08` | present funcs + others |
| GX2ClearColor | `0x0271F5A8`, `0x02739368`, `0x027AFE24`, `0x027AFF88`, `0x027AFFA8`, `0x02A97068` | clear funcs |
| GX2ClearBuffersEx | `0x027392F0`, `0x02739328`, `0x02739350`, `0x027AFE6C`, `0x027AFEB0` | FUN_0273929C, FUN_027AFE24 |
| GX2SetTVBuffer | `0x027396E4` (wrapper), `0x02739934` | FUN_027396E4 (→ GX2SetTVScale @ `0x02739724`-area + ISYNC) |
| GX2Flush | e.g. `0x024D4F88`, `0x02734CE4` | present funcs |

Full per-import lists: `re-out\import_calls.json`.

## 4. Recommended VR hook points (MK8 analogues of SM3DW-VR)

| Purpose | Hook address | How |
|---|---|---|
| Marker/eye copy (TV) | `0x0273951C` or `0x027AFD58` | replace `bl` with `bla` to pack `rrCopyMarker`-style wrapper calling `GX2CopyColorBufferToScanBuffer` |
| Marker/eye copy (DRC path) | `0x027AFD78` | same |
| End-of-frame sync | `0x024D4F20` / `0x024D4FAC` (`GX2DrawDone` in TV present) | wrap like SM3DW `rrBeforeCalc` (DrawDone + restore) |
| Swap interception | `0x024D4FA8` / `0x02734D04` | conditional skip/redirect of `GX2SwapScanBuffers` |
| Per-eye rect/projection | `0x024AFD28` writes to `device+0x440` (or table `obj+0x930`) | copy-and-offset rect per eye |
| Aspect/FOV scale | `0x024AEF70`, `0x024AEBEC` | existing AspectRatio-pack technique |
| Camera-update gate | `FUN_024B0060` entry or `DAT_1018999C` | optional: force mode/flags for stereo |

Known-good v4.2 addresses from official graphic packs (cross-check):
`0x024AEF70`, `0x024AEBEC` (aspect fdivs); `0x024AF384`, `0x024AF000`, `0x02416B74`
(per-draw skip `bne→b`); `0x027DF528/0x027DF2F4/0x0271DE0C` (FXAA `blr`);
`0x024D97F8/0x024D9474` (trophies); `0x024D74CC/0x024D7148` (courses);
`0x024DCE24` (rating); LOD floats `0x10121DF0`, `0x100F33F0`.

## 5. Open items / confidence

- **High confidence**: landmark semantics; present/swap/copy/clear functions and their
  call-site addresses; vtable locations; camera-update call chain; rect/aspect pipeline
  offsets (`obj+0x930/A10/940/A00`, `device+0xAC`, `device+0x440`).
- **Medium**: vtable slot meanings (which virtual index is present vs copy — neighbors
  identified, base not fully classified); class object layouts beyond the noted offsets.
- **Not found (bonus item)**: an explicit view/matrix build site. The camera path passes
  floats via `DAT_101D6944+0x148+0x16c` into `FUN_024AF330`; matrix math likely lives in
  the scene/camera code (`FUN_024CE59C`-related objects) and may be uploaded as shader
  constants. Next step if needed: trace stores of 16 consecutive floats from camera objects,
  or hook per-draw cbuffer updates.
- Import stub bodies are file-zeroes (loader-filled); Ghidra call refs to stubs don't
  exist by design — always map call sites via the RELA tables.

---

## 6. Stereo double-draw hook sites

Raw evidence this session: `re-out2\s1.txt`..`s11.txt` (Ghidra scripts `ghidra-scripts\S1,S2,S3,S5..S11.java`;
S4 deleted � compile error would poison the run). Section table used for all raw scans:
`.text` vaddr `0x02000020` = file off `0x0075B550`, size `0xB020DC`
(NOTE: `work\Turbo_v42.text.bin` is the **compressed** blob � unusable for opcode scans).

### 6.1 Frame render entry � the scene render routine

**`FUN_020A5DB4` `[0x020A5DB4�0x020A5EF7]`** (full disasm, `s7/s10`) � called once per
target per frame (vtable; no static callers):

```
020a5dd4  bl  FUN_024CE59C          ; global; camera mgr = global+0x2BC (700)
  param_2 != 0 (DRC):
020a5dec  li r0,3 ; stw 8(r1)
020a5df8  bl  FUN_0250E8E8          ; dispatch render-mode 3 (DRC scene: calc+record+deferred submit)
020a5e1c  bl  FUN_020A2290          ; scan-target switch (target0, 0, 1)
  param_2 == 0 (TV):
020a5e40  li r9,5 ; stw 0xc(r1)
020a5e4c  bl  FUN_0250E8E8          ; dispatch render-mode 5 (TV scene)  <== "before calc" region
020a5e70  bl  FUN_020A2290          ; scan-target switch (target0, 1, 1)
020a5e84..020a5eb0  loop: bl FUN_0209F8A4 (sub-renderables @ param+0x5C)
020a5eb4  lis r12,0x1019 / lwz r12,-0x7ad0(r12)   ; DAT_10188530
020a5ec0  bl  FUN_02116B60          ; present/flip (rrPresent analogue) <== "after draw" hook
020a5ed0  bl  FUN_020A5D14 ; FUN_020A5D94(0)      ; frame teardown
```

Chain below it: `FUN_0250E8E8` `[0x0250E8E8]` (mode gate: `*( *DAT_101D693C + 0x134 ) & 4`,
skip if `iVar1+0x130-3U < 2`) ? `FUN_0250E74C` (lock `FUN_0274C444/478` on param+0xCC �
lock is released per dispatch, so re-invoking the dispatch from a cave is safe) ?
`FUN_0250E41C` 8-mode switch ? per-pass setup `FUN_0250DAC4` + `FUN_0250E364`?`FUN_024B0060`
(camera/viewport/flags, �1/�2) and vtable-dispatched record `FUN_026A5D98` (�6.2).

### 6.2 Record + submit (rrRecord / rrDraw analogues)

**`FUN_026A5D98` `[0x026A5D98�0x026A5F17]`** � per-pass record driver (r27=renderable,
r28=record context; full disasm `s10`):

```
026a5e04  lwz r0,0xc(r27) ; rlwinm r0,r0,2,30,31   ; eye/target id = renderable+0xC >> 30 (0..3)
026a5e1c  stw r0,0xb8(r28)                          ; context+0xB8 = eye id  (per-draw field)
026a5e2c  bl  FUN_02695720   ; rotate: End+CallDisplayList previous pass DL, begin new list
026a5e30  cmpwi r30,1 / beq 0x026a5e94              ; pass id 1 skips scalar uniform
026a5e40  bl  FUN_02695660(context, 8)              ; uniform offset for reg-slot 8
026a5e90  bl  GX2SetVertexUniformReg(off, 4, {1.0,1.0,1.0,float(param_3)})   ; stub 0x04008BE0
026a5e98  bl  FUN_02695674(context)                 ; GX2SetVertexUniformReg(off, 0x10, context)
                                                     ; *** uploads 4x4 matrix FROM context+0 ***
026a5ecc  bl  GX2SetVertexUniformReg(off, 4, {0,0,0,0})   ; reg 10, if param_5
026a5ee0  bl  FUN_026A4700   ; record body A (draw calls; see 6.5)
026a5eec  bl  FUN_026A5158   ; record body B
026a5ef8  bl  FUN_026A53E0   ; record body C
026a5f00  bl  FUN_026A599C   ; record body D � raster/primitive state (decompiled s9)
```

`FUN_02695674` `[0x02695674�0x026956AF]`: `r5 = r3` (passed pointer) ? the **4x4 view(-proj)
matrix lives at context+0**; uploaded as 16 vertex uniform regs each pass.

`FUN_02695720` `[0x02695720�0x0269576F]`: `new = FUN_0269E484(device+0x25C)`; store to
context+0xBC; `bl FUN_0269E4DC(device+0x25C, new)` @ `0x02695750`; context+0xB6=0.

`FUN_0269E4DC` `[0x0269E4DC�0x0269E59B]` (disasm `s10`) � DL lifecycle:
`GX2BeginDisplayListEx` @ `0x0269E530`, `GX2SetFetchShader` @ `0x0269E540`,
`GX2SetVertexShader` @ `0x0269E548`, `GX2SetPixelShader` @ `0x0269E550`,
`GX2EndDisplayList` @ `0x0269E560`, then
**`GX2CallDisplayList` @ `0x0269E578` = THE scene-DL submit point** (rrDraw).
Architecture is deferred: each pass first submits the PREVIOUS pass's finished list, then
records into a new one (`FUN_0269E4CC(dl, reg) = *(reg*4 + *(dl+0x30))` computes uniform
offsets into the open list).

### 6.3 Per-draw flag fields (MK8 analogues of SM3DW +0x74 / +0x380 / +0x388)

| SM3DW-VR cave field | MK8 field | Evidence |
|---|---|---|
| eye/target id (+0x388 use) | **renderable+0xC top 2 bits** ? stored to **context+0xB8** | `0x026A5E04�0x026A5E1C` |
| list-ready byte (+0x380) | **context+0xB6** (0 = list open/recording); DL-entry **+0xC** (0 ? record path `FUN_027B1CD4`) | `0x02695758`; `FUN_024B25CC` decomp |
| flag word (+0x74) | DL-entry **+0** flags byte (bit1 dirty ? `FUN_027B1EE8`); per-frame block **device+0x870..0x888** written by `FUN_024AF330` (landmark `0x024AF384` = `bne` skip of its 5-camera flag loop); camera-obj **+0x50 bit0, +0x94 bit2**; slot flags **device+0x890+idx*0x20 +0x1C..+0x1F** | `FUN_024B25CC`/`FUN_024AF330`/`FUN_024AEE78` decompiles |
| current DL ptr | **context+0xBC** (device arena `context+0xA8` ? +0x25C) | `0x02695734�0x0269574C` |
| list index (halfword) | `*(*(param_1+0x1C)+0x78)+0x18)+0x18` into 0x80-stride DL-entry array (`+4` DL ptr, `+0x30/+0x34/+0x38` uniform descs, `+0x78` parent) | `FUN_024B25CC` decomp (`s2`) |

Also: device **+0xAF0** (TV/DRC mode 0..3, written `FUN_024B0060`), **+0xAF4/+0xAF5**
(multi-target flags), **+0xAF7** (stereo/multi-context flag � selects eye-rect sources
`obj+0x940` vs `+0xA00` and camera contexts 6/10/2 at `DAT_1018D248+0xF74`), **+0xB04**.

### 6.4 Camera / projection objects (feeds �2)

- Camera manager: **`FUN_024CE59C()+0x2BC`**; per-slot camera descriptors
  `camMgr+0x24+idx*0x18` (+8 type, +0xC flags, +0x14 scene-view id � written by
  `FUN_020A64E4` @ `0x020a656c` on camera change), slot list `camMgr+0x9C..0xB0`,
  mode `camMgr+0xBC`.
- Per-frame camera update **`FUN_024B0060`** (from mode switch `FUN_0250E364`, and directly
  from camera-change `FUN_020A64E4` @ `0x020a6580`): fills device+0x890 slots, then calls
  `FUN_024AEE78` @0x024b038c, `FUN_024AF330` @0x024b03b8, `FUN_024AF7B4`, `FUN_024AF8F4`
  @0x024b03c8, `FUN_024AFD28` @0x024b03d0 (rect/aspect chain, �2). Rect updates also fire
  from present-side `FUN_020A0654`/`FUN_020A2EB0` (toggling `DAT_1018999C+0x890+idx*0x20+0x1F`).
- Camera objects: rect floats **+0x18..+0x28**, params **+0x4c** (? obj with **+0xac** aspect,
  written `(x2-x1)/(y2-y2)` in `FUN_024AEE78`/`FUN_024AF8F4`); per-camera draw objs fields
  **+0x1CC/+0x1DC/+0x238/+0x2DD** (`FUN_024AF330`). Candidate camera matrix: **20-word
  (4x4+row) copy from `*(*(FUN_023ABBE8()+0x118)+0x40)[cam]+0xD0`** in `FUN_025D7884`
  @ `0x025d87e8` region.
- Projection 4x4: not a standalone builder in this path; likely rides the **uniform blocks**
  bound in `FUN_024B25CC` via `*(pass_desc+0xC)+4 / +0xC` (GX2SetPixel/Vertex/GeometryUniformBlock),
  or the camera matrix at +0xD0 above.

### 6.5 Ranked stereo double-draw hook list

1. **Primary frame pair (SM3DW `rrBeforeCalc`/`rrSecondDraw` style)** � TV path of
   `FUN_020A5DB4`:
   - before-calc hook: branch entry **`0x020A5E34`** (or function entry `0x020A5DB4`);
   - after-draw hook: **`0x020A5EB4`** (before `bl FUN_02116B60` @ **`0x020A5EC0`**);
   - cave: `GX2DrawDone` ? set eye=1 (patch renderable+0xC top bits consumed @ `0x026A5E04`,
     and/or eye-rect/aspect state �2) ? re-run TV dispatch (`lis/stw mode 5; bl FUN_0250E8E8`,
     re-execute `0x020A5E4C` region with saved camMgr) ? `GX2DrawDone` ? restore eye ?
     fall through to present @ `0x020A5EC0`. Re-entrancy safe (per-dispatch lock released).
   Confidence: high (addresses), medium (re-entry side effects).
2. **Per-pass eye matrix swap** � inside `FUN_026A5D98`:
   - `GX2DrawDone` at list rotation **`0x026A5E2C`** (isolates each pass submit);
   - patch the **16 words at context+0 (r28)** between `0x026A5E2C` and
     **`bl FUN_02695674` @ `0x026A5E98`** (IPD-offset matrix per eye);
   - force eye id by patching the `stw` source @ `0x026A5E04`.
   Confidence: high (full disasm; matrix source = context+0 proven by `r5=r3` in `FUN_02695674`).
3. **Scene DL submit funnel** � **`bl GX2CallDisplayList` @ `0x0269E578`** (`FUN_0269E4DC`).
   All scene-list submits pass through here; wrap = DrawDone + double-submit after re-record.
   Gate on the owning arena (`device+0x25C` chain) � other arenas share the helper.
   Confidence: high.
4. **Per-eye rect/projection** (reuse proven �2 machinery): per-eye patch of rect tables
   `obj+0x930 / +0xA10 / +0x940 / +0xA00` (per-eye via `device+0xAF7` + viewport slots),
   or single choke point `device+0x440..0x450` write in `FUN_024AFD28`,
   or aspect `obj+0xAC` / `fdivs 0x024AEF70`. Confidence: high (pack-proven addresses).
5. Secondary DRC path (skip for TV-only VR): `0x020A5DEC`/`0x020A5E1C` of the same function.

### 6.6 Open items (post-section-6)

- Record bodies `FUN_026A4700/5158/53E0` contain embedded primary-opcode-4 words
  (Espresso-specific or warped code) right after their prologues � static disassembly and
  decompilation stop at ~0x20 bytes (`s11`); their role (draw/uniform recording) is inferred
  from call order only. Runtime (after kernel fixup) is unaffected.
- Writer of the context+0 matrix (the "calc" side copy) not traced � vtable-dispatched pass
  system; hook 2 patches the copy point instead, so not blocking.
- `FUN_026A5D98` vtable slot / ultimate caller unknown (no static refs; pass traversal).
- Projection 4x4 storage not definitively pinned (candidates in �6.4).

Confidence summary: frame/record/submit addresses + call structure = **high**;
context+0 matrix upload = **high**; per-draw field mapping = **high** (disasm-proven);
full double-draw cave behavior (game-side side effects of re-dispatch) = **medium**
until prototyped.

## 7. MILESTONE 1 - mono pair transport works end-to-end (runtime-proven)

Status: **DONE.** The CEMU VR layer recognizes MK8's codecave, acknowledges the
marker protocol, records a stereo pair from the marker clears, and submits it to
the Quest 3 over VirtualDesktopXR. The user is inside the VR space (mono, i.e.
the same game image is blitted to both eyes - no per-eye pose yet).

### 7.1 What actually works (proven by `cemuvr_layer.log`)
- Graphic pack `MarioKart8_VR_v42` applies at codecave `0x01800100`; the layer
  scans, finds the `CTFL` flow header, and writes the `CTM1` ack at `+24`
  (`reference.ack guest=01800100 marker-only protocol acknowledged`).
- `mkCopyMarker` (hooked at `0x0273951C`, `0x027AFD58`, `0x027AFD78` -
  `GX2CopyColorBufferToScanBuffer` sites) runs the original copy, then emits two
  `GX2ClearColor` protocol clears (eye0 = a/b, eye1 = b/a) once the ack is set.
- Cemu forwards those to `vkCmdClearColorImage`; the layer decodes them:
  `rgba=3CE0E0E1/3DF8F8F9/3F7BFBFC/00000000` -> `{eye0, slot0, target1}` and the
  swapped pair -> `{eye1, slot0, target1}` (`mkdiag.decode`).
- `ReferencePairImages::record` snapshots the source image; `presentReferencePair`
  blits the pair into XR swapchains and submits:
  `first_pair size=10240x5760 format=R8G8B8A8_UNORM`,
  `phase=running`, `reference.stamp`-style submit.

### 7.2 Critical findings needed to make it work
- **GX2ClearColor -> vkCmdClearColorImage**: Cemu's clear path the layer hooks is
  `vkCmdClearColorImage` (NOT `vkCmdClearAttachments`). The protocol works only
  because `mkCopyMarker` issues `import.gx2.GX2ClearColor(buf, target, a, b, 0)`.
- **Image extent limit**: MK8 (with the 10K Graphics pack preset) renders into a
  **10240x5760** color buffer. `ReferencePairImages` rejected it via
  `validate_failed ... >8192`; the limit was raised to **16384** in
  `reference_pair.h`. Without the Graphics pack the buffer is smaller.
- **Pairs form from eye0-then-eye1 clears on the SAME scan target.** The wrapper
  emits both eyes per copy call; `ReferencePairOrder::observe` pairs them.
- **DON'T hook `FUN_026A5D98` at all** (`0x026A5E20` / `0x026A5E94`): any
  replacement instruction inside that function makes Cemu **crash during early
  module load** (`mw_shamo`). The function runs at boot; the marker-clear route
  avoids it entirely. (Double-draw hook therefore needs a different, later,
  load-safe site.)

### 7.3 Verified environment (reproducible)
- Layer: `D:\mk8-VR\runtime\cemuvr_layer.dll` (built from
  `D:\mk8-VR\mk8-vr\src-fork` by `D:\mk8-VR\scripts\build_mk8vr.cmd`).
- Pack: `D:\mk8-VR\mk8-vr\graphicPacks\MarioKart8_VR` (version 7; header + 3 copy
  hooks). `patch_diag.asm` was removed; the diag block lives in `patch_vr.asm`.
- Launch: `D:\mk8-VR\runtime\launch-mk8-vr.cmd` (sets `VK_LAYER_PATH`,
  `VK_INSTANCE_LAYERS`, `CEMUVR_ENABLE`, `CEMUVR_MK_DIAG=1`, `CEMUVR_LOG`,
  `VK_LOADER_LAYERS_DISABLE=~implicit~`). Cemu launched via `start "" /D`.
- Crash bisect lesson: header-only pack boots; adding a code hook at a BOOT-path
  function crashes; adding the copy-site hook (rendering-path only) is safe.

### 7.3b Menus / pre-race sequences also captured
The layer engages from **boot** (the pack's codecave + marker clears fire before
any race), so the live marker/ack pipeline is active during the 2D menu and
intro sequences as well. Consequence: once per-eye poses land, **every** scene
(menus, intro cutscenes, races) is a candidate for the VR/stereo path - no
separate "menu only" hook is required. The only remaining requirement for the
"2D menu -> 3D scene" goal is the per-eye projection (Milestone 2).

### 7.4 Next (Milestone 2 - real stereo)
- Give the two marker clears a **per-eye view-projection matrix** so the two
  images differ (true stereo). Candidate: hook the matrix upload `bl FUN_02695674`
  @ `0x026A5E98` is UNSAFE (boot crash); use the projection override that SM3DW
  applies in `mtProjectionRestore` (per-eye perspective) at a rendering-only site.
- Consider raising the resolution: 10240x5760 x2 in VR is unnecessary; either
  disable the 10K preset or downscale the pair blit.
- The TV window stays 2D by design until the guest renders two distinct eyes;
  the layer already outputs to the headset.

## 8. MILESTONE 2 - session findings (hook safety + why we are mono)

### 8.1 The 0x026A5E20 producer hook is SAFE when the trampoline is correct
- The earlier "any hook inside FUN_026A5D98 crashes at mw_shamo load" was WRONG.
  Root cause of the original crash: the codecave hook (`mkDiagHook`) had **no
  terminator** -- it fell through past its last instruction into the following
  data/EOF. A correct trampoline boots and runs.
- Correct, proven trampoline for `0x026A5E20 = ba mkDiagHook`:
  `mflr r0 / stwu r1,-0x20(r1) / stw r0,0x24(r1)` ... body ... then REPLAY the
  replaced instruction `lwz r12,0x14(r27)` and `b 0x026A5E24` (relative `b`,
  not `ba`). Return via `lwz r0,0x24(r1) / mtlr r0 / addi r1,r1,0x20`.
- This hook fires **~400k+ times** and is stable. `hook@026A5E20=49 80 02 86`
  (a `b` to the codecave) confirms the patch landed.
- **DO NOT use `stmw`/`lmw r14..r31` in the codecave.** A variant that saved the
  full non-volatile set (`stmw r14,0x2C(r1)` in a `-0x70` frame) crashed the
  guest JIT (WER `c0000005`, fault module "unknown" = guest JIT memory, fault
  offset varies per run). Baseline that spills only r27/r28/r30 is fine. Keep
  codecave frames small and clobber only registers the hook itself owns.
- Crash signature to recognise: `c0000005`, `Fault Module Name = unknown`,
  offset in `0x0020xxxxxxxx` range -> Cemu recompiling/crashing on guest codecave.

### 8.2 Matrix producer confirmed: record-context+0 IS the view-projection
- At `0x026A5E20`, `r27` = renderable, `r28` = record context, `r30` = pass id.
  `*(context+0)` is a real view-projection matrix. Captured example:
  `row0 3FADD2C8 0 0 0 / row1 0 401A8279 0 0 / row2 0 0 BF80068E 4458D298 /
   row3 0 0 BF800000 4459477B` (X/Y scale split across the first 8 words, depth
  translation in the next 8). This is the per-eye perturbation target.
- `r12 = *(renderable+0x14)` is the per-pass uniform table base; the matrix is
  uploaded by `bl FUN_02695674(context)` @ `0x026A5E98`.

### 8.3 WHY WE ARE STILL MONO (the decisive finding)
- The fork has two output modes in `xr_core.cpp` `XrCore::endFrame`:
  1. **Surface mode** (`m_surfaceMode`): one quad, `XR_EYE_VISIBILITY_BOTH` ->
     the mono "2D frame in VR space" the user sees (line ~977).
  2. **Stereo projection** (`!m_surfaceMode && haveL && haveR && m_viewsValid &&
     m_frameState.shouldRender`): real per-eye projection (line ~1003).
- `cemu_layer.cpp:1261-1275`: `surfaceFrame = menu || unposed`. **`unposed`
  forces surface mode.** `referenceUnposedSurface(a,b,enabled)`
  (`reference_marker.h:10`) returns TRUE iff `enabled && a==0 && b==0` -- i.e.
  when **both pose tokens are zero**.
- Our MK8 pack emits the **eye marker clears only**; it never emits a **pose
  token**, so `pairs.poseTokens[] == 0` -> `unposed=true` -> surface/mono. This
  is exactly why `stereo.pair incomplete ... shouldRender=0 views=1` prints.
- **Fix path (SM3DW analogue):** `rrCopyMarker` emits a SECOND clear -- the pose
  token marker -- with magic floats `.1875/.8125` and `f3,f4` carrying a 16-bit
  per-eye token (`decodeReferencePoseToken`, `reference_marker.h:13`). The token
  is sourced from SM3DW's `rrPoseLatch` / `rrProjectionPoseSequence`, which are
  filled by the per-eye projection override (`mtProjectionRestore`).
  -> To get stereo we must (a) compute a per-eye projection at a rendering-only
     site, (b) latch a matching token per eye, (c) emit that token as the second
     marker clear so the layer sets `unposed=false` and takes the stereo path.

### 8.4 Graphics load (frame drops)
- MK8 Graphics pack presets were set to 10K (10240x5760) + Extreme shadows
  (400%) + 16x AF + Extreme motion blur + Ultra LOD. This overloaded the Shadow
  PC. Lightened in `%APPDATA%\Cemu\settings.xml` to: TV 1280x720 (HD, Default),
  Shadows Medium, AF 1x, Motion Blur Normal, LOD Normal. Backup:
  `settings.xml.pre-lighten-backup`. Source pair is now 1280x720 (much lighter).
  -> For real stereo this also means each eye renders 1280x720.

### 8.5 CONFIRMED LIVE: pose mailbox + pose-token emit DO flip the layer to stereo
Implemented and tested (pack `patch_vr.asm`):
- **CTPH pose mailbox** (version 5, `mkPoseHeader`) added to the MK8 codecave so
  `marioTouchMailbox()` (`cemu_layer.cpp:1048`) finds it. Confirmed live:
  `reference.ack guest=01800100 marker-only protocol acknowledged` and
  `reference.pose mailbox=... version=5`.
- **Pose-token emit** in `mkCopyMarker`: before each eye-marker clear, emit a
  second `GX2ClearColor` with `{.1875,.8125, tokLo, tokHi}` read from header
  words 46/47 (the layer writes the published token there, `payload[43,44]`).
- **Result (log):** eye markers decode for **both eyes, both targets**
  (`mkdiag.decode eye=0/1 target=1` then `eye=0/1 target=4`); lifecycle advances
  `waiting_for_stereo_pair -> stereo_pair_parameters_known -> xr_session_ready`;
  `stereo.pair ... shouldRender=1` (was 0). So the token half WORKS.
- **BUT** the pair is still `left=1 right=1 ... views=1` -> both eye records
  reference the **same source image** (one copy site, same buffer) -> the layer
  has no distinct second image -> **stereo path renders nothing** (user sees the
  game only in the flat desktop panel, not in VR). With the pose-token clear
  REMOVED (A/B test) the layer returns to safe mono surface mode (M1 behaviour:
  flat quad in VR).
- **Conclusion:** the remaining Milestone-2 requirement is a **guest double-draw**
  producing two DISTINCT source images (SM3DW `rrSecondDraw` analogue), plus the
  per-eye projection offset in `*(context+0)` at `0x026A5E20`. The protocol
  plumbing (mailbox/ack/markers/token/stamps) is now proven working.

### 8.6 SM3DW pose-marker protocol, exact (for the MK8 port)
- `mrBeforeCalc` (patch_vr.asm @ rrBeforeCalc, ~line 143): seqlock-reads the
  `CTPH` header: `header+8` (word2) must be EVEN and nonzero, `header+12`
  (word3) == 1 (valid); then `lwsync` and copies `header+24..` into
  `rrPoseLatch0 + slot*196` (word k of header+24 -> latch word 1+(k)). The wire
  token ends up at `latch+164/168` because header word 46/47 = byte 184/188.
- `rrCopyMarker` (@471) reads the token from `rrPoseLatch0+slot*196+164/168`
  (`lfs f3,164(r12) / lfs f4,168(r12)`), gated by `latch+0 != 0` (else emits
  zero token). f1/f2 = `rrPoseMarkerMagic` (.1875/.8125), or
  `rrMenuMetadataMagic` (.21875) when `rrMenuCameraUsed[...] != 0`.
- Sequence: save f1..f4 to a sub-frame -> `GX2ClearColor` (pose metadata) with
  r3 = source -> restore f1..f4 -> `GX2ClearColor` (eye marker). Both clears on
  the SAME buffer back-to-back, no flush between. MK8 `mkCopyMarker` mirrors this.
- `ReferencePoseHistory::pair(left,right,lT,rT)` (`reference_pose_history.h:65`)
  requires `left==right && leftToken==rightToken` -> BOTH eyes carry the SAME
  token+publication in a frame (the host publishes one mailbox per frame).
- `decodeReferencePoseToken` (`reference_marker.h:13`): `v[0]==.1875 && v[1]==.8125`
  (or UNORM8 47/255,207/255) then `token=floor(v[2]*255)*256+floor(v[3]*255)`;
  menu case `v[0]==.21875` -> token=0x10000.

### 8.7 Double-draw RE pass (Ghidra S20, `re-out2\s20.txt`)
Frame flow `FUN_020A5DB4` (TV path, param_2==0), CORRECTED from section 6:
```
020a5dd4  bl  FUN_024CE59C                 ; camMgr = global+0x2BC
020a5e40..020a5e4c  {mode 5} ; bl FUN_0250E8E8(camMgr+0x2BC, {5})
020a5e68  li r4,1 ; li r5,1 ; bl FUN_020A2290(pts, 1, 1)   ; select scan target 1 (TV)
020a5e88..020a5eb0  loop: bl FUN_0209F8A4(sub-renderable[i])  ; n = param_1+0x54
020a5eb4  lis r12,0x1019 ; lwz r12,-0x7AD0(r12) ; lwz r3,0x44(r12)  ; scanout obj
020a5ec0  bl  FUN_02116B60(scanout+0x44)          ; NOT present! resets 5 bytes @+0x2c
020a5ec4..020a5ed8  FUN_020A5D14 / FUN_020A5D94 (frame teardown)
```
- **`FUN_02116B60` is NOT the present/flip.** It only zeroes 5 bytes
  (`[02116b60,02116b83]`: `li r9,5; ctr-loop stbx 0`) at `param+0x2c` — a scan-target
  state reset. Section 6.1's "present/flip (rrPresent analogue)" label is WRONG.
- **`FUN_020A2290(param_1, target, flag)` = scan-target switch** (`[020a2290,020a250f]`).
  Sets `param_1+0xe8` = index 0..8 by `target` (case 1 -> +0xe8=0 = TV). Calls
  `FUN_020a2268`, and on change `FUN_02117684(scanout+0x44,1)` + `FUN_02116B60`.
  `param_1+0x1b8` = current target. **This is the per-eye scan-target wiring point**
  (SM3DW eye-rect/aspect analogue).
- **`FUN_0250E8E8(camMgr+0x2BC, {mode})` = scene dispatch** (`[0250e8e8,0250e937]`):
  gate `(*(*DAT_101D693C+0x134) & 4)==0` -> skip; else set `param_1+0xc4 = *mode`
  and `bl FUN_0250E74C` (the calc+record+deferred-submit chain). **`FUN_0250E74C`
  is where the scene is calculated, recorded and submitted.**
- `FUN_0209F8A4(sub)` ([0209f8a4,0209f8cb], the simple entry) just stamps
  `+0xc=+0x4=+0x8=-1, +0x10=DAT_1000DAE4, +0x14=0` (draw-state reset per
  sub-renderable).
- `DAT_10188530` is the display/scanout manager global; `+0x44` indexes the active
  scanout object. `DAT_10188530+0x124` (used by teardown `FUN_020A5D14`) is a list
  of 4 scanout contexts (per-target rects/ids).

**Design implication for the double-draw (still UNPROVEN, needs care):**
- Candidate A (re-dispatch): at `0x020A5EB4`, emit `GX2DrawDone`, set eye=1, then
  re-run `{mode 5}; bl FUN_0250E8E8(camMgr+0x2BC,...)` + `FUN_020A2290(pts,1,1)` +
  the sub-renderable loop, then let the original `FUN_02116B60`/teardown run. Risk:
  `FUN_0250E74C` may include the **calc** (would double-simulate). MUST verify the
  calc/record split in `FUN_0250E74C` before hooking.
- Candidate B (per-pass matrix swap in `FUN_026A5D98`): cheapest, but changes the
  projection of the SAME single image -> layer still sees ONE image
  (`left==right`), so it cannot alone yield stereo. Only useful **together with**
  a second distinct buffer.
- Candidate C (submit funnel `GX2CallDisplayList` @ `0x0269E578`): double-submit a
  re-recorded list; needs the second target wired via `FUN_020A2290`.
- **Open blocker:** the actual `GX2CopyColorBufferToScanBuffer` (our marker site)
  is reached inside `FUN_0250E74C`; the two eyes need TWO different color buffers
  so the two copies differ. Verify whether MK8 already renders TV+DRC to separate
  buffers (it does: mode 5 TV vs mode 3 DRC) — the double-draw could reuse the
  DRC buffer as the second eye with a per-eye projection + per-eye marker.

### 8.8 CORRECTION: the mode-switch chain is SETUP ONLY, not calc/record/submit
Ghidra S22-S24 (`re-out2\s22.txt`,`s23.txt`,`s24.txt`) resolved the whole chain and
**overturned section 6.1's "calc+record+deferred submit" label** for `FUN_0250E74C`:
- `FUN_0250E74C` `[0250e74c,0250e7b3]` = lock `FUN_0274C444(+0xcc)`; if `+0xc4`
  (mode) != 0 call `FUN_0250E41C(param_1,{mode})`, clear `+0xc4`; unlock. (= tiny.)
- `FUN_0250E41C` `[0250e41c,0250e74b]` = **mode switch, SETUP ONLY** (34 calls, all
  descriptor/camera helpers, NO GX2 record/submit):
  - case 5 (TV) and case 4 (DRC) are near-identical: set `+0xbc/+0xc0` (target mode
    flags), then `FUN_0250DD74`/`FUN_0250DD30` to write 2 target descriptor slots,
    `FUN_025487B8(1,0)`, `FUN_0250DAC4`, `FUN_0250E364`.
  - `FUN_0250DD74` `[0250dd74,0250de07]` = **descriptor writer**: writes
    `camMgr+0x24+idx*0x18` (+0xC, +0x10, +0x14) then `FUN_0250DCEC`. 1 call.
  - `FUN_0250DD30` = same, one field. `FUN_0250DCEC` = descriptor write -> `FUN_0250DAC4`.
  - `FUN_0250DAC4` `[0250dac4,0250dceb]` = **pure index/flag bookkeeping, 0 calls**
    (fills `camMgr+0x9c..` slot order and `+0xb0..` flag bytes). No GX2.
  - `FUN_0250E364` `[0250e364,0250e41b]` = **camera/viewport/aux module refresh**
    (calls `FUN_024B0060` = per-frame camera update, `FUN_0207ED58`, `FUN_020A61C4`,
    `FUN_0218F78C`, `FUN_022C74E4`, `FUN_02101B68`, `FUN_020EADB4`, `FUN_02043B54`
    -> `FUN_02044BAC`). No scene record/submit.
- **Therefore the scene is NOT drawn by `FUN_0250E8E8`/`FUN_0250E74C`.** The TV
  draw in `FUN_020A5DB4` is the **sub-renderable loop** `FUN_0209F8A4(...)` and,
  beneath it, the **pass record driver `FUN_026A5D98`** (our hook) whose record
  bodies submit via `GX2CallDisplayList` @ `0x0269E578` (section 6.2).

**Revised double-draw design (next RE target):**
- MK8 renders the SAME scene per frame to **multiple scan targets** (TV mode 5 ->
  targets 0,1; DRC mode 3) through the SAME pass system with **per-target camera
  descriptors** (`camMgr+0x24+idx*0x18`, `+0x14` = scene-view id) and per-camera
  matrices.
- **Most promising path:** rather than a naive re-dispatch, give the two scan
  targets (TV buffer / DRC buffer) **different view-projections** so the two
  existing per-target copies become the two eyes. The copy marker already carries
  `target` (1=TV, 4=DRC) -> map TV->eye0, DRC->eye1 in the pack.
- Per-eye projection injection point: the **camera update `FUN_024B0060`** (runs
  from `FUN_0250E364` every frame, per-slot) or the per-pass matrix at
  `*(context+0)` in `FUN_026A5D98` (already proven hookable @ `0x026A5E20`).
- **Still to RE:** where `FUN_026A5D98` is dispatched from (vtable) and how the
  per-scan-target camera matrices are selected, so a per-target offset can be
  applied. And confirm TV/DRC actually use distinct color buffers at copy time.

### 8.9 Per-scan-target camera/viewport state (S25 `re-out2\s25.txt`, S26)
- **`FUN_024B0060(param_1)` = per-frame camera/viewport update** `[024b0060,024b03e7]`,
  called from `FUN_0250E364` (mode setup) every frame. It:
  - reads the slot order `camMgr+0x9c..` (built by `FUN_0250DAC4`) and mode
    `camMgr+0xbc` (0=TV / 1 / 2 ...);
  - writes **per-scan-target slots `param_1+0x890 + idx*0x20`** (+4=slot id,
    +0x18 = camera descriptor ptr from `DAT_101d62fc + idx + mode*0x14`,
    +0x1c/+0x1d/+0x1e/+0x1f = active/shadow/view flags);
  - sets `param_1+0xaf0` = TV/DRC mode (0..3), `+0xaf4/+0xaf5` target flags;
  - then the rect/aspect chain `FUN_024AEE78`, `FUN_024AF330`, `FUN_024AF7B4`,
    `FUN_024AF8F4`, `FUN_024AFD28` (section 6.4). **This is where per-target
    viewport/rect/camera is finalized -> the per-eye projection injection point.**
- `DAT_101d62fc` (per-mode/target camera descriptor table) and `DAT_101d693c`,
  `DAT_101d6944` resolve to **runtime RAM/heap pointers, NOT statically readable**
  in the RPX (S26 `MemoryAccessException`). They must be read live (via the layer
  mailbox or a codecave probe), not from static analysis.
- `DAT_1018999c` region IS readable: `+0x10.. = 100C1CAC 100C20D4 100C2128
  100C20E0 100C2134` — pointers (likely the per-slot update modules used by
  `FUN_0250E364`).
- **`FUN_026A5D98` has NO static data refs and NO code refs** -> dispatched via a
  computed call (vtable built at runtime); hence our `0x026A5E20` hook is the only
  way in. The per-pass record context `r28` and `*(context+0)` matrix are proven.

### 8.10 Environment / performance budget (measured)
- The "big computer" is a **VM**: `Win32_Processor` reports **AMD EPYC 7543P but
  only 4 cores / 8 logical** (the host has 32 cores -> we get a 1/8 slice).
  GPUs: **RTX A4500** (workstation, virtualized) + Red Hat QXL (VM console) +
  "Virtual Desktop Monitor". So the real budget is **~4 vCPU + a shared GPU**.
- Cemu alone measured **~240% of one core** (2.4 cores) with the game running.
- Our layer's per-frame added cost is **< 1 ms** (`reference.timing`: acqL/acqR
  ~0.1 ms). The dominant per-frame cost is `totalMs ~97 ms` = XR present +
  guest->XR copy + **VirtualDesktop compositor/stream round-trip**, NOT our code.
- Cemu log had **`GX2DrawdoneSync: true`** (full CPU<->GPU sync on every
  GX2DrawDone) -- a known CPU sink for emulation + our frequent markers. Backed
  up settings and set it **false** (`settings.xml.pre-drawdonesync-backup`).
- Conclusion: in-headset ~10 fps here is **environment-bound** (4-core VM +
  streamed VR), not a defect in the port. Optimisation levers that remain:
  lower the layer's working/eye resolution, lower VD stream bitrate/resolution,
  reduce guest render load via graphics presets (already lightened), and keep
  GX2DrawdoneSync off.

**Net (Milestone-2 double-draw):** two viable implementation routes, both needing
live validation:
  A. **Per-scan-target projection offset** (no double-draw): TV target and DRC
     target already render+copy to separate buffers each frame. Apply a horizontal
     projection offset to ONE target at `FUN_024B0060` (or `*(context+0)` per pass)
     and map TV->eye0 / DRC->eye1 in the pack. Lowest guest-side risk.
  B. **Full SM3DW-style re-draw**: re-run the scene record a second time with a
     per-eye projection + fresh buffer. Highest fidelity, highest risk.

### 8.11 Session log: pair-rejection fix, DrawDoneSync lag fix, pose-token crash
- **Two eye1 markers per frame fixed.** Live `mkdiag.decode` showed, per frame:
  `eye0 1280x720`, `eye1 864x480`, `eye1 854x480` (two DRC copy sites both emit
  eye1). `ReferencePairOrder::observe` rejected the 2nd eye1 and wiped the pair
  (`complete[slot]=0`) -> nothing submitted -> black headset. **Fix:** `observe`
  now treats a repeated eye1 (when a pair already completed, `pending<0`) as a
  duplicate and IGNORES it, and a repeated eye0 at the pending slot as a
  duplicate. Pair now completes (`lifecycle stereo_pair_parameters_known` ->
  `xr_session_ready`).
- **Lag root-caused to the environment, then removed.** `totalMs` was ~97-99 ms
  per frame with `acqL/acqR < 0.2 ms`. Turning **`GX2DrawdoneSync` false** in
  `settings.xml` dropped `totalMs` to **0-1 ms**. Keep it off. (Backup
  `settings.xml.pre-drawdonesync-backup`.)
- **CRASH ROOT CAUSE = the pose-token emit added this session.** Adding a
  `mkPoseTokenClear` (an extra `GX2ClearColor` before each eye marker, reading the
  token from CTPH words 46/47) made Cemu fail-fast (`0xc0000409`,
  `STATUS_STACK_BUFFER_OVERRUN`/`std::terminate`, offset `0xfca125`) within
  seconds of launch, repeatedly. Reverting it restored stability. Earlier
  `0xc0000005` / module "unknown" events are the older `stmw/lmw` codecave
  experiments.
  - **Hypotheses for the crash (to test, one at a time):**
    1. **Guest float state / GX2ClearColor reentrancy**: two back-to-back
       `GX2ClearColor` imports inside the copy wrapper may corrupt GX2 state or
       the guest's floating-point ABI at that point in `FUN_026A5D98`.
    2. **Reading CTPH words 46/47 mid-write**: the layer writes those words every
       frame (seqlock). Reading them non-atomically may yield a torn value used as
       float bits -> a NaN/denormal clear color. Less likely to fail-fast, but
       possible.
    3. **Stack/ABI**: `mkPoseTokenClear` used its own `stwu -0x20` frame nested in
       `mkCopyMarker`'s `-0x20`; combined with the `bl` from a `bla`-entered hook
       the guest stack discipline may differ from SM3DW's inline (non-call) emit.
  - **Next safe approach:** emit the pose-token clear INLINE in `mkCopyMarker`
    (no nested `bl`), or better, reuse the SM3DW pattern exactly (see 8.6:
    `rrCopyMarker` emits the pose clear then the eye clear back-to-back, no
    helper call). Validate with ONE change at a time and relaunch.

### 8.12 Headset freeze root cause: VD render resolution (NOT our layer)
- **Symptom:** "untestable in headset" at any bitrate/graphics setting; image
  stuck on the first MK8 frame; XR session hits `state=5` (STOPPING).
- **Proof our layer is innocent:** `reference.timing` shows the layer submitting
  every frame at `totalMs` mostly 0-1ms, `acqL/acqR <0.2ms`, tracking valid
  (`session.anchor set=1 serial=30`). The freeze is downstream of our submit.
- **Root cause:** `C:\ProgramData\Virtual Desktop\OpenXR.log` logs
  **`Recommended resolution: 2688x2880 (2.000 supersampling, 1.000 upscaling)`**
  -> VD composites/encodes ~15.5 MP/frame on a **virtualized RTX A4500 with a
  4-core VM**, over Wi-Fi. That is the freeze.
- **The setting is CLIENT-PUSHED:** the Quest headset pushes its VD settings to
  the PC. Editing `C:\ProgramData\Virtual Desktop\StreamerSettings.json`
  (`RenderResolutionOverride`, `CodecName`) is **overwritten on VD Streamer
  restart** (observed: file reverted to `2.0` / `HEVC 10-bit`). Must be changed
  **in the headset**: VD in-headset -> Settings -> Streaming -> VR Graphics
  Quality/Resolution = Low, Bitrate ~50 Mbps, Encoding H.264.
- VD Streamer runs as SYSTEM (`VirtualDesktop.Service.exe`, PID 3520 at boot);
  cannot be killed from a non-elevated shell -> restart from tray/`services.msc`
  or reboot. The Streamer process is the one that reads the settings.
- Also lowered the Shadow VM desktop from 4K to 1080p (reduces DWM/compositor +
  VD surface work).

### 8.13 BREAKTHROUGH: the quad/surface layer was the freeze; projection path works
- Setting **`CEMUVR_REFERENCE_UNPOSED_SURFACE=0`** + **`CEMUVR_REFERENCE_POSE_STAMP=0`**
  (in `runtime\launch-mk8-vr.cmd`) forces the layer OUT of the mono **quad/surface**
  layer and INTO the **stereo projection** path (`reference.timing ... surface=0`).
- Result in headset: **completely responsive**, and **two distinct per-eye images**
  now fill the viewport (TV source -> one eye, DRC source -> the other) with the
  near-screen filling the view (proper IPD offset) instead of a distant 2D
  screen. This is exactly the Route-A hardware stereo target.
- **Therefore the prior "freeze/untestable" was the QUAD/`XrCompositionLayerQuad`
  path** (`m_surfaceMode`): VD's runtime did not present our quad layer
  correctly -> stuck image. The **projection** path is what VDXR handles. Do NOT
  ship the surface fallback on VD; keep `CEMUVR_REFERENCE_UNPOSED_SURFACE=0`.
 - Note: the same run still logs some `stereo.pair incomplete ... shouldRender=0
   views=1` (throttled), but frames submit (`submitted=1 surface=0`). The
   remaining defect is **temporal**: the two eyes are not always the SAME frame
   (one eye trails the other), so parallax is present but the stereo pair is not
   frame-locked. That is the next thing to fix (frame-lock eye0/eye1).

### 8.14 THE "come together in the middle" FIX + Route A' dead end + Route B decision
- **THE FIX (`CEMUVR_LAYER_FOV_MILLIDEG`).** Symptom: both eyes showed a live
  image but "too far apart / pulled apart, can't fuse" and head-locked. Cause
  (documented in `xr_core.cpp` ~1021-1045): each eye was given the runtime's
  **native asymmetric** OpenXR fov, so the SAME guest image landed at different
  angular positions per eye -> a fixed angle carrying no depth that pulls the
  eyes apart. Setting **`CEMUVR_LAYER_FOV_MILLIDEG=90000`** (in
  `runtime\launch-mk8-vr.cmd`) gives BOTH eyes the SAME **symmetric** fov. Result
  in headset: **the eyes fuse into ONE coherent image "in the middle"** — MAJOR
  milestone. `layer.fov vertikal=90.000 Grad (symmetrisch, beide Augen gleich)`
  confirms it. Env -> `cfg.layerFovVerticalDeg` (`cemu_layer.cpp:368`), applied in
  `xr_core.cpp:1036` (gated on `!m_explicitRenderedPair`, which holds for MK8
  since we don't stamp a rendered pair: `stampRenderedPair` needs `poseSerial`).
- **Route A (TV-vs-DRC) is DEAD for a live second eye.** Proven via per-site diag
  (`mkdiag.site`): the DRC/GamePad buffer is **not re-rendered per frame on
  Cemu** — the DRC eye was a SINGLE STATIC FRAME. Also established: only
  `0x027AFD58` (TV, tgt=1, buf 0x0E201168) and `0x027AFD78` (DRC, tgt=4, buf
  0x308160D0) fire; **`0x0273951C` never fires**. Decode sees eye0(TV 1280x720) +
  eye1(864x480) + eye1(854x480) = 3 distinct tracked images (a spurious/extra
  marker shape also decodes).
- **Route A' (whole-image horizontal shift) is DEAD.** Emitting eye0+eye1 at the
  TV site (same live buffer) and blitting eye1 with a pixel disparity gives two
  LIVE eyes but **NOT real parallax** — a uniform image shift moves everything by
  the same amount; near and far objects do not separate -> "gobbledy gook". Also
  learned: `vkCmdBlitImage` **forbids negative src OR dst offsets**
  (negative -> `VK_ERROR_DEVICE_LOST`, "failed to submit command buffer. Error
  -4"). Fixed blit reads src[|d|..w] -> dst[0..w-|d|] (pure shift, aspect-
  preserving). This experiment is retained behind `CEMUVR_DISPARITY_PX` (0 =
  off) in `reference_pair.h::record`.
- **Head-lock ("attached to the headset", can't see screen corners).** In
  projection mode the guest image fills a fixed fov tied to gaze, so it rotates
  with the head. `CEMUVR_BILDPOSE=0` (use live `m_views[e].pose` instead of the
  guest-image-bound stamp) did NOT change it. A flat full-fov image cannot be
  "looked around"; that needs either a world-locked QUAD (surface mode, which
  froze on VD) or true re-projection into a 3D world.
- **DECISION (user, this session): target the SM3DW-VR paradigm.** (1) Render the
  **3D world with real parallax** (look around a true 3D scene) = **Route B**,
  then (2) let **2D menus/HUD "sit inside"** that 3D space (e.g. a pause menu
  floating mid-scene), not glued to the face. Route A'/image-shift is abandoned;
  Route A is abandoned for the second eye. Next: Route B re-projection (guest TV
  image -> world-locked virtual screen in the projection layer) + per-eye camera
  offset for depth.

### 8.15 HOW SM3DW-VR ACTUALLY WORLD-LOCKS (read of the reference + our fork diff)
- **There is NO shader/mesh/pipeline reprojection anywhere** (glob of
  `*.glsl/vert/frag/comp/spv/hlsl` = zero hits in both trees). The only GPU work
  is `vkCmdCopyImage`/`vkCmdBlitImage`. The runtime composes/reprojects at
  `predictedDisplayTime`; the layer only copies the guest image into an XR
  swapchain and submits composition layers in `XrCore::endFrame()`
  (`xr_core.cpp:958`).
- **World-locked flat screen = `XrCompositionLayerQuad` in STAGE space**:
  `quad.space=m_stageSpace`, `eyeVisibility=BOTH` (mono), pose = `m_surfaceAnchor`
  (room-fixed), size in metres (`xr_core.cpp:977-1002`). Anchor comes from
  `SessionAnchor::surface(distance)` (`session_anchor.h:51-57`): latched once
  after >=30 tracked frames, yaw-only, screen 2 m in front of the initial
  standing position, upright. `setSurface()` at `xr_core.cpp:733-759`.
- **True look-around 3D = the PROJECTION branch** (`xr_core.cpp:1003`) using
  **guest-supplied per-eye poses/fovs** (`m_eyeStamp[e]`, set by
  `stampRenderedPair` -> requires `rendered.poseSerial` non-zero, i.e. the POSE
  TOKEN path). The runtime then timewarps that world camera pose to the current
  head pose -> the world stays put when you turn your head. This is the "3D world
  you can look around" target.
- **Our fork ALREADY contains all the SM3DW surface/pose machinery unchanged**
  (profile.h, session_anchor.h, xr_core.h, profile_host.cpp identical; xr_core.cpp
  differs only by log throttling; cemu_layer.cpp only by added mkdiag + perf
  timing). So we do NOT need to reimplement the mechanism -- we need to DRIVE it
  from the MK8 pack.
- **Consequence for MK8:** MK8 currently has NO pose token -> `tokenL=tokenR=0` ->
  `referenceUnposedSurface(0,0,enabled)` -> surface mode -> quad -> (froze on VD).
  Two independent levers:
  1. **Flat world-locked screen (quad):** re-enable
     `CEMUVR_REFERENCE_UNPOSED_SURFACE=1`. It froze on VD before, but that run was
     ALSO at VD's 2688x2880 (2.0 SS) -- never re-tested at the lighter settings.
     Cheap to retry.
  2. **True 3D look-around (projection + guest poses):** finish the MK8 POSE TOKEN
     emit (the deferred CTPH/`rrCopyMarker` work) so `referencePoseHistory.pair`
     succeeds and `stampRenderedPair` sets `m_explicitRenderedPair=true`, feeding
     the guest view camera pose to the runtime. THIS is the Route B enabler.

### 8.16 MILESTONE 3 ACHIEVED: the MK8 pose-token round-trip is LIVE

The pose-token pipeline now works end to end. Log proof (headset, VD runtime):
`reference.surface generation=35 reason=stereo tokens=1/1`, then every ~120
generations `reference.stamp ... tokens=N/N valid=1 renderSerial=M submitted=1`
and `reference.transport ... copies=11 held=11 submitted=1 rejected=0`.
`m_explicitRenderedPair` is therefore set and the layer submits the guest's
per-eye camera pose to the OpenXR runtime. This is the SM3DW world-lock path,
now driven from the MK8 pack.

**The chain, and the four bugs that blocked it (all fixed this session):**

1. **`reference.pose` was LOG-FILTERED.** `diag.h:62` lists `"reference.pose"` in
   `periodicKind`, so `CVR_INFO("reference.pose",...)` is suppressed unless
   `CEMUVR_DIAG=1`. The mailbox was being found all along
   (`reference.pose.probe ... strictPacket=1`); we just never saw the success
   line. Fix: add `CEMUVR_DIAG=1` to `launch-mk8-vr.cmd`. (DIAGNOSTIC, not a code
   path.)
2. **TOKEN ADDRESS OFF BY TWO WORDS.** The pack's `mkPoseTokLo/Hi` sat at header
   words **44/45**, but the host writes payload[43]/[44] -> header words **46/47**
   (`cemu_layer.cpp` `memcpy(packet+12,payload,...)`; word = 3 + payload index).
   Guest read zeros -> token 0 -> mono surface. Fix: pad the CTPH header so
   `mkPoseTokLo`=word 46, `mkPoseTokHi`=word 47.
3. **`CEMUVR_REFERENCE_POSE_STAMP=0` disabled the HOST pose decode.**
   `cemu_layer.cpp:666` gates pose-token decode on this flag; the launch script
   had it 0. The guest emitted the pose clear but it fell through to
   `CmdClearColorImage` and was never decoded. Fix: set it to 1.
4. **EYE1 HAD NO TOKEN (tokens=1/0).** The layer keys pose tokens by command
   buffer (`referencePoseMarkers[cb]`) and **erases** the entry when a marker is
   decoded (`cemu_layer.cpp:699`). One leading pose clear therefore fed eye0 only;
   eye1 found nothing -> `tokens=1/0` -> `referencePoseHistory.pair` rejected.
   Fix: emit the pose clear **before EACH eye marker** (pose -> eye0 -> pose ->
   eye1), so both eyes present the same token -> `tokens=1/1`.

**Protocol facts confirmed (MK8, Cemu, VD):**
- The pose clear is a plain `GX2ClearColor(r3=TVbuf, f1..f4)` with RGBA =
  **(.1875, .8125, tokLo, tokHi)**. Cemu quantizes the magic to **47/255**
  (`0x3E3CBCBD`) and **207/255** (`0x3F4FCFD0`) — `decodeReferencePoseToken`
  accepts exactly these (`reference_marker.h:21-22`). The token bytes ride as
  floats `(byte+.25)/255` (host encode, `cemu_layer.cpp:1213`) and decode back via
  `floor(x*255+.001)`.
- The pose clear MUST share the **command buffer** with the eye markers; the eye
  marker pulls the token from `referencePoseMarkers[cb]`.
- The CTPH codecave address **moved again**: `0x01800084` -> `0x0180008C` (and
  earlier `0x01800000`). The layer's signature scan handles this; never hardcode.
- The eye markers themselves carry **no** token (RGBA = target, A, B, slot); the
  token is a SEPARATE clear that precedes them.

## 9. The per-pass record: projection at +0, CAMERA at +0x40 (runtime-proven)

Live `mkdiag` dumps from a running MK8 session (VDXR, Quest 3) settle what
section 6 only guessed. `r28` (record context) points at a struct whose first
16 words are the PROJECTION and whose next 16 words are CAMERA STATE.

### 9.1 `context+0` is the projection, constant, and NOT the camera
```
mkdiag.hit   count=44      renderable=3E5C86D4 context=3A30E3A4 eye=1 pass=1
mkdiag.hit   count=316178  renderable=3D7A09D0 context=3A30E3A4 eye=1 pass=1
mkdiag.mat row=0 3FADD2C8 00000000 00000000 00000000   -> 1.35797, 0, 0, 0
mkdiag.mat row=1 00000000 401A8279 00000000 00000000   -> 0, 2.41433, 0, 0
mkdiag.mat row=2 00000000 00000000 BF80068E 4458D298   -> 0, 0, -1.00019, 866.29
mkdiag.mat row=3 00000000 00000000 BF800000 4459477B   -> 0, 0, -1.0, 869.12
```
- Rows 0/1 are pure diagonal and 2.41433/1.35797 = 1.77778 = 16:9, so these are
  the projection FOV/aspect terms; the -1 at row3 col2 is the w = -z divide.
- **No camera translation anywhere**, and byte-identical across 316k firings.
- Therefore a view matrix is NOT here, and section 6.5 #2 ("patch the 16 words
  at context+0, IPD-offset matrix per eye") is a DEAD END. Confirmed the hard
  way: hooking its upload at 0x026A5E98 (`bla mkProjUpload` replacing
  `bl FUN_02695674`) makes Cemu fail fast with 0xc0000005 (AV at
  Cemu.exe+0x2cd046) within the first frames. That hook is NOT armed.

### 9.2 `context+0x40` is the camera, and it moves every frame
```
mkdiag.mat2 row=0 3F800000 00000000 00000000 00000000   -> 1, 0, 0, 0
mkdiag.mat2 row=1 00000000 3F800000 00000000 00000000   -> 0, 1, 0, 0
mkdiag.mat2 row=2 00000000 00000000 3F800000 00000000   -> 0, 0, 1, 0
mkdiag.mat2 row=3 tx       ty       00000000 w          -> the moving part
```
Sampled per-frame (count 232/249/266/283/300), row3 column 0/1:
```
tx: 0.075  0.150  0.225  0.300  0.375     (+0.075 per frame)
ty: -0.195 -0.390 -0.585 -0.780 -0.973    (-0.195 per frame)
```
- Fixed direction (tx/ty = -0.3846) with magnitude growing LINEARLY
  (0.209 -> 0.418 -> 0.627 -> 0.836 -> 1.044): a camera moving at constant
  velocity, i.e. the kart driving in a straight line.
- Rows 0..2 are identity, so the block carries the camera POSITION but no
  rotation. Rotation is therefore NOT beside the projection; it lives with the
  per-object/per-pass model matrices (camera-relative rendering).

### 9.3 Consequences for the head-look (6DOF / head on a swivel)
- **Positional 6DOF is directly reachable**: offset the translation in
  `context+0x40` row 3 by the head delta (x/y from the CTPH mailbox).
- **Rotational look-around still needs the view/model rotation**, which this
  record does not hold. Next target: the per-draw model-view upload inside the
  record bodies (FUN_026A4700/5158/53E0), or the camera object matrix that
  section 6.4 flagged at `camera+0xD0` (20-word 4x4+row copy in FUN_025D7884
  around 0x025d87e8).
- **Do not mutate `context+0x40` inside the 0x026A5E20 hook**: that hook fires
  once per PASS (hundreds of thousands of times per frame), so an in-place
  rotation accumulates catastrophically. Use a copy-swap at the upload site.

### 9.4 Tooling added this session
- `mkPoseRead` + `mkPoseLatch0` in the pack: seqlock latch of CTPH words 6..53
  (per-eye 3x4, projection, token, head position), called from mkCopyMarker.
  `tools/test_pose_latch.py` runs it (and `mkHeadLook`) against synthetic memory
  offline: 28 checks pass, no Cemu or headset needed.
- `mkHeadLook` / `mkProjUpload` / `mkHeadLookEnable` (flow word 26) exist but are
  NOT armed: they rotate `context+0`, which section 9.1 proves is the projection.
- The layer writes `mkHeadLookEnable` from `CEMUVR_MK8_HEADLOOK`; the diag
  producer hook at 0x026A5E20 is armed and now also dumps `context+0x40`.

## 10. THE ARCHITECTURE WE WERE MISSING (BetterVR) - hook the camera accessor

Reading `ref\bettervr\BotW-BetterVR-main` (the third reference) settles the
approach. BetterVR never edits a matrix in the render path. It hooks the
game's camera/projection ACCESSORS and hands the renderer a per-eye object:

- `resources\BreathOfTheWild_BetterVR\patch_RND_StereoRendering_CameraAndProjection.asm`
    - `0x03AE4AA0 = ba agl__lyr__Layer__getRenderCamera`
    - `0x03AE4AEC = ba agl__lyr__Layer__getRenderProjection`
    - each returns `modifiedCopy_seadLookAtCamera` /
      `modifiedCopy_seadPerspectiveProjection` when `currentEyeSide` is set
    - the modified look-at camera is a 4x4 `mtx` + pos + at + up (20 words); the
      modified projection is mtx + deviceMtx + near/far/angle/fovy*/aspect/offsetXY
    - it ALSO patches the light pre-pass projection separately
- `src\hooking\camera.cpp` `CemuHooks::hook_GetRenderCamera` (line 757) is the
  semantics to port:
    1. read the game camera struct from `cameraIn` (gpr[3])
    2. `ResolveGameplayBasePose`: invert the camera view matrix -> world pos/rot
    3. `BuildGameplayCameraPose(pos, rot, side)`:
         basePos = anchor(gameplayPos); baseYaw = ResolveCameraBaseYaw(gameplayRot)
         newPos = basePos + baseYaw * eyePos
         newRot = baseYaw * eyeRot          // eyePos/eyeRot = LIVE headset eye pose
       i.e. VR view = GAME camera pose COMPOSED WITH THE LIVE HEADSET POSE.
       That composition is what makes head-on-a-swivel / 6DOF real, and it is why
       editing the projection at the pass upload could never produce it.
    4. rebuild the view matrix, set pos/at/up, write to `cameraOut` (gpr[12]),
       return cameraOut in r3
  `hook_GetRenderProjection` does the same for the projection, giving the headset
  its own asymmetric per-eye FOV (with a symmetric fallback - the same trick as
  our CEMUVR_LAYER_FOV_MILLIDEG).

### 10.1 What this means for MK8
- Target: MK8's camera-accessor analogue, NOT the pass matrix. Section 6.4 already
  flagged the candidate: a 20-word (4x4 + row) camera-matrix copy at
  `*(*(FUN_023ABBE8()+0x118)+0x40)[cam]+0xD0` in `FUN_025D7884` around 0x025d87e8.
  That layout (matrix + pos/at/up) matches `sead::LookAtCamera` exactly, so the
  source camera object is very likely there.
- Injection: hook that site, compute the world pose from the game camera, compose
  with the live headset pose from the CTPH mailbox (mkPoseLatch0), write the
  modified camera into the pack's own copy, and let the renderer consume it.
- Caveat: our MK8 layer is the SM3DW-style Vulkan layer (no Cemu C-hook API like
  BetterVR's `import.coreinit.hook_*`), so the composition must be done in PPC
  asm in the pack, the way SM3DW-VR does it (`mtProjection*`, `mrLookCos/Sin`),
  not in C++.
- Do NOT repeat the earlier mistake: the pass record at `context+0` is the
  PROJECTION (section 9.1), and hooking its upload crashed Cemu.

### 10.2 Reference inventory (the three repos)
- `ref\bettervr\BotW-BetterVR-main`   - full shipped VR mod; camera-accessor hooks
  + shader patches; the semantics reference (camera.cpp, patch_RND_StereoRendering_*).
- `ref\sm3dw-vr\SuperMario3DWorld-VR-main` - our fork's base; PPC-asm per-eye
  projection + marker protocol; the implementation-style reference (262 KB pack).
- `ref\graphicpacks\cemu_graphic_packs-master` - official Cemu packs; source of the
  known-good v4.2 addresses and pack idioms.

## 11. MK8 CAMERA OBJECT CONFIRMED: [camera]+0xD0, 20-word block -> renderer

Ghidra verification (scripts S28/S29, output `work\re-out2\s29.txt`).

### 11.1 Use the right program (this cost a pass)
- Project `D:\mk8-VR\ghidra-proj`, project name `mk8`, contains TWO programs.
  The ANALYZED one is **`/Turbo_v42_plain.rpx`**, not `/Turbo_v42_fixed.rpx`.
  `_fixed` has `.text` = 0x02000020..0x02557737 (truncated, no functions, every
  lookup NULL); `_plain` has `.text` = **0x02000020..0x02B020FB**, which is what
  the RE addresses assume. Always target `_plain`:
  `analyzeHeadless D:\mk8-VR\ghidra-proj mk8 -process Turbo_v42_plain.rpx -noanalysis -scriptPath D:\mk8-VR\work\ghidra-scripts -postScript <S>.java`
- The project has almost no pre-defined functions, so a script must
  `disassemble()` + `createFunction()` at each address before decompiling
  (`getFunctionContaining` returns null otherwise).

### 11.2 `FUN_025D7884` `[025d7884, 025d8f83]` - the camera-matrix handoff
```
025d87e8  bl 0x023abbe8                     ; r3 = (something global)
025d87f4  lwz r10,0x118(r3)
025d8808  lwz r6,0x3c(r10)                  ; count
025d8814  lwz r4,0x40(r10)                  ; array base
025d8834  rlwinm r7,r12,0x2,0x0,0x1d        ; index*4
025d8838  add r4,r4,r7
025d8840  lwz r5,0x0(r4)                    ; r5 = CAMERA OBJECT
025d8844  addi r4,r1,0x40                   ; stack destination
025d8848  mtspr CTR,r0                      ; r0 = 5
025d884c  addi r12,r5,0xd0                  ; *** r12 = camera+0xD0 ***
025d8850  lwz r11,0x4(r12) / stw r11,0x4(r4)
025d8858  lwz r6,0x8(r12)  / stw r6,0x8(r4)
025d8860  lwz r7,0xc(r12)  / stw r7,0xc(r4)
025d8868  lwzu r8,0x10(r12) / stwu r8,0x10(r4)
025d8870  bdnz 0x025d8850                   ; 5 iterations = 20 words
025d8874  addi r4,r1,0x44                   ; block now at r1+0x44
025d887c  bl 0x02566c7c(r23, r1+0x44)       ; -> renderer
```
- Reads **20 words from camera+0xD4..camera+0x120** and copies them to a STACK
  LOCAL (`r1+0x44`), then hands that block to `FUN_02566c7c`. This is exactly the
  "20-word (4x4+row) camera matrix" of section 6.4, now confirmed by disassembly.
- A stack-local handoff is the friendliest possible injection point: the block is
  already a private copy, so nothing else needs to be preserved.

### 11.3 `FUN_024B0060` `[024b0060, 024b03e7]` decompiles exactly as documented
- fills the per-scan-target slots `param_1+0x890+idx*0x20` (+0x18 camera
  descriptor from `DAT_101d62fc + idx + mode*0x14`, +0x1C..+0x1F flags);
- sets `param_1+0xAF0` = TV/DRC mode (0 or 1), `+0xAF4/+0xAF5` target flags;
- then runs the rect/aspect chain in order: `FUN_024aee78`, `FUN_024af330`,
  `FUN_024af7b4`, `FUN_024af8f4`, `FUN_024afd28`. No GX2 record/submit.
- Confirms sections 6.4 and 8.9; this is viewport/rect/aspect, NOT the view matrix.

### 11.4 Next step (port BetterVR semantics to this site)
1. Hook the copy result: after `0x025d8874`, or wrap `bl 0x02566c7c`, so the
   renderer is handed a camera block we control.
2. Compose: newView = gameCamera composed with the live headset pose from the
   CTPH mailbox (`mkPoseLatch0`, already latched by `mkPoseRead`). That is the
   BetterVR `hook_GetRenderCamera` semantics (camera.cpp:757).
3. Keep it per-eye if the site runs per target; otherwise drive eye0/eye1 by
   alternating or by the DRC pass.
4. Do NOT touch `context+0` (projection, section 9.1) or the `0x026A5E98` upload.

## 12. The camera-block FUNNEL: three call sites of FUN_02566c7c

Section 11 armed one call site; it never fired. Ghidra S31/S32 found why, and
the correct (funnel) hook target.

### 12.1 [cameraObj]+0xD0 is read in THREE places, all identical
All three copy the same 20 words (`+0xD4..+0x120`) from the camera object into a
PRIVATE STACK LOCAL, then call the same consumer:
```
FUN_025D7884 case r4==0x1d   025d884c addi r12,r5,0xd0 -> r1+0x44 ; 025d887c bl 0x02566c7c
FUN_025d8f84 (per-slot loop)  025d915c addi r11,r9,0xd0 -> r1+0x08 ; 025d9190 bl 0x02566c7c
FUN_025d8f84 (second path)    025d9368 addi r11,r1+..   -> r1+0x58 ; 025d9398 bl 0x02566c7c
```
- Each loop is 5 x 4 words (`lwzu rN,0x10(r11)` / `stwu rN,0x10(r12)` / `bdnz`).
- The live scene uses **FUN_025d8f84** (a per-slot loop, `param_1+0x38c` = slot
  count, `param_1+0x3b8` = slot array), NOT the `0x1d` case of FUN_025D7884.
  That is exactly why the section-11 hook at 0x025D887C never ran.

### 12.2 FUN_02566c7c is the consumer (the BetterVR "accessor" analogue)
`void FUN_02566c7c(uint slot, int cameraBlock)` - it enqueues a render entry of
type `0x1d` carrying the camera block as payload:
```
local_18[0] = 0x1d;
iVar3 = FUN_023abbe8();
uVar6 = FUN_023aafdc(*(iVar3 + 0x118), param_2);
FUN_0256684c(uVar2, local_18, uVar5, uVar6);
```
Both the dispatcher case and the per-slot loop reach the same final call, so this
is the funnel. Hooking its ENTRY would need a trampoline (the entry would
re-enter the hook); hooking the three CALL SITES is simpler and equally complete,
because each passes a writable stack block in r4 and the slot in r3.

### 12.3 Armed now
```
0x025D887C = bla mkCameraSubmit     ; FUN_025D7884 case 0x1d
0x025D9190 = bla mkCameraSubmit     ; FUN_025d8f84 first path
0x025D9398 = bla mkCameraSubmit     ; FUN_025d8f84 second path
0x02566c7c = mkCameraConsumeNative  ; the real consumer, called by the wrapper
```
The wrapper dumps the game matrix (`mkdiag.cam`), rotates the leading 4x4 by the
latched headset rotation (right multiply, row-vector), and passes the block on.
It only clobbers volatile registers, exactly as the replaced `bl` did.

### 12.4 Verification tooling: probe the patched bytes
The layer logs `camhook@025D887C=<8 bytes>` in its mkdiag.probe line so "never
patched" and "never reached" can be told apart. `ba 0x018013B4` for
0x025D887C confirmed the section-11 hook WAS installed; it simply was not reached.
The same probe should be extended to 0x025D9190 / 0x025D9398 before the next run.

### 12.5 Ghidra gotcha (repeat)
Use `/Turbo_v42_plain.rpx`, never `_fixed`. The project has almost no functions,
so scripts must `disassemble(addr)` + `createFunction(addr)` first. Also do NOT
create a function mid-body of another (an earlier pass created FUN_025d8780
inside FUN_025D7884 and made the decompile disagree with the disassembly).

## 13. THE [camera]+0xD0 SUBSYSTEM IS DEAD IN THE LIVE RENDERER (measured)

Sections 11 and 12 built and armed a camera substitution on FUN_025d8f84 /
FUN_025D7884 -> FUN_02566c7c. It never runs. This is now MEASURED, not inferred.

### 13.1 Why the earlier evidence could not answer this
- `mkdiag.cam` only logs when the dumped matrix CHANGES, and readMkDiag returns
  early unless the pass counter moved. So `mkdiag.cam == 0` conflated
  "never entered" with "entered but stable/not sampled".
- Fix: `mkCameraCalls` is incremented INSIDE mkCameraSubmit, and the layer reads
  it (MKM1 word 55) before any gating. Same pattern for the entry counters below
  (words 55/56/57).

### 13.2 The measurement (live session, headset connected, race running)
```
mkdiag.camcalls  n=0     ; FUN_02566c7c never called at any of the 3 patched sites
mkdiag.camloop   n=0     ; FUN_025d8f84 never ENTERED at all
reference.stamp generation=2880 tokens=2846/2846 valid=1 submitted=1
```
The game was demonstrably rendering a stereo race with live pose tokens while
both counters stayed at zero for the whole session.
- All three hooks are provably installed: `camhook@025D887C`, `camA@025D9190`,
  `camB@025D9398` all read `49 80 13 EF` = `ba 0x018013EC` (mkCameraSubmit).
- `FUN_025D7884` and `FUN_025d8f84` have NO static callers (vtable dispatched).
  They are simply not dispatched by the live renderer.

### 13.3 Consequence
- **Do not pursue `[cameraObj]+0xD0` any further.** It is a dormant code path.
  Section 11.2/12.1 artefacts (S29/S31/S32 disassembly) remain correct, but they
  describe code MK8 does not execute during play.
- The armed mkCameraSubmit hooks are harmless (they never fire) and can stay
  while we find the real path; they cost nothing.
- The live camera path is the per-frame update FUN_024B0060 (see 13.4), which is
  the one the documentation already identified and which we can now measure.

### 13.4 FUN_024B0060 prologue (for a safe entry counter)
```
024b0060  mfspr r0,LR          <- replaced by `ba mkCamUpdateCount`
024b0064  stwu r1,-0x50(r1)    <- trampoline branches here
024b0068  stw r0,0x54(r1)
024b006c  stmw r21,0x24(r1)
024b0070  or r31,r3,r3         ; r3 = param_1
024b0074  bl 0x024ce59c        ; camera manager, then +0x2bc
```
The trampoline replays `mfspr r0,LR` (0x7C0802A6) and branches to 0x024b0064;
`ba` does not touch LR, so the callee still sees the caller's return address.
It clobbers only r11/r12, which are volatile at a function entry, and leaves r3
(the argument) intact.

### 13.5 Next
- Confirm `mkdiag.camupdate` increments every frame (proves the live path).
- Then dump the per-slot camera descriptors it fills at
  `param_1 + 0x890 + idx*0x20` (+0x18 = descriptor pointer from DAT_101d62fc),
  and follow the descriptor to the actual view matrix.
- Lesson for future passes: never trust a reference list for vtable-dispatched
  code, and never trust a diag count that is gated by a change test.

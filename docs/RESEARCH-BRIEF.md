# MK8 VR reverse-engineering brief

## Goal
Find, in Mario Kart 8 v4.2 (Wii U, `Turbo.rpx`), the PPC hook points needed to port
the SM3DW-VR marker/stereo protocol. Deliver an address list + pseudocode evidence
in `D:\mk8-VR\work\RE-FINDINGS.md`.

## Environment (Windows)
- Ghidra 12.1.4: `D:\ghidra\ghidra_12.1.4_PUBLIC\support\analyzeHeadless.bat`
- JAVA_HOME: `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot` (set in every shell)
- Ghidra project: `-projectPath D:\mk8-VR\ghidra-proj -projectName mk8`
- Program inside project: `/Turbo_v42_fixed.rpx` (import of code section .text at
  0x02000020, size 0x557718; data sections at 0x10000000..; RPL import stub
  sections at 0xc0008040+; language PowerPC:BE:32:e500 — Espresso/750-class).
- Follow-up headless runs (script dir D:\mk8-VR\work\ghidra-scripts):
  `analyzeHeadless D:\mk8-VR\ghidra-proj mk8 -process Turbo_v42_fixed.rpx -noanalysis -scriptPath D:\mk8-VR\work\ghidra-scripts -postScript YourScript.java [args]`
- Prefer Java GhidraScripts (reliable headless). Raw .text fallback:
  `D:\mk8-VR\work\Turbo_v42.text.bin` (base 0x02000020).
- Original dump: `D:\wiiU\MARIO KART 8 [Update] [0005000e1010ec00]\code\Turbo.rpx`
  (do not modify). Cemu log confirmed module checksum 0x9F0A90B7 = v4.2.

## Known facts
- Symbol/string tables in the rpx are obfuscated; no names available. Ghidra
  imported with auto analysis only ~2s — likely few functions; expect to
  disassemble/decompile on demand around addresses of interest.
- Graphic-pack landmark hooks (v4.2, moduleMatches 0x9F0A90B7):
  - `0x024AEF70` — inside projection-matrix calculation. Pack replaces the
    instruction with `bla scaleAspectRatioCalc` (code: fdivs f9,f13,f12;
    scales by aspect ratio). Camera FOV/projection edit point.
  - `0x024AF384` — conditional-skip of a per-frame draw ("60FPS FullDraw" pack
    replaces it with `b .+0x54`, skipping a 0x54-byte branch block). Frame
    loop landmark.
- Data constants patched by packs: 0x100C359C, 0x10121E30 (aspect floats).
- Codecave region used by Cemu graphic packs: 0x01800000-0x01A00000 (RW).

## RPL import call convention (for identifying GX2 calls)
- Sections .imp10.. .imp40 (type 0x80000002) at 0xc0008040-0xc0009bxx are the
  import stubs (16-38 bytes each). Game code calls them via `bl 0xc000xxxx`.
- Sections .rela7/.rela8/.rela9 (SHT_RELA, ~1M entries at file 0x5DF600+) —
  R_PPC_REL24 relocs from call sites to import stubs. Ghidra may or may not
  have applied them; check references.
- No import names available (strtab obfuscated). Identify stubs by:
  1. disassembling stub bodies (they typically encode an ordinal / module id);
  2. cross-referencing call-site patterns and argument setup (r3-r8 before bl);
  3. behavior: find stubs whose callers pass GX2ColorBuffer-like structs.
  Target functions to identify (any of these suffice):
  GX2ClearColor, GX2CopyColorBufferToScanBuffer, GX2DrawDone,
  GX2SetTVBufferPos / GX2CopyColorBuffer, GX2SwapScanBuffers / flip,
  GX2SetViewport / GX2SetScissor, GX2BeginRender/EndRender or
  GX2DrawDone-adjacent flush, GX2Flush.

## What to deliver (write to D:\mk8-VR\work\RE-FINDINGS.md)
1. Frame present flow: function(s) that copy the finished TV color buffer to the
   scan buffer / trigger present, with exact addresses and caller chain from the
   per-frame loop. Include the function that contains 0x024AF384 and what the
   skipped 0x54-byte block does.
2. Projection setup: full pseudocode of the function containing 0x024AEF70,
   where the projection matrix is stored (matrix base register/object), and how
   to offset the projection center per-eye (which float words to patch).
3. Camera/view matrix: where the per-frame view matrix is built for the race
   camera (search for typical patterns: psq_l of floats, matrix multiply calls,
   or references near the projection function).
4. GX2 clear/copy call sites: every `bl <import stub>` site identifiable as
   GX2ClearColor or GX2CopyColorBufferToScanBuffer, with address + caller.
5. Recommended hook addresses for the MK8 VR pack:
   - where to issue marker clears (like SM3DW rrCopyMarker hooks);
   - where a second (right-eye) draw could be injected;
   - where to patch eye-constant eye offsets into projection.
Each claim must cite address + decompiled/disassembled evidence (keep snippets
short). If something cannot be identified, say so explicitly with what was tried.

## Method hints
- Start: disassemble + decompile around 0x024AEF70 and 0x024AF384; walk callers
  upward to the frame loop; find bl targets in 0xc0008000-0xc000A000 range.
- Use headless scripts to: create functions at addresses (CreateFunctionCmd /
  createFunction), run DecompInterface on ranges, list references
  (getReferencesTo) for import stub addresses, dump results to files under
  D:\mk8-VR\work\re-out\.
- Analysis was only 2s — if the program has almost no functions, run a
  discovery pass (set entry points at the anchor addresses + code analysis
  before -postScript, i.e. omit -noanalysis once with -processor already fixed).
- Big binary: avoid dumping whole-program C; query targeted ranges only.

[MarioKart8_VR_v42]
moduleMatches = 0x9F0A90B7
.origin = codecave
; MK8 v4.2 VR flow header. Same CTFL layout as SM3DW-VR so the CEMU VR
; Vulkan layer finds and acknowledges the codecave (writes CTM1 at +24).
mkFlowHeader:
.int 0x4354464C
.int 1
mkEnabled:
.int 1
mkExtraDraws:
.int 0
mkSkipped:
.int 0
mkMarkerMagic:
.int 0x43544D31
mkMarkerAck:
.int 0
mkEye:
.int 0
mkSlot:
.int 0
mkCopyCalls:
.int 0
mkCopyBuffer:
.int 0
mkCopyTarget:
.int 0
mkEmitCount:
.int 0
; Per-target snapshot of the last source color buffer seen at the copy sites,
; so the layer can prove TV (tgt=1) and DRC (tgt=4) use distinct buffers.
mkTVbuf:
.int 0
mkDRCbuf:
.int 0
mkCallerLR:
.int 0
; Per-site diagnostics (independent block, not in the flow header): counters,
; last source buffer and last scan target for each of the three copy hook sites.
mkSiteACount:
.int 0
.int 0
.int 0
mkSiteBuf:
.int 0
.int 0
.int 0
mkSiteTgt:
.int 0
.int 0
.int 0
mkPoseEmitHdr:
.int 0
; Head-look opt-in, written by the layer from CEMUVR_MK8_HEADLOOK (flow word 26).
; 0 = leave the per-pass matrix untouched (default, ships inert).
mkHeadLookEnable:
.int 0
; Menu / 2D-UI flag (flow word 27), written by the layer from CEMUVR_MK8_MENU.
; Non-zero makes the pose clears carry the menu magic (0.21875), which the layer
; decodes as token 0x10000 and presents as a world-locked panel (setSurface,
; 2.31 m wide, 2 m away) instead of a stereo world view.
mkMenuUsed:
.int 0
; Synthetic head-look test gate (flow word 28), written by the layer from
; CEMUVR_MK8_TESTYAW. Non-zero applies a fixed 30 deg yaw to the per-pass
; context+0x40 block ONCE per frame (change-guarded), so the effect is visible
; in 2D without a headset. 0 = byte-identical baseline.
mkTestYaw:
.int 1
mkValueTV:
.int 0x3D000000
mkValueDRC:
.int 0x3E000000
mkValueA:
.int 0x3DFCD6EA
mkValueB:
.int 0x3F7CD6EA
mkValueZero:
.int 0
mkValueOne:
.int 0x3F800000
; Pose-token marker magic floats (decodeReferencePoseToken: v0=.1875, v1=.8125).
mkPoseTok0:
.int 0x3E400000
mkMenuMagic:
.int 0x3E600000
mkPoseTok1:
.int 0x3F500000
mkPoseEmitCount:
.int 0

; ---------------------------------------------------------------------------
; CTPH pose mailbox. The CEMU VR layer scans the codecave range for this
; signature, then publishes the per-frame headset pose + a 16-bit wire token
; into it (words 46/47 carry the token as two encoded floats). Reading the
; token back and emitting it as a pose-marker clear is what flips the layer
; out of mono "surface" mode into real stereo. Layout mirrors SM3DW's
; rrPoseHeader (version 5).
mkPoseHeader:
.int 0x43545048
.int 5
mkPoseSeq:
.int 0
.int 1
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
mkPoseTokLo:
.int 0
mkPoseTokHi:
.int 0
; Pad the mailbox to >= 376 bytes (version 5 payload size). The layer writes up
; to packet+376; undersizing this block would clobber mkCopyMarker below.
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0

; Per-site thunks: each copy hook jumps here so the shared body can record
; which of the three call sites fired (site 0/1/2) for debugging pair mapping.
mkSite0:
li r6, 0
b mkCopyMarker
mkSite1:
li r6, 1
b mkCopyMarker
mkSite2:
li r6, 2
b mkCopyMarker

; Marker wrapper around GX2CopyColorBufferToScanBuffer.
; r3 = source color buffer, r4 = scan target (1=TV, 4=DRC).
; Milestone 1: run the original copy, then emit eye0+eye1 marker clears
; on the source buffer so the layer records it as the current generation
; pair. No pose tokens are stamped -> unposed mono surface in the headset.
mkCopyMarker:
mflr r0
stwu r1, -0x20(r1)
stw r0, 0x24(r1)
stw r3, 0x1C(r1)
stw r4, 0x18(r1)
bl mkPoseRead
; Record which hook site fired (r6 = site 0/1/2) into site counters, and the
; caller LR. Word15 of the flow header carries the last site index.
lis r12, mkCallerLR@ha
addi r12, r12, mkCallerLR@l
stw r6, 0(r12)
lis r12, mkSiteACount@ha
addi r12, r12, mkSiteACount@l
mulli r11, r6, 4
add r12, r12, r11
lwz r11, 0(r12)
addi r11, r11, 1
stw r11, 0(r12)
; Also snapshot this site's source buffer (by site index) so the layer can map
; site -> extent without guessing.
lis r12, mkSiteBuf@ha
addi r12, r12, mkSiteBuf@l
mulli r11, r6, 4
add r12, r12, r11
stw r3, 0(r12)
lis r12, mkSiteTgt@ha
addi r12, r12, mkSiteTgt@l
mulli r11, r6, 4
add r12, r12, r11
stw r4, 0(r12)
lis r12, mkCopyCalls@ha
addi r12, r12, mkCopyCalls@l
lwz r11, 0(r12)
addi r11, r11, 1
stw r11, 0(r12)
stw r3, 4(r12)
stw r4, 8(r12)
; Snapshot this call's source color buffer per scan target, so the layer can
; compare TV (tgt=1) vs DRC (tgt=4) buffers and prove they are distinct.
cmpwi r4, 1
bne mkCopySnapDRC
lis r12, mkTVbuf@ha
addi r12, r12, mkTVbuf@l
stw r3, 0(r12)
b mkCopyAfterSnap
mkCopySnapDRC:
cmpwi r4, 4
bne mkCopyAfterSnap
lis r12, mkDRCbuf@ha
addi r12, r12, mkDRCbuf@l
stw r3, 0(r12)
mkCopyAfterSnap:
bl import.gx2.GX2CopyColorBufferToScanBuffer
; Only emit protocol clears after this process's core acknowledges support.
lis r12, mkMarkerAck@ha
addi r12, r12, mkMarkerAck@l
lwz r11, 0(r12)
lis r10, 0x4354
addi r10, r10, 0x4D31
cmpw r11, r10
bne mkCopyExit
lwz r4, 0x18(r1)
; Route A' stereoscopy: the TV copy runs on the live TV color buffer; the DRC
; copy's buffer is NOT re-rendered per frame on Cemu (static GamePad image), so
; it cannot source a live second eye. Emit both eye markers at the TV site (see
; mkCopyTV) on the same live buffer; the layer derives eye1 by copying the TV
; image with a horizontal disparity shift. The DRC site therefore emits nothing.
cmpwi r4, 1
beq mkCopyTV
b mkCopyExit
mkCopyTV:
; --- Pose-token clear FIRST, on the same live TV buffer and in the same command
; buffer as the eye markers below. The layer keys pose tokens by command buffer
; (referencePoseMarkers[cb]); the eye marker that follows grabs the token from
; this clear. RGBA = (.1875, .8125, tokLo, tokHi); tokLo/tokHi are read straight
; from the CTPH header words the host writes each frame. If no valid token is
; present yet (0/0) the layer rejects it and we fall back to mono surface mode.
bl mkLoadTokMagic
lis r12, mkPoseTok1@ha
addi r12, r12, mkPoseTok1@l
lfs f2, 0(r12)
lis r12, mkPoseTokLo@ha
addi r12, r12, mkPoseTokLo@l
lfs f3, 0(r12)
lis r12, mkPoseTokHi@ha
addi r12, r12, mkPoseTokHi@l
lfs f4, 0(r12)
lwz r3, 0x1C(r1)
lis r9, mkPoseEmitHdr@ha
addi r9, r9, mkPoseEmitHdr@l
lwz r11, 0(r9)
addi r11, r11, 1
stw r11, 0(r9)
bl import.gx2.GX2ClearColor
; --- TV site: emit eye0 marker (A,B) then eye1 marker (B,A) on the SAME live
; TV buffer. The GamePad (DRC) buffer is not re-rendered per frame on Cemu, so
; Route A cannot source a live second eye from it. Instead the layer copies the
; live TV image twice and applies a horizontal disparity shift to eye1. Emitting
; both markers here keeps the pair frame-locked (same buffer, same frame).
lis r12, mkValueTV@ha
addi r12, r12, mkValueTV@l
lfs f1, 0(r12)
lis r12, mkValueA@ha
addi r12, r12, mkValueA@l
lfs f2, 0(r12)
lfs f3, 4(r12)
lis r12, mkValueZero@ha
addi r12, r12, mkValueZero@l
lfs f4, 0(r12)
lwz r3, 0x1C(r1)
lis r9, mkEmitCount@ha
addi r9, r9, mkEmitCount@l
lwz r11, 0(r9)
addi r11, r11, 1
stw r11, 0(r9)
bl import.gx2.GX2ClearColor
; --- Pose-token clear AGAIN before eye1: the layer consumes (erases) the
; command-buffer pose entry when eye0's marker is decoded, so eye1 needs its own
; fresh pose clear to carry the same token. Both eyes must present the same token.
bl mkLoadTokMagic
lis r12, mkPoseTok1@ha
addi r12, r12, mkPoseTok1@l
lfs f2, 0(r12)
lis r12, mkPoseTokLo@ha
addi r12, r12, mkPoseTokLo@l
lfs f3, 0(r12)
lis r12, mkPoseTokHi@ha
addi r12, r12, mkPoseTokHi@l
lfs f4, 0(r12)
lwz r3, 0x1C(r1)
lis r9, mkPoseEmitHdr@ha
addi r9, r9, mkPoseEmitHdr@l
lwz r11, 0(r9)
addi r11, r11, 1
stw r11, 0(r9)
bl import.gx2.GX2ClearColor
; --- eye1 marker (B,A) on the same TV buffer ---
lis r12, mkValueTV@ha
addi r12, r12, mkValueTV@l
lfs f1, 0(r12)
lis r12, mkValueB@ha
addi r12, r12, mkValueB@l
lfs f2, 0(r12)
lis r12, mkValueA@ha
addi r12, r12, mkValueA@l
lfs f3, 0(r12)
lis r12, mkValueZero@ha
addi r12, r12, mkValueZero@l
lfs f4, 0(r12)
lwz r3, 0x1C(r1)
lis r9, mkEmitCount@ha
addi r9, r9, mkEmitCount@l
lwz r11, 0(r9)
addi r11, r11, 1
stw r11, 0(r9)
bl import.gx2.GX2ClearColor
b mkCopyExit
mkCopyExit:
lwz r0, 0x24(r1)
mtlr r0
addi r1, r1, 0x20
blr

; Silent wrapper: runs the original copy only, emits NO markers. Used for the
; second DRC copy site (0x027AFD78) so only ONE eye1 marker is emitted per
; frame. Two eye1 markers would make ReferencePairOrder reject the pair.
mkCopySilent:
mflr r0
stwu r1, -0x10(r1)
stw r0, 0x14(r1)
bl import.gx2.GX2CopyColorBufferToScanBuffer
lwz r0, 0x14(r1)
mtlr r0
addi r1, r1, 0x10
blr

; Wrap every GX2CopyColorBufferToScanBuffer call site (RE-FINDINGS.md 1.2/3).
; Each goes through a per-site thunk that records which site fired.
0x0273951C = bla mkSite0
0x027AFD58 = bla mkSite1
0x027AFD78 = bla mkSite2

; ---------------------------------------------------------------------------
; Diagnostic block "MKM1" (same codecave). The layer scans for it and logs
; the per-pass view-projection matrix from the scene record driver, proving
; the double-draw hook point (RE-FINDINGS.md section 6) executes.
mkDiagHeader:
.int 0x4D4B4D31
.int 1
mkDiagCount:
.int 0
mkDiagRenderable:
.int 0
mkDiagContext:
.int 0
mkDiagEye:
.int 0
mkDiagPass:
.int 0
mkDiagMatrix:
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
mkDiagMatrix2:
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
mkDiagMatrix3:
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
mkCameraCalls:
.int 0
mkCamLoopCalls:
.int 0
mkCamUpdateCalls:
.int 0
mkCamSlots:
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
mkCamStructCalls:
.int 0
mkCamStruct:
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
mkYawApplied:
.int 0
mkYawSnap:
.int 0
.int 0
.int 0
.int 0
mkYawCos:
.float 0.8660254
mkYawSin:
.float 0.5
mkCamMgrInfo:
.int 0
.int 0
.int 0
mkCamMgrWords:
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
mkCamObjA:
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
mkCamObjB:
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
mkCamSlot64:
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0

; Diag hook replaces 0x026A5E20 (lwz r12,0x14(r27)). This runs once per pass.
; r27 = renderable, r28 = record context, r30 = pass index.
;
; Baseline = the version that ran 412k+ hits at 06:53 without crashing.
; Saves/restores only the registers this hook itself clobbers (r27/r28/r30
; live in the record driver are already spilled by its prologue stmw r25).
mkDiagHook:
mflr r0
stwu r1, -0x20(r1)
stw r0, 0x24(r1)
stw r27, 0x1C(r1)
stw r28, 0x18(r1)
stw r30, 0x14(r1)
; The code we return to (0x026A5E24..) uses r11 (= r30*0x31) and r12. Save the
; scratch we clobber so the record driver sees its original register values.
stw r11, 0x10(r1)
stw r10, 0x0C(r1)
stw r9, 0x08(r1)
; Counter + snapshot fields. r27=renderable, r28=context, r30=pass.
lis r9, mkDiagCount@ha
addi r9, r9, mkDiagCount@l
lwz r11, 0(r9)
addi r11, r11, 1
stw r11, 0(r9)
lwz r11, 0x1C(r1)
stw r11, 4(r9)
lwz r11, 0x18(r1)
stw r11, 8(r9)
; eye/target id = renderable+0xC top 2 bits (RE-FINDINGS 6.3); store at word5.
lwz r11, 0x1C(r1)
lwz r11, 0xC(r11)
rlwinm r11, r11, 0x2, 0x1e, 0x1f
stw r11, 12(r9)
lwz r11, 0x14(r1)
stw r11, 16(r9)
; Copy 16 words of *(context+0) (the view-projection matrix) into words 7..22.
; r28 is the record context (spilled at 0x18(r1)). mkDiagMatrix sits at
; r9 + 5*4 (words 7..22 are 5..20 relative to mkDiagCount at word 2).
lwz r10, 0x18(r1)      ; r10 = context
lwz r11, 0(r10)
stw r11, 20(r9)
lwz r11, 4(r10)
stw r11, 24(r9)
lwz r11, 8(r10)
stw r11, 28(r9)
lwz r11, 12(r10)
stw r11, 32(r9)
lwz r11, 16(r10)
stw r11, 36(r9)
lwz r11, 20(r10)
stw r11, 40(r9)
lwz r11, 24(r10)
stw r11, 44(r9)
lwz r11, 28(r10)
stw r11, 48(r9)
lwz r11, 32(r10)
stw r11, 52(r9)
lwz r11, 36(r10)
stw r11, 56(r9)
lwz r11, 40(r10)
stw r11, 60(r9)
lwz r11, 44(r10)
stw r11, 64(r9)
lwz r11, 48(r10)
stw r11, 68(r9)
lwz r11, 52(r10)
stw r11, 72(r9)
lwz r11, 56(r10)
stw r11, 76(r9)
lwz r11, 60(r10)
stw r11, 80(r9)
lwz r11, 64(r10)
stw r11, 84(r9)
lwz r11, 68(r10)
stw r11, 88(r9)
lwz r11, 72(r10)
stw r11, 92(r9)
lwz r11, 76(r10)
stw r11, 96(r9)
lwz r11, 80(r10)
stw r11, 100(r9)
lwz r11, 84(r10)
stw r11, 104(r9)
lwz r11, 88(r10)
stw r11, 108(r9)
lwz r11, 92(r10)
stw r11, 112(r9)
lwz r11, 96(r10)
stw r11, 116(r9)
lwz r11, 100(r10)
stw r11, 120(r9)
lwz r11, 104(r10)
stw r11, 124(r9)
lwz r11, 108(r10)
stw r11, 128(r9)
lwz r11, 112(r10)
stw r11, 132(r9)
lwz r11, 116(r10)
stw r11, 136(r9)
lwz r11, 120(r10)
stw r11, 140(r9)
lwz r11, 124(r10)
stw r11, 144(r9)
; Synthetic-yaw test (see mkApplyTestYaw). r10 is still the context here.
stw r3, 0x04(r1)
mr r3, r10
bl mkApplyTestYaw
lwz r3, 0x04(r1); Call projection hook after snapshot (safe stub for now)
; bl rrProjectionHook
lwz r27, 0x1C(r1)
lwz r28, 0x18(r1)
lwz r30, 0x14(r1)
lwz r11, 0x10(r1)
lwz r10, 0x0C(r1)
lwz r9, 0x08(r1)
lwz r0, 0x24(r1)
mtlr r0
addi r1, r1, 0x20
lwz r12, 0x14(r27)
b 0x026A5E24

; Boot-safety test: hook the matrix producer at 0x026A5E20 only (no eye-id
; hook yet). If this boots, the earlier crash was a missing trampoline return.

; ---------------------------------------------------------------------------
; rrProjectionHook - inject host poses (stub extended later)


; ---------------------------------------------------------------------------
; mkPoseRead - seqlock latch of the host CTPH pose mailbox into mkPoseLatch0.
; Port of SM3DW-VR's rrBeforeCalc latch, driven from the MK8 rendering path
; (mkCopyMarker) so no new boot-path hook is required. Copies mailbox words
; 6..53 (per-eye 3x4 matrices, projection, token, scalars, head position)
; into slot 0 and stores the mailbox sequence in slot word 0, so consumers can
; tell a fresh latch from a stale one. Preserves r3..r12 and LR: mkCopyMarker
; depends on r6 (hook site id) and r3/r4 across this call.
mkPoseRead:
mflr r0
stwu r1, -0x30(r1)
stw r0, 0x34(r1)
stw r3, 0x2C(r1)
stw r4, 0x28(r1)
stw r5, 0x24(r1)
stw r6, 0x20(r1)
stw r7, 0x1C(r1)
stw r8, 0x18(r1)
stw r9, 0x14(r1)
stw r10, 0x10(r1)
stw r11, 0x0C(r1)
stw r12, 0x08(r1)
lis r12, mkPoseLatch0@ha
addi r12, r12, mkPoseLatch0@l
li r11, 0
stw r11, 0(r12)
lis r8, mkPoseHeader@ha
addi r8, r8, mkPoseHeader@l
lwz r7, 8(r8)
andi. r11, r7, 1
bne mkPoseReadExit
cmpwi r7, 0
beq mkPoseReadExit
lwz r11, 12(r8)
cmpwi r11, 1
bne mkPoseReadExit
.int 0x7C2004AC ; lwsync
lwz r11, 24(r8)
stw r11, 4(r12)
lwz r11, 28(r8)
stw r11, 8(r12)
lwz r11, 32(r8)
stw r11, 12(r12)
lwz r11, 36(r8)
stw r11, 16(r12)
lwz r11, 40(r8)
stw r11, 20(r12)
lwz r11, 44(r8)
stw r11, 24(r12)
lwz r11, 48(r8)
stw r11, 28(r12)
lwz r11, 52(r8)
stw r11, 32(r12)
lwz r11, 56(r8)
stw r11, 36(r12)
lwz r11, 60(r8)
stw r11, 40(r12)
lwz r11, 64(r8)
stw r11, 44(r12)
lwz r11, 68(r8)
stw r11, 48(r12)
lwz r11, 72(r8)
stw r11, 52(r12)
lwz r11, 76(r8)
stw r11, 56(r12)
lwz r11, 80(r8)
stw r11, 60(r12)
lwz r11, 84(r8)
stw r11, 64(r12)
lwz r11, 88(r8)
stw r11, 68(r12)
lwz r11, 92(r8)
stw r11, 72(r12)
lwz r11, 96(r8)
stw r11, 76(r12)
lwz r11, 100(r8)
stw r11, 80(r12)
lwz r11, 104(r8)
stw r11, 84(r12)
lwz r11, 108(r8)
stw r11, 88(r12)
lwz r11, 112(r8)
stw r11, 92(r12)
lwz r11, 116(r8)
stw r11, 96(r12)
lwz r11, 120(r8)
stw r11, 100(r12)
lwz r11, 124(r8)
stw r11, 104(r12)
lwz r11, 128(r8)
stw r11, 108(r12)
lwz r11, 132(r8)
stw r11, 112(r12)
lwz r11, 136(r8)
stw r11, 116(r12)
lwz r11, 140(r8)
stw r11, 120(r12)
lwz r11, 144(r8)
stw r11, 124(r12)
lwz r11, 148(r8)
stw r11, 128(r12)
lwz r11, 152(r8)
stw r11, 132(r12)
lwz r11, 156(r8)
stw r11, 136(r12)
lwz r11, 160(r8)
stw r11, 140(r12)
lwz r11, 164(r8)
stw r11, 144(r12)
lwz r11, 168(r8)
stw r11, 148(r12)
lwz r11, 172(r8)
stw r11, 152(r12)
lwz r11, 176(r8)
stw r11, 156(r12)
lwz r11, 180(r8)
stw r11, 160(r12)
lwz r11, 184(r8)
stw r11, 164(r12)
lwz r11, 188(r8)
stw r11, 168(r12)
lwz r11, 192(r8)
stw r11, 172(r12)
lwz r11, 196(r8)
stw r11, 176(r12)
lwz r11, 200(r8)
stw r11, 180(r12)
lwz r11, 204(r8)
stw r11, 184(r12)
lwz r11, 208(r8)
stw r11, 188(r12)
lwz r11, 212(r8)
stw r11, 192(r12)
.int 0x7C2004AC ; lwsync
lwz r11, 8(r8)
cmpw r7, r11
bne mkPoseReadExit
stw r7, 0(r12)
mkPoseReadExit:
lwz r12, 0x08(r1)
lwz r11, 0x0C(r1)
lwz r10, 0x10(r1)
lwz r9, 0x14(r1)
lwz r8, 0x18(r1)
lwz r7, 0x1C(r1)
lwz r6, 0x20(r1)
lwz r5, 0x24(r1)
lwz r4, 0x28(r1)
lwz r3, 0x2C(r1)
lwz r0, 0x34(r1)
mtlr r0
addi r1, r1, 0x30
blr

; Two 196-byte latch slots (only slot 0 is used on MK8 today;
; the second keeps the SM3DW layout so a shared consumer can be ported).
mkPoseLatch0:
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0

; ---------------------------------------------------------------------------
; mkHeadLook - rotate a per-pass 4x4 matrix by the latched headset rotation, in
; VIEW space, so turning your head looks around the world instead of the world
; staying glued to the face. Reads the head rotation from mkPoseLatch0 (the
; left-eye 3x4 the host publishes at latch words 1..9), transposes it and applies
; new_row[i] = SUM_k D[k][i] * old_row[k]; row 3 stays (0,0,0,1). Source and
; destination must differ: every output row reads all three input rows.
; Leaves the matrix untouched unless the layer opted in AND a fresh pose is
; latched, so the pack is inert by default.
;   r3 = source 16-word matrix, r4 = destination 16-word matrix
mkHeadLook:
lis r12, mkHeadLookEnable@ha
addi r12, r12, mkHeadLookEnable@l
lwz r11, 0(r12)
cmpwi r11, 0
beq mkHeadLookDone
lis r12, mkPoseLatch0@ha
addi r12, r12, mkPoseLatch0@l
lwz r11, 0(r12)
cmpwi r11, 0
beq mkHeadLookDone
lwz r11, 48(r3)
stw r11, 48(r4)
lwz r11, 52(r3)
stw r11, 52(r4)
lwz r11, 56(r3)
stw r11, 56(r4)
lwz r11, 60(r3)
stw r11, 60(r4)
lfs f1, 4(r12)
lfs f2, 8(r12)
lfs f3, 12(r12)
lfs f4, 20(r12)
lfs f5, 24(r12)
lfs f6, 28(r12)
lfs f7, 36(r12)
lfs f8, 40(r12)
lfs f9, 44(r12)
lfs f11, 0(r3)
fmuls f10, f1, f11
lfs f11, 16(r3)
fmuls f12, f4, f11
fadds f10, f10, f12
lfs f11, 32(r3)
fmuls f12, f7, f11
fadds f10, f10, f12
stfs f10, 0(r4)
lfs f11, 4(r3)
fmuls f10, f1, f11
lfs f11, 20(r3)
fmuls f12, f4, f11
fadds f10, f10, f12
lfs f11, 36(r3)
fmuls f12, f7, f11
fadds f10, f10, f12
stfs f10, 4(r4)
lfs f11, 8(r3)
fmuls f10, f1, f11
lfs f11, 24(r3)
fmuls f12, f4, f11
fadds f10, f10, f12
lfs f11, 40(r3)
fmuls f12, f7, f11
fadds f10, f10, f12
stfs f10, 8(r4)
lfs f11, 12(r3)
fmuls f10, f1, f11
lfs f11, 28(r3)
fmuls f12, f4, f11
fadds f10, f10, f12
lfs f11, 44(r3)
fmuls f12, f7, f11
fadds f10, f10, f12
stfs f10, 12(r4)
lfs f11, 0(r3)
fmuls f10, f2, f11
lfs f11, 16(r3)
fmuls f12, f5, f11
fadds f10, f10, f12
lfs f11, 32(r3)
fmuls f12, f8, f11
fadds f10, f10, f12
stfs f10, 16(r4)
lfs f11, 4(r3)
fmuls f10, f2, f11
lfs f11, 20(r3)
fmuls f12, f5, f11
fadds f10, f10, f12
lfs f11, 36(r3)
fmuls f12, f8, f11
fadds f10, f10, f12
stfs f10, 20(r4)
lfs f11, 8(r3)
fmuls f10, f2, f11
lfs f11, 24(r3)
fmuls f12, f5, f11
fadds f10, f10, f12
lfs f11, 40(r3)
fmuls f12, f8, f11
fadds f10, f10, f12
stfs f10, 24(r4)
lfs f11, 12(r3)
fmuls f10, f2, f11
lfs f11, 28(r3)
fmuls f12, f5, f11
fadds f10, f10, f12
lfs f11, 44(r3)
fmuls f12, f8, f11
fadds f10, f10, f12
stfs f10, 28(r4)
lfs f11, 0(r3)
fmuls f10, f3, f11
lfs f11, 16(r3)
fmuls f12, f6, f11
fadds f10, f10, f12
lfs f11, 32(r3)
fmuls f12, f9, f11
fadds f10, f10, f12
stfs f10, 32(r4)
lfs f11, 4(r3)
fmuls f10, f3, f11
lfs f11, 20(r3)
fmuls f12, f6, f11
fadds f10, f10, f12
lfs f11, 36(r3)
fmuls f12, f9, f11
fadds f10, f10, f12
stfs f10, 36(r4)
lfs f11, 8(r3)
fmuls f10, f3, f11
lfs f11, 24(r3)
fmuls f12, f6, f11
fadds f10, f10, f12
lfs f11, 40(r3)
fmuls f12, f9, f11
fadds f10, f10, f12
stfs f10, 40(r4)
lfs f11, 12(r3)
fmuls f10, f3, f11
lfs f11, 28(r3)
fmuls f12, f6, f11
fadds f10, f10, f12
lfs f11, 44(r3)
fmuls f12, f9, f11
fadds f10, f10, f12
stfs f10, 44(r4)
mkHeadLookDone:
blr

; ---------------------------------------------------------------------------
; mkProjUpload - replaces the `bl FUN_02695674` at 0x026A5E98, where the per-pass
; 4x4 at context+0 is uploaded as 16 vertex uniform registers. Copies the game's
; matrix to scratch, seeds the result buffer, lets mkHeadLook rotate the copy, then
; uploads the result. context+0 is never written, so nothing accumulates across the
; hundreds of thousands of passes per frame. Only volatile registers are clobbered,
; exactly as the replaced call did.
mkProjUpload:
mflr r0
stwu r1, -0x20(r1)
stw r0, 0x24(r1)
lis r12, mkMatrixScratch@ha
addi r12, r12, mkMatrixScratch@l
lwz r11, 0(r3)
stw r11, 0(r12)
lwz r11, 4(r3)
stw r11, 4(r12)
lwz r11, 8(r3)
stw r11, 8(r12)
lwz r11, 12(r3)
stw r11, 12(r12)
lwz r11, 16(r3)
stw r11, 16(r12)
lwz r11, 20(r3)
stw r11, 20(r12)
lwz r11, 24(r3)
stw r11, 24(r12)
lwz r11, 28(r3)
stw r11, 28(r12)
lwz r11, 32(r3)
stw r11, 32(r12)
lwz r11, 36(r3)
stw r11, 36(r12)
lwz r11, 40(r3)
stw r11, 40(r12)
lwz r11, 44(r3)
stw r11, 44(r12)
lwz r11, 48(r3)
stw r11, 48(r12)
lwz r11, 52(r3)
stw r11, 52(r12)
lwz r11, 56(r3)
stw r11, 56(r12)
lwz r11, 60(r3)
stw r11, 60(r12)
lis r12, mkMatrixResult@ha
addi r12, r12, mkMatrixResult@l
lwz r11, 0(r3)
stw r11, 0(r12)
lwz r11, 4(r3)
stw r11, 4(r12)
lwz r11, 8(r3)
stw r11, 8(r12)
lwz r11, 12(r3)
stw r11, 12(r12)
lwz r11, 16(r3)
stw r11, 16(r12)
lwz r11, 20(r3)
stw r11, 20(r12)
lwz r11, 24(r3)
stw r11, 24(r12)
lwz r11, 28(r3)
stw r11, 28(r12)
lwz r11, 32(r3)
stw r11, 32(r12)
lwz r11, 36(r3)
stw r11, 36(r12)
lwz r11, 40(r3)
stw r11, 40(r12)
lwz r11, 44(r3)
stw r11, 44(r12)
lwz r11, 48(r3)
stw r11, 48(r12)
lwz r11, 52(r3)
stw r11, 52(r12)
lwz r11, 56(r3)
stw r11, 56(r12)
lwz r11, 60(r3)
stw r11, 60(r12)
lis r3, mkMatrixScratch@ha
addi r3, r3, mkMatrixScratch@l
lis r4, mkMatrixResult@ha
addi r4, r4, mkMatrixResult@l
bl mkHeadLook
lis r3, mkMatrixResult@ha
addi r3, r3, mkMatrixResult@l
bl mkProjUploadNative
lwz r0, 0x24(r1)
mtlr r0
addi r1, r1, 0x20
blr

0x02695674 = mkProjUploadNative:

mkMatrixScratch:
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
mkMatrixResult:
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0
.int 0

; Arm the per-pass producer hook (dumps the real matrix to the layer log) and the
; upload wrapper that applies the head-look.
0x026A5E20 = ba mkDiagHook
; NOTE: the mkProjUpload hook is deliberately NOT armed.
; Hooking 0x026A5E98 (the upload of the per-pass matrix at context+0) makes Cemu
; fail with 0xc0000005 during the first frames. The live mkdiag.mat dump shows
; why this was always the wrong place: the 16 words at context+0 are a
; projection-style matrix with ZERO camera translation (rows 0/1 are pure
; diagonal, 1.35797 / 2.41433 = 16:9), so it holds no camera pose to rotate.
; The head rotation must be applied to the VIEW matrix instead. See RE-FINDINGS.

; ---------------------------------------------------------------------------
; Pad input hook (MK8 <-> CEMU VR layer).
;
; MK8 reads the emulated Wii U GamePad from exactly one place: its single
; VPADRead call at vaddr 0x02725720. We let the real VPADRead run, then
; overwrite the returned status with the VR controllers, using the CTPH hand
; words the layer publishes every frame. Because we write the VPAD bits
; ourselves, one button can come from several physical inputs at once
; (Circle OR L2 -> B), so menus and racing need no profile switching.
;
; CTPH hand layout (byte offsets from mkPoseHeader; header word w = byte w*4):
;   left  hand: located +232 (w58)  buttons +284 (w71)  trigger +288  squeeze +292  stickX +296  stickY +300
;   right hand: located +304 (w76)  buttons +356 (w89)  trigger +360  squeeze +364  stickX +368  stickY +372
; kPad bits published by the layer:
;   primary=0x01 secondary=0x02 stick=0x04 menu=0x08 trigger=0x10 squeeze=0x20 blow=0x40
;
; Resulting racing/menu mapping:
;   A + Y  <- right trigger (R2) or Cross   (accelerate / confirm)
;   B      <- left trigger (L2) or Circle   (brake / back)
;   ZL     <- Square or left grip (L1)      (item)
;   ZR     <- Triangle or right grip (R1)   (drift)
;   +      <- menu on either hand
;   steer  <- left thumbstick (plus the 0.5 stick-as-dpad bits Cemu sets)
;
; If neither hand is tracked (or the layer is not publishing yet) the hook
; leaves Cemu's own pad state untouched, so a real gamepad still works.
mkPadHook:
mflr r0
stwu r1, -0x40(r1)
stw r0, 0x44(r1)
stw r3, 0x3C(r1)
stw r4, 0x38(r1)
stw r5, 0x34(r1)
stw r6, 0x30(r1)
; run the real pad read with the caller's arguments (chan, buffers, count, outError)
bl import.vpad.VPADRead
stw r3, 0x2C(r1)
cmpwi r3, 0
ble mkPadExit
; r6 = 1 only when the VR contribution below actually runs
li r6, 0
; only act once the host is publishing a settled frame (word2 = sequence)
lis r12, mkPoseSeq@ha
addi r12, r12, mkPoseSeq@l
lwz r11, 0(r12)
cmpwi r11, 0
beq mkPadNoVr
andi. r11, r11, 1
bne mkPadNoVr
; and at least one hand must be tracked
lis r12, mkPoseHeader@ha
addi r12, r12, mkPoseHeader@l
lwz r11, 232(r12)
lwz r10, 304(r12)
or r11, r11, r10
cmpwi r11, 0
beq mkPadNoVr
li r6, 1
; ---- buttons ----
li r3, 0
lwz r9, 284(r12)
lwz r10, 356(r12)
; A and Y (accelerate) <- analog right trigger above the tuned threshold, or
; Cross (right primary). Reading the analog value lets the panel own the
; threshold instead of the layer's fixed 0.60.
lfs f6, 360(r12)
lis r8, mkTuneAccel@ha
addi r8, r8, mkTuneAccel@l
lfs f0, 0(r8)
fcmpu cr0, f6, f0
bgt mkPadSetAY
andi. r11, r10, 0x01
beq mkPadNoAY
mkPadSetAY:
ori r3, r3, 0x8000
ori r3, r3, 0x1000
mkPadNoAY:
; B (back / brake) <- analog left trigger above the tuned threshold, or Circle
lfs f6, 288(r12)
lis r8, mkTuneBrake@ha
addi r8, r8, mkTuneBrake@l
lfs f0, 0(r8)
fcmpu cr0, f6, f0
bgt mkPadSetB
andi. r11, r10, 0x02
beq mkPadNoB
mkPadSetB:
ori r3, r3, 0x4000
mkPadNoB:
; ZL (item) <- Square (left primary) or left grip (L1)
andi. r11, r9, 0x01
bne mkPadSetZL
andi. r11, r9, 0x20
beq mkPadNoZL
mkPadSetZL:
ori r3, r3, 0x0080
mkPadNoZL:
; ZR (drift) <- Triangle (left secondary) or right grip (R1)
andi. r11, r9, 0x02
bne mkPadSetZR
andi. r11, r10, 0x20
beq mkPadNoZR
mkPadSetZR:
ori r3, r3, 0x0040
mkPadNoZR:
; + (pause) <- menu on either hand
andi. r11, r9, 0x08
bne mkPadSetPlus
andi. r11, r10, 0x08
beq mkPadNoPlus
mkPadSetPlus:
ori r3, r3, 0x0008
mkPadNoPlus:
; ---- left stick: steering + the 0.5 stick-as-dpad bits ----
lfs f1, 296(r12)
lfs f2, 300(r12)
lis r8, mkVpadHalf@ha
addi r8, r8, mkVpadHalf@l
lfs f0, 0(r8)
fcmpu cr0, f1, f0
bge mkPadXRight
lis r8, mkVpadNegHalf@ha
addi r8, r8, mkVpadNegHalf@l
lfs f0, 0(r8)
fcmpu cr0, f1, f0
ble mkPadXLeft
b mkPadXDone
mkPadXRight:
lis r11, 0x2000
or r3, r3, r11
b mkPadXDone
mkPadXLeft:
lis r11, 0x4000
or r3, r3, r11
mkPadXDone:
lis r8, mkVpadHalf@ha
addi r8, r8, mkVpadHalf@l
lfs f0, 0(r8)
fcmpu cr0, f2, f0
bge mkPadYUp
lis r8, mkVpadNegHalf@ha
addi r8, r8, mkVpadNegHalf@l
lfs f0, 0(r8)
fcmpu cr0, f2, f0
ble mkPadYDown
b mkPadYDone
mkPadYUp:
lis r11, 0x1000
or r3, r3, r11
b mkPadYDone
mkPadYDown:
lis r11, 0x0800
or r3, r3, r11
mkPadYDone:
; ---- motion steering: right-hand roll, gated to a zone in front of the user --
; Three gates before any steering is produced:
;   1. SPATIAL FALLOFF -- the wheel only works while the hand is inside a sphere
;      in front of the chest (centre = published head position + a vertical and
;      a forward offset). Authority falls linearly to zero across the shell
;      between zoneInner and zoneOuter, so a hand at your side, behind you or
;      raised produces nothing instead of a control curve you did not ask for.
;   2. ROTATIONAL DEADZONE on the roll itself, for an unsteady hand.
;   3. SLEW LIMIT so a flick cannot jolt the kart to full lock in one frame.
; All three are CTUN parameters, so the panel can widen or tighten them. The
; head reference makes the zone follow the player instead of being pinned to
; wherever the anchor was latched.
lis r8, mkSteerMode@ha
lwz r11, mkSteerMode@l(r8)
cmpwi r11, 0
beq mkSteerDone
lfs f5, 324(r12)
; --- spatial gate: squared radial distance, so no square root is needed ---
lfs f8, 220(r12)
lfs f9, 224(r12)
lis r8, mkTuneZoneVert@ha
addi r8, r8, mkTuneZoneVert@l
lfs f0, 0(r8)
fadds f9, f9, f0
lfs f10, 228(r12)
lis r8, mkTuneZoneFwd@ha
addi r8, r8, mkTuneZoneFwd@l
lfs f0, 0(r8)
fadds f10, f10, f0
lfs f3, 320(r12)
fsubs f3, f3, f8
lfs f4, 336(r12)
fsubs f4, f4, f9
lfs f6, 352(r12)
fsubs f6, f6, f10
fmuls f3, f3, f3
fmuls f4, f4, f4
fmuls f6, f6, f6
fadds f3, f3, f4
fadds f3, f3, f6
lis r8, mkTuneZoneInner@ha
addi r8, r8, mkTuneZoneInner@l
lfs f4, 0(r8)
fmuls f4, f4, f4
lis r8, mkTuneZoneOuter@ha
addi r8, r8, mkTuneZoneOuter@l
lfs f6, 0(r8)
fmuls f6, f6, f6
fsubs f0, f6, f3
fsubs f4, f6, f4
fdivs f0, f0, f4
; clamp the falloff into 0..1
lis r8, mkSteerOne@ha
addi r8, r8, mkSteerOne@l
lfs f3, 0(r8)
fcmpu cr0, f0, f3
ble mkZoneNoHi
fmr f0, f3
mkZoneNoHi:
lis r8, mkSteerZero@ha
addi r8, r8, mkSteerZero@l
lfs f3, 0(r8)
fcmpu cr0, f0, f3
bge mkZoneNoLo
fmr f0, f3
mkZoneNoLo:
fmuls f5, f5, f0
; --- rotational deadzone ---
lis r8, mkTuneSteerDead@ha
addi r8, r8, mkTuneSteerDead@l
lfs f0, 0(r8)
fcmpu cr0, f5, f0
bgt mkSteerActive
lis r8, mkSteerZero@ha
addi r8, r8, mkSteerZero@l
lfs f4, 0(r8)
fsubs f0, f4, f0
fcmpu cr0, f5, f0
blt mkSteerActive
fmr f5, f4
b mkSteerShape
mkSteerActive:
lis r8, mkTuneSteerGain@ha
addi r8, r8, mkTuneSteerGain@l
lfs f0, 0(r8)
fmuls f5, f5, f0
; clamp to +-1
lis r8, mkSteerOne@ha
addi r8, r8, mkSteerOne@l
lfs f0, 0(r8)
fcmpu cr0, f5, f0
ble mkSteerNoHi
fmr f5, f0
mkSteerNoHi:
lis r8, mkSteerNegOne@ha
addi r8, r8, mkSteerNegOne@l
lfs f0, 0(r8)
fcmpu cr0, f5, f0
bge mkSteerNoLo
fmr f5, f0
mkSteerNoLo:
mkSteerShape:
; response curve: y = (1-k)*x + k*x^3 with k = mkTuneSteerCurve
lis r8, mkTuneSteerCurve@ha
addi r8, r8, mkTuneSteerCurve@l
lfs f0, 0(r8)
fmuls f6, f5, f5
fmuls f6, f6, f5
fmuls f6, f6, f0
lis r8, mkSteerOne@ha
addi r8, r8, mkSteerOne@l
lfs f4, 0(r8)
fsubs f4, f4, f0
fmuls f5, f5, f4
fadds f5, f5, f6
; sign: flip if the wheel reads backwards
lis r8, mkTuneSteerSign@ha
addi r8, r8, mkTuneSteerSign@l
lfs f0, 0(r8)
fmuls f5, f5, f0
; slew limit: approach the target by at most mkTuneSteerSlew per frame
lis r8, mkTuneSteerSlew@ha
addi r8, r8, mkTuneSteerSlew@l
lfs f6, 0(r8)
lis r8, mkSteerPrev@ha
addi r8, r8, mkSteerPrev@l
lfs f7, 0(r8)
fsubs f4, f5, f6
fcmpu cr0, f7, f4
blt mkSteerSlewUp
fadds f4, f5, f6
fcmpu cr0, f7, f4
bgt mkSteerSlewDown
b mkSteerSlewDone
mkSteerSlewUp:
fadds f5, f7, f6
b mkSteerSlewDone
mkSteerSlewDown:
fsubs f5, f7, f6
mkSteerSlewDone:
stfs f5, 0(r8)
; ---- chaos: a deliberate, goofy lag when the driver starts to flail ---------
; "Losing control" is read from steering VOLATILITY -- an EMA of |change in
; wheel steer| -- rather than from a game flag. If you are sawing the wheel back
; and forth, the response gains inertia, which amplifies the swerve and makes
; you saw harder. That is the pseudo-challenge: the controller stops being a
; perfect extension of the hand exactly when you want it most. It is capped by
; mkTuneChaosMax and the EMA decays, so it reads as a stumble, never as broken
; controls -- and it is a slider, so it can be dialled to zero.
lis r8, mkChaosPrev@ha
addi r8, r8, mkChaosPrev@l
lfs f7, 0(r8)
stfs f5, 0(r8)
fsubs f6, f5, f7
lis r8, mkSteerZero@ha
addi r8, r8, mkSteerZero@l
lfs f4, 0(r8)
fcmpu cr0, f6, f4
bge mkChaosAbsDone
fsubs f6, f4, f6
mkChaosAbsDone:
lis r8, mkChaosVol@ha
addi r8, r8, mkChaosVol@l
lfs f8, 0(r8)
fsubs f4, f6, f8
lis r8, mkChaosEma@ha
addi r8, r8, mkChaosEma@l
lfs f6, 0(r8)
fmuls f4, f4, f6
fadds f8, f8, f4
lis r8, mkChaosVol@ha
addi r8, r8, mkChaosVol@l
stfs f8, 0(r8)
; normalise volatility into 0..1 across the lo..hi band
lis r8, mkTuneChaosLo@ha
addi r8, r8, mkTuneChaosLo@l
lfs f4, 0(r8)
fsubs f6, f8, f4
lis r8, mkTuneChaosHi@ha
addi r8, r8, mkTuneChaosHi@l
lfs f9, 0(r8)
fsubs f9, f9, f4
fdivs f6, f6, f9
lis r8, mkSteerOne@ha
addi r8, r8, mkSteerOne@l
lfs f4, 0(r8)
fcmpu cr0, f6, f4
ble mkChaosNoHi
fmr f6, f4
mkChaosNoHi:
lis r8, mkSteerZero@ha
addi r8, r8, mkSteerZero@l
lfs f4, 0(r8)
fcmpu cr0, f6, f4
bge mkChaosNoLo
fmr f6, f4
mkChaosNoLo:
; c = mkTuneChaosMax * norm
lis r8, mkTuneChaosMax@ha
addi r8, r8, mkTuneChaosMax@l
lfs f4, 0(r8)
fmuls f6, f6, f4
; 4-slot shift register of the wheel steer, then blend in the 3-frame-old value
lis r8, mkChaosHist@ha
addi r8, r8, mkChaosHist@l
lfs f8, 8(r8)
stfs f8, 12(r8)
lfs f8, 4(r8)
stfs f8, 8(r8)
lfs f8, 0(r8)
stfs f8, 4(r8)
stfs f5, 0(r8)
lfs f8, 12(r8)
lis r8, mkSteerOne@ha
addi r8, r8, mkSteerOne@l
lfs f4, 0(r8)
fsubs f4, f4, f6
fmuls f5, f5, f4
fmuls f8, f8, f6
fadds f5, f5, f8
; the physical stick still wins when pushed past the override threshold
lis r8, mkSteerOverride@ha
addi r8, r8, mkSteerOverride@l
lfs f0, 0(r8)
fcmpu cr0, f1, f0
bgt mkSteerDone
lis r8, mkSteerNegOvr@ha
addi r8, r8, mkSteerNegOvr@l
lfs f0, 0(r8)
fcmpu cr0, f1, f0
blt mkSteerDone
fmr f1, f5
mkSteerDone:
mkPadWriteHold:
; ---- write back into the newest sample: MERGE ------------------------------
; Whatever Cemu's own devices already produced for this controller (a real pad,
; a keyboard added alongside it) is KEPT, and our VR bits are OR-ed on top.
; That is what makes multi-source input and switching seamless: no source is
; ever "selected", every source is simply live on every frame. The pad state is
; The pad state is always touched, even with no hand tracked, because the pad
; path needs the carrier translation and its edge refresh below.
lwz r4, 0x38(r1)
lwz r11, 0(r4)
or r3, r3, r11
; carrier: the Cemu profile maps the pad's Circle onto VPAD_STICK_R because Cemu
; allows only one input per GamePad function and L2 already owns B. Translate it
; here so the physical pad gets BOTH -- trigger brake and Circle back -- exactly
; like the VR mapping already does. MK8 never reads the right stick click, so
; borrowing that bit costs nothing.
lis r11, 0x0002
and r11, r3, r11
cmpwi r11, 0
beq mkPadNoCarrier
ori r3, r3, 0x4000
mkPadNoCarrier:
stw r3, 0(r4)
lis r8, mkPadPrev@ha
addi r8, r8, mkPadPrev@l
lwz r9, 0(r8)
andc r10, r3, r9
andc r11, r9, r3
stw r10, 4(r4)
stw r11, 8(r4)
stw r3, 0(r8)
cmpwi r6, 0
beq mkPadExit
; sticks: explicit source priority, not a magnitude comparison. The Cemu-side
; stick (a physical pad, or the keyboard's digital stick) wins whenever it is
; actually being pushed past the override threshold; below that the wheel keeps
; authority. That is what makes handing control between two devices feel
; deliberate instead of like two sources pulling against each other.
lfs f6, 12(r4)
lis r8, mkSteerOverride@ha
addi r8, r8, mkSteerOverride@l
lfs f0, 0(r8)
fcmpu cr0, f6, f0
bgt mkPadCemuStickX
lis r8, mkSteerNegOvr@ha
addi r8, r8, mkSteerNegOvr@l
lfs f0, 0(r8)
fcmpu cr0, f6, f0
blt mkPadCemuStickX
b mkPadStickXDone
mkPadCemuStickX:
fmr f1, f6
mkPadStickXDone:
stfs f1, 12(r4)
lfs f6, 16(r4)
lis r8, mkSteerOverride@ha
addi r8, r8, mkSteerOverride@l
lfs f0, 0(r8)
fcmpu cr0, f6, f0
bgt mkPadCemuStickY
lis r8, mkSteerNegOvr@ha
addi r8, r8, mkSteerNegOvr@l
lfs f0, 0(r8)
fcmpu cr0, f6, f0
blt mkPadCemuStickY
b mkPadStickYDone
mkPadCemuStickY:
fmr f2, f6
mkPadStickYDone:
stfs f2, 16(r4)
lfs f3, 368(r12)
lfs f4, 372(r12)
stfs f3, 20(r4)
stfs f4, 24(r4)
mkPadExit:
lwz r3, 0x2C(r1)
lwz r0, 0x44(r1)
mtlr r0
addi r1, r1, 0x40
blr
; pad-only entry: no VR contribution this frame, but the carrier translation
; and the edge refresh below must still run, or a pad-only session loses
; Circle (parked on VPAD_STICK_R by the profile) and its B edges entirely.
mkPadNoVr:
li r3, 0
b mkPadWriteHold

; half / negative-half for the stick-as-dpad threshold (0.5 and -0.5)
mkVpadHalf:
.int 0x3F000000
mkVpadNegHalf:
.int 0xBF000000
; motion steering: fixed constants (not user-tuned)
; mkSteerMode      1 = on, 0 = off
; mkSteerOverride  physical stick magnitude that takes over from the wheel
mkSteerMode:
.int 1
mkSteerZero:
.int 0
mkSteerOne:
.int 0x3F800000
mkSteerNegOne:
.int 0xBF800000
mkSteerOverride:
.int 0x3E800000
mkSteerNegOvr:
.int 0xBE800000

; ---------------------------------------------------------------------------
; Tuning block "CTUN". The in-VR panel scans the codecave for this signature
; and writes these floats; the pack reads them every frame. The magic, version,
; count and ORDER must stay in sync with the layer's tuner.
;   0 mkTuneSteerGain  roll -> steer multiplier          0.5 .. 6.0
;   1 mkTuneSteerDead  centre deadzone on the roll       0.0 .. 0.4
;   2 mkTuneSteerCurve cubic blend, 0=linear 1=cubic     0.0 .. 1.0
;   3 mkTuneSteerSlew  max steer change per frame        0.03 .. 1.0
;   4 mkTuneSteerSign  +1.0 normal, -1.0 inverted
;   5 mkTuneAccel      right trigger value that presses A/Y  0.0 .. 0.9
;   6 mkTuneBrake      left trigger value that presses B     0.0 .. 0.9
mkTuneHeader:
.int 0x4354554E
.int 1
mkTuneCount:
.int 14
mkTuneSteerGain:
.int 0x40133333
mkTuneSteerDead:
.int 0x3DF5C28F
mkTuneSteerCurve:
.int 0x3F000000
mkTuneSteerSlew:
.int 0x3ECCCCCD
mkTuneSteerSign:
.int 0xBF800000
mkTuneAccel:
.int 0x3E4CCCCD
mkTuneBrake:
.int 0x3E4CCCCD
; steering zone: a sphere in front of the chest the wheel hand must be inside.
; Centre = published head position + (zoneVert, zoneFwd). Authority is 1 inside
; zoneInner and falls linearly to 0 at zoneOuter. zoneFwd is signed because the
; anchor's forward axis sign is not guaranteed -- if the zone behaves inverted
; (works behind your back instead of in front), negate zoneFwd.
mkTuneZoneInner:
.int 0x3E99999A
mkTuneZoneOuter:
.int 0x3F19999A
mkTuneZoneFwd:
.int 0x3EB33333
mkTuneZoneVert:
.int 0xBE99999A
mkTuneChaosMax:
.int 0x3F266666
mkTuneChaosLo:
.int 0x3E800000
mkTuneChaosHi:
.int 0x3F400000
; last steering value, for the slew limit
mkSteerPrev:
.int 0
mkChaosPrev:
.int 0
mkChaosVol:
.int 0
mkChaosEma:
.int 0x3E800000
mkChaosHist:
.int 0
.int 0
.int 0
.int 0
; previous hold word, for trigger/release edges
mkPadPrev:
.int 0

; Replace MK8's single VPADRead call site with the hook above.
0x02725720 = bla mkPadHook

; ---------------------------------------------------------------------------
; mkHeadLookRight - rotate a 4x4 VIEW matrix by the latched headset rotation for
; a ROW-VECTOR convention (GX2): v_view = v_world * M, so rotating the camera in
; view space is a RIGHT multiply, M' = M * D. Only the upper-left 3x3 is touched;
; row 3 and column 3 are copied through. Inert unless mkHeadLookEnable != 0 and a
; fresh pose is latched (mkPoseLatch0 word 0 non-zero).
;   r3 = source matrix (must differ from r4), r4 = destination matrix
mkHeadLookRight:
lis r12, mkHeadLookEnable@ha
addi r12, r12, mkHeadLookEnable@l
lwz r11, 0(r12)
cmpwi r11, 0
beq mkHeadLookRightDone
lis r12, mkPoseLatch0@ha
addi r12, r12, mkPoseLatch0@l
lwz r11, 0(r12)
cmpwi r11, 0
beq mkHeadLookRightDone
; copy through column 3 (rows 0..2) and the whole row 3
lwz r11, 12(r3)
stw r11, 12(r4)
lwz r11, 28(r3)
stw r11, 28(r4)
lwz r11, 44(r3)
stw r11, 44(r4)
lwz r11, 48(r3)
stw r11, 48(r4)
lwz r11, 52(r3)
stw r11, 52(r4)
lwz r11, 56(r3)
stw r11, 56(r4)
lwz r11, 60(r3)
stw r11, 60(r4)
lfs f1, 4(r12)
lfs f2, 8(r12)
lfs f3, 12(r12)
lfs f4, 20(r12)
lfs f5, 24(r12)
lfs f6, 28(r12)
lfs f7, 36(r12)
lfs f8, 40(r12)
lfs f9, 44(r12)
lfs f11, 0(r3)
fmuls f10, f11, f1
lfs f11, 4(r3)
fmuls f12, f11, f4
fadds f10, f10, f12
lfs f11, 8(r3)
fmuls f12, f11, f7
fadds f10, f10, f12
stfs f10, 0(r4)
lfs f11, 0(r3)
fmuls f10, f11, f2
lfs f11, 4(r3)
fmuls f12, f11, f5
fadds f10, f10, f12
lfs f11, 8(r3)
fmuls f12, f11, f8
fadds f10, f10, f12
stfs f10, 4(r4)
lfs f11, 0(r3)
fmuls f10, f11, f3
lfs f11, 4(r3)
fmuls f12, f11, f6
fadds f10, f10, f12
lfs f11, 8(r3)
fmuls f12, f11, f9
fadds f10, f10, f12
stfs f10, 8(r4)
lfs f11, 16(r3)
fmuls f10, f11, f1
lfs f11, 20(r3)
fmuls f12, f11, f4
fadds f10, f10, f12
lfs f11, 24(r3)
fmuls f12, f11, f7
fadds f10, f10, f12
stfs f10, 16(r4)
lfs f11, 16(r3)
fmuls f10, f11, f2
lfs f11, 20(r3)
fmuls f12, f11, f5
fadds f10, f10, f12
lfs f11, 24(r3)
fmuls f12, f11, f8
fadds f10, f10, f12
stfs f10, 20(r4)
lfs f11, 16(r3)
fmuls f10, f11, f3
lfs f11, 20(r3)
fmuls f12, f11, f6
fadds f10, f10, f12
lfs f11, 24(r3)
fmuls f12, f11, f9
fadds f10, f10, f12
stfs f10, 24(r4)
lfs f11, 32(r3)
fmuls f10, f11, f1
lfs f11, 36(r3)
fmuls f12, f11, f4
fadds f10, f10, f12
lfs f11, 40(r3)
fmuls f12, f11, f7
fadds f10, f10, f12
stfs f10, 32(r4)
lfs f11, 32(r3)
fmuls f10, f11, f2
lfs f11, 36(r3)
fmuls f12, f11, f5
fadds f10, f10, f12
lfs f11, 40(r3)
fmuls f12, f11, f8
fadds f10, f10, f12
stfs f10, 36(r4)
lfs f11, 32(r3)
fmuls f10, f11, f3
lfs f11, 36(r3)
fmuls f12, f11, f6
fadds f10, f10, f12
lfs f11, 40(r3)
fmuls f12, f11, f9
fadds f10, f10, f12
stfs f10, 40(r4)
mkHeadLookRightDone:
blr

; ---------------------------------------------------------------------------
; mkCameraSubmit - replaces `bl 0x02566c7c` at 0x025D887C inside FUN_025D7884.
; That call receives r4 = a private 20-word camera block on the caller's stack,
; copied from [cameraObj]+0xD4 (RE-FINDINGS 11.2), so substituting it cannot
; corrupt game state. The block's leading 4x4 is the view matrix; we rotate it by
; the latched headset rotation before handing it on. This is BetterVR's
; hook_GetRenderCamera semantics (camera.cpp:757) expressed in asm.
; Only volatile registers are clobbered, exactly as the replaced call did.
mkCameraSubmit:
mflr r0
stwu r1, -0x20(r1)
stw r0, 0x24(r1)
stw r3, 0x1C(r1)
stw r4, 0x18(r1)
; Unconditional entry counter. mkdiag.cam only reports when the dumped matrix
; CHANGES, so it cannot distinguish "never entered" from "entered but stable".
; This counter is read by the layer before any gating.
lis r12, mkCameraCalls@ha
addi r12, r12, mkCameraCalls@l
lwz r11, 0(r12)
addi r11, r11, 1
stw r11, 0(r12)
; dump the game's own matrix so the convention can be confirmed from the log
lis r12, mkDiagMatrix3@ha
addi r12, r12, mkDiagMatrix3@l
lwz r11, 0(r4)
stw r11, 0(r12)
lwz r11, 4(r4)
stw r11, 4(r12)
lwz r11, 8(r4)
stw r11, 8(r12)
lwz r11, 12(r4)
stw r11, 12(r12)
lwz r11, 16(r4)
stw r11, 16(r12)
lwz r11, 20(r4)
stw r11, 20(r12)
lwz r11, 24(r4)
stw r11, 24(r12)
lwz r11, 28(r4)
stw r11, 28(r12)
lwz r11, 32(r4)
stw r11, 32(r12)
lwz r11, 36(r4)
stw r11, 36(r12)
lwz r11, 40(r4)
stw r11, 40(r12)
lwz r11, 44(r4)
stw r11, 44(r12)
lwz r11, 48(r4)
stw r11, 48(r12)
lwz r11, 52(r4)
stw r11, 52(r12)
lwz r11, 56(r4)
stw r11, 56(r12)
lwz r11, 60(r4)
stw r11, 60(r12)
; seed the result from the block so a disabled gate is a perfect no-op
lis r12, mkMatrixResult@ha
addi r12, r12, mkMatrixResult@l
lwz r11, 0(r4)
stw r11, 0(r12)
lwz r11, 4(r4)
stw r11, 4(r12)
lwz r11, 8(r4)
stw r11, 8(r12)
lwz r11, 12(r4)
stw r11, 12(r12)
lwz r11, 16(r4)
stw r11, 16(r12)
lwz r11, 20(r4)
stw r11, 20(r12)
lwz r11, 24(r4)
stw r11, 24(r12)
lwz r11, 28(r4)
stw r11, 28(r12)
lwz r11, 32(r4)
stw r11, 32(r12)
lwz r11, 36(r4)
stw r11, 36(r12)
lwz r11, 40(r4)
stw r11, 40(r12)
lwz r11, 44(r4)
stw r11, 44(r12)
lwz r11, 48(r4)
stw r11, 48(r12)
lwz r11, 52(r4)
stw r11, 52(r12)
lwz r11, 56(r4)
stw r11, 56(r12)
lwz r11, 60(r4)
stw r11, 60(r12)
; rotate: src = the block, dst = mkMatrixResult
lwz r3, 0x18(r1)
lis r4, mkMatrixResult@ha
addi r4, r4, mkMatrixResult@l
bl mkHeadLookRight
; write the rotated matrix back over the game's stack block
lwz r4, 0x18(r1)
lis r12, mkMatrixResult@ha
addi r12, r12, mkMatrixResult@l
lwz r11, 0(r12)
stw r11, 0(r4)
lwz r11, 4(r12)
stw r11, 4(r4)
lwz r11, 8(r12)
stw r11, 8(r4)
lwz r11, 12(r12)
stw r11, 12(r4)
lwz r11, 16(r12)
stw r11, 16(r4)
lwz r11, 20(r12)
stw r11, 20(r4)
lwz r11, 24(r12)
stw r11, 24(r4)
lwz r11, 28(r12)
stw r11, 28(r4)
lwz r11, 32(r12)
stw r11, 32(r4)
lwz r11, 36(r12)
stw r11, 36(r4)
lwz r11, 40(r12)
stw r11, 40(r4)
lwz r11, 44(r12)
stw r11, 44(r4)
lwz r11, 48(r12)
stw r11, 48(r4)
lwz r11, 52(r12)
stw r11, 52(r4)
lwz r11, 56(r12)
stw r11, 56(r4)
lwz r11, 60(r12)
stw r11, 60(r4)
; restore the call arguments and run the native consumer
lwz r3, 0x1C(r1)
lwz r4, 0x18(r1)
bl mkCameraConsumeNative
lwz r0, 0x24(r1)
mtlr r0
addi r1, r1, 0x20
blr

0x02566c7c = mkCameraConsumeNative:

; Stage A: hand the renderer a head-driven camera. The old projection hook stays
; unarmed - section 9.1 proved context+0 is the projection, not the camera.
0x025D887C = bla mkCameraSubmit
; All three call sites of the camera-block consumer FUN_02566c7c funnel here:
; FUN_025D7884 (case 0x1d) and both calls in FUN_025d8f84, the per-slot loop the
; live scene actually uses. Each passes a private 20-word stack copy of
; [cameraObj]+0xD4, so none of them can be corrupted.
0x025D9190 = bla mkCameraSubmit
0x025D9398 = bla mkCameraSubmit

; ---------------------------------------------------------------------------
; mkLoadTokMagic - loads f1 with the metadata magic for the pose clear: 0.21875
; (menu, decodes to token 0x10000 -> world-locked panel) when mkMenuUsed is
; non-zero, else 0.1875 (the ordinary pose-token magic). Mirrors SM3DW-VR
; rrCopyMarker's rrMenuMetadataSelect (reference patch_vr.asm line 580). f2..f4
; are left untouched.
mkLoadTokMagic:
lis r12, mkMenuUsed@ha
addi r12, r12, mkMenuUsed@l
lwz r11, 0(r12)
cmpwi r11, 0
bne mkLoadTokMagicMenu
lis r12, mkPoseTok0@ha
addi r12, r12, mkPoseTok0@l
lfs f1, 0(r12)
blr
mkLoadTokMagicMenu:
lis r12, mkMenuMagic@ha
addi r12, r12, mkMenuMagic@l
lfs f1, 0(r12)
blr

; ---------------------------------------------------------------------------
; mkCamLoopCount - entry counter for FUN_025d8f84, the per-slot camera loop
; that reads [cameraObj]+0xD0 and feeds FUN_02566c7c. That function has no
; static callers (vtable dispatched), so we cannot tell from the reference
; list whether the subsystem runs at all. Replays the replaced prologue
; instruction (stwu r1,-0xf0(r1)) and branches into the body. r11/r12 are
; volatile at a function entry and r3..r10 are preserved, so the callee sees
; exactly what it would have seen.
mkCamLoopCount:
lis r12, mkCamLoopCalls@ha
addi r12, r12, mkCamLoopCalls@l
lwz r11, 0(r12)
addi r11, r11, 1
stw r11, 0(r12)
stwu r1, -0xf0(r1)
b 0x025d8f88

0x025D8F84 = ba mkCamLoopCount

; ---------------------------------------------------------------------------
; mkCamUpdateCount - entry counter for FUN_024B0060, the per-frame camera and
; viewport update (fills param_1+0x890 slots, sets +0xAF0 TV/DRC mode, then the
; rect/aspect chain). Proves which camera path the live renderer actually uses.
; Replays the replaced prologue instruction (mfspr r0,LR) and branches to
; 0x024b0064. r11/r12 are volatile at an entry and r3 (the argument) is
; untouched, so the callee sees exactly the ABI state it expects.
mkCamUpdateCount:
lis r12, mkCamUpdateCalls@ha
addi r12, r12, mkCamUpdateCalls@l
lwz r11, 0(r12)
addi r11, r11, 1
stw r11, 0(r12)
.int 0x7C0802A6 ; mfspr r0,LR
b 0x024b0064

0x024B0060 = ba mkCamUpdateCount

; ---------------------------------------------------------------------------
; mkCamSlotDump - hooks `bl 0x024afd28` at 0x024B03D0, the tail of
; FUN_024B0060 (the LIVE per-frame camera update, section 13.4). At that point
; the per-slot descriptors have just been filled, and r3 = r31 = param_1
; (024b03cc `or r3,r31,r31`).
; Dumps, for the five slots at param_1+0x890+idx*0x20, the descriptor pointer
; stored at +0x18 (source table DAT_101d62fc + idx*4 + mode*0x14), plus the
; TV/DRC mode at +0xAF0. Only reads; only r11/r12 and the saved r0 are touched.
mkCamSlotDump:
mflr r0
stwu r1, -0x20(r1)
stw r0, 0x24(r1)
stw r3, 0x1C(r1)
lis r12, mkCamSlots@ha
addi r12, r12, mkCamSlots@l
stw r3, 0(r12)
lwz r11, 0xAF0(r3)
stw r11, 4(r12)
lwz r11, 0x8A8(r3)
stw r11, 8(r12)
lwz r11, 0x8C8(r3)
stw r11, 12(r12)
lwz r11, 0x8E8(r3)
stw r11, 16(r12)
lwz r11, 0x908(r3)
stw r11, 20(r12)
lwz r11, 0x928(r3)
stw r11, 24(r12)
lwz r3, 0x1C(r1)
bl mkCamAfd28Native
lwz r0, 0x24(r1)
mtlr r0
addi r1, r1, 0x20
blr

0x024afd28 = mkCamAfd28Native:

0x024B03D0 = bla mkCamSlotDump

; ---------------------------------------------------------------------------
; mkCamStructDump - entry hook for FUN_024dd9fc, the NORMAL camera setter used
; by the live slot path (FUN_025d8f84). That function copies 0x60 bytes from its
; r4 argument into the render object at +0xC4 and +0x1F0, so r4 is the camera
; struct: this is the data MK8 actually renders with.
; No stack frame and no calls: it only clobbers r11/r12 (volatile at an entry)
; and leaves r3/r4 untouched, then replays the replaced mfspr and branches to
; 0x024dda00. Safe even though FUN_024dd9fc later does stmw.
mkCamStructDump:
lis r12, mkCamStructCalls@ha
addi r12, r12, mkCamStructCalls@l
lwz r11, 0(r12)
addi r11, r11, 1
stw r11, 0(r12)
; Capture setter callsite and arguments in currently unused diagnostic words.
 mflr r11
 lis r12, mkYawSnap@ha
 addi r12, r12, mkYawSnap@l
 stw r11, 0(r12)
 stw r3, 4(r12)
 stw r4, 8(r12)
cmpwi r4, 0
beq mkCamStructDone
lis r12, mkCamStruct@ha
addi r12, r12, mkCamStruct@l
lwz r11, 0(r4)
stw r11, 0(r12)
lwz r11, 4(r4)
stw r11, 4(r12)
lwz r11, 8(r4)
stw r11, 8(r12)
lwz r11, 12(r4)
stw r11, 12(r12)
lwz r11, 16(r4)
stw r11, 16(r12)
lwz r11, 20(r4)
stw r11, 20(r12)
lwz r11, 24(r4)
stw r11, 24(r12)
lwz r11, 28(r4)
stw r11, 28(r12)
lwz r11, 32(r4)
stw r11, 32(r12)
lwz r11, 36(r4)
stw r11, 36(r12)
lwz r11, 40(r4)
stw r11, 40(r12)
lwz r11, 44(r4)
stw r11, 44(r12)
lwz r11, 48(r4)
stw r11, 48(r12)
lwz r11, 52(r4)
stw r11, 52(r12)
lwz r11, 56(r4)
stw r11, 56(r12)
lwz r11, 60(r4)
stw r11, 60(r12)
lwz r11, 64(r4)
stw r11, 64(r12)
lwz r11, 68(r4)
stw r11, 68(r12)
lwz r11, 72(r4)
stw r11, 72(r12)
lwz r11, 76(r4)
stw r11, 76(r12)
lwz r11, 80(r4)
stw r11, 80(r12)
lwz r11, 84(r4)
stw r11, 84(r12)
lwz r11, 88(r4)
stw r11, 88(r12)
lwz r11, 92(r4)
stw r11, 92(r12)
mkCamStructFilterDone:
mkCamStructDone:
.int 0x7C0802A6 ; mfspr r0,LR (replayed)
b 0x024dda00

0x024DD9FC = ba mkCamStructDump

; ---------------------------------------------------------------------------
; mkApplyTestYaw - applies a FIXED 30 degree yaw to the per-pass context+0x40
; block, right-multiplied on the upper 3x3 (row-vector convention):
;   new[i][0] = M[i][0]*c + M[i][2]*s
;   new[i][2] = M[i][2]*c - M[i][0]*s
; Change-guarded so it applies once per frame: the hook fires per PASS, so an
; unguarded in-place rotation would compound. If the block still equals the
; snapshot we left behind, we skip; the game rewrites it each frame, which is the
; signal to rotate again. mkYawApplied counts real applications so "rotated",
; "spun" and "never fired" are distinguishable from the log.
;   r3 = context block
mkApplyTestYaw:
lis r12, mkYawApplied@ha
addi r12, r12, mkYawApplied@l
lwz r11, 0(r12)
addi r11, r11, 1
stw r11, 0(r12)
lis r12, mkTestYaw@ha
addi r12, r12, mkTestYaw@l
lwz r11, 0(r12)
cmpwi r11, 0
beq mkYawDone
lis r9, mkYawSnap@ha
addi r9, r9, mkYawSnap@l
lwz r10, 48(r3)
lwz r8, 0(r9)
cmpw r10, r8
bne mkYawApply
lwz r10, 52(r3)
lwz r8, 4(r9)
cmpw r10, r8
bne mkYawApply
lwz r10, 56(r3)
lwz r8, 8(r9)
cmpw r10, r8
bne mkYawApply
lwz r10, 60(r3)
lwz r8, 12(r9)
cmpw r10, r8
beq mkYawDone
mkYawApply:
lis r12, mkYawCos@ha
addi r12, r12, mkYawCos@l
lfs f1, 0(r12)
lis r12, mkYawSin@ha
addi r12, r12, mkYawSin@l
lfs f2, 0(r12)
lfs f3, 0(r3)
lfs f4, 8(r3)
fmuls f5, f3, f1
fmuls f6, f4, f2
fadds f5, f5, f6
stfs f5, 0(r3)
fmuls f5, f3, f2
fmuls f6, f4, f1
fsubs f5, f6, f5
stfs f5, 8(r3)
lfs f3, 16(r3)
lfs f4, 24(r3)
fmuls f5, f3, f1
fmuls f6, f4, f2
fadds f5, f5, f6
stfs f5, 16(r3)
fmuls f5, f3, f2
fmuls f6, f4, f1
fsubs f5, f6, f5
stfs f5, 24(r3)
lfs f3, 32(r3)
lfs f4, 40(r3)
fmuls f5, f3, f1
fmuls f6, f4, f2
fadds f5, f5, f6
stfs f5, 32(r3)
fmuls f5, f3, f2
fmuls f6, f4, f1
fsubs f5, f6, f5
stfs f5, 40(r3)
lwz r10, 48(r3)
stw r10, 0(r9)
lwz r10, 52(r3)
stw r10, 4(r9)
lwz r10, 56(r3)
stw r10, 8(r9)
lwz r10, 60(r3)
stw r10, 12(r9)
lis r12, mkYawApplied@ha
addi r12, r12, mkYawApplied@l
lwz r11, 0(r12)
addi r11, r11, 1
stw r11, 0(r12)
mkYawDone:
blr

; ---------------------------------------------------------------------------
; mkCamMgrDump - wraps `bl 0x024ce59c` at 0x024B0074 inside FUN_024B0060 (the live
; per-frame camera update). FUN_024CE59C() returns the camera manager; the caller
; immediately dereferences +0x2BC as the camera manager proper. That structure is
; the canonical home of a live camera and the last unexamined candidate.
; Calls the native, keeps its return value in r3 for the caller, and dumps
; [mgr+0x2BC] plus the first 32 words of that structure. Volatile registers only.
mkCamMgrDump:
mflr r0
stwu r1, -0x30(r1)
stw r0, 0x34(r1)
bl mkCamMgrNative
lis r12, mkCamMgrInfo@ha
addi r12, r12, mkCamMgrInfo@l
lwz r11, 0(r12)
addi r11, r11, 1
stw r11, 0(r12)
stw r3, 4(r12)
cmpwi r3, 0
beq mkCamMgrDone
lwz r11, 0x2BC(r3)
lis r12, mkCamMgrInfo@ha
addi r12, r12, mkCamMgrInfo@l
stw r11, 8(r12)
cmpwi r11, 0
beq mkCamMgrDone
lwz r10, 0(r11)
stw r10, 12(r12)
lwz r10, 4(r11)
stw r10, 16(r12)
lwz r10, 8(r11)
stw r10, 20(r12)
lwz r10, 12(r11)
stw r10, 24(r12)
lwz r10, 16(r11)
stw r10, 28(r12)
lwz r10, 20(r11)
stw r10, 32(r12)
lwz r10, 24(r11)
stw r10, 36(r12)
lwz r10, 28(r11)
stw r10, 40(r12)
lwz r10, 32(r11)
stw r10, 44(r12)
lwz r10, 36(r11)
stw r10, 48(r12)
lwz r10, 40(r11)
stw r10, 52(r12)
lwz r10, 44(r11)
stw r10, 56(r12)
lwz r10, 48(r11)
stw r10, 60(r12)
lwz r10, 52(r11)
stw r10, 64(r12)
lwz r10, 56(r11)
stw r10, 68(r12)
lwz r10, 60(r11)
stw r10, 72(r12)
lwz r10, 64(r11)
stw r10, 76(r12)
lwz r10, 68(r11)
stw r10, 80(r12)
lwz r10, 72(r11)
stw r10, 84(r12)
lwz r10, 76(r11)
stw r10, 88(r12)
lwz r10, 80(r11)
stw r10, 92(r12)
lwz r10, 84(r11)
stw r10, 96(r12)
lwz r10, 88(r11)
stw r10, 100(r12)
lwz r10, 92(r11)
stw r10, 104(r12)
lwz r10, 96(r11)
stw r10, 108(r12)
lwz r10, 100(r11)
stw r10, 112(r12)
lwz r10, 104(r11)
stw r10, 116(r12)
lwz r10, 108(r11)
stw r10, 120(r12)
lwz r10, 112(r11)
stw r10, 124(r12)
lwz r10, 116(r11)
stw r10, 128(r12)
lwz r10, 120(r11)
stw r10, 132(r12)
lwz r10, 124(r11)
stw r10, 136(r12)
; follow the two heap objects the manager points at: these are the camera
; objects (the descriptor array only holds type/flags/scene-view ids).
lis r12, mkCamMgrInfo@ha
addi r12, r12, mkCamMgrInfo@l
lwz r11, 8(r12)
cmpwi r11, 0
beq mkCamMgrDone
lwz r10, 0x0C(r11)
cmpwi r10, 0
beq mkCamMgrSkipA
lis r12, mkCamObjA@ha
addi r12, r12, mkCamObjA@l
lwz r11, 0(r10)
stw r11, 0(r12)
lwz r11, 4(r10)
stw r11, 4(r12)
lwz r11, 8(r10)
stw r11, 8(r12)
lwz r11, 12(r10)
stw r11, 12(r12)
lwz r11, 16(r10)
stw r11, 16(r12)
lwz r11, 20(r10)
stw r11, 20(r12)
lwz r11, 24(r10)
stw r11, 24(r12)
lwz r11, 28(r10)
stw r11, 28(r12)
lwz r11, 32(r10)
stw r11, 32(r12)
lwz r11, 36(r10)
stw r11, 36(r12)
lwz r11, 40(r10)
stw r11, 40(r12)
lwz r11, 44(r10)
stw r11, 44(r12)
lwz r11, 48(r10)
stw r11, 48(r12)
lwz r11, 52(r10)
stw r11, 52(r12)
lwz r11, 56(r10)
stw r11, 56(r12)
lwz r11, 60(r10)
stw r11, 60(r12)
lwz r11, 64(r10)
stw r11, 64(r12)
lwz r11, 68(r10)
stw r11, 68(r12)
lwz r11, 72(r10)
stw r11, 72(r12)
lwz r11, 76(r10)
stw r11, 76(r12)
lwz r11, 80(r10)
stw r11, 80(r12)
lwz r11, 84(r10)
stw r11, 84(r12)
lwz r11, 88(r10)
stw r11, 88(r12)
lwz r11, 92(r10)
stw r11, 92(r12)
lwz r11, 96(r10)
stw r11, 96(r12)
lwz r11, 100(r10)
stw r11, 100(r12)
lwz r11, 104(r10)
stw r11, 104(r12)
lwz r11, 108(r10)
stw r11, 108(r12)
lwz r11, 112(r10)
stw r11, 112(r12)
lwz r11, 116(r10)
stw r11, 116(r12)
lwz r11, 120(r10)
stw r11, 120(r12)
lwz r11, 124(r10)
stw r11, 124(r12)
mkCamMgrSkipA:
lis r12, mkCamMgrInfo@ha
addi r12, r12, mkCamMgrInfo@l
lwz r11, 8(r12)
lwz r10, 0x10(r11)
cmpwi r10, 0
beq mkCamMgrDone
lis r12, mkCamObjB@ha
addi r12, r12, mkCamObjB@l
lwz r11, 0(r10)
stw r11, 0(r12)
lwz r11, 4(r10)
stw r11, 4(r12)
lwz r11, 8(r10)
stw r11, 8(r12)
lwz r11, 12(r10)
stw r11, 12(r12)
lwz r11, 16(r10)
stw r11, 16(r12)
lwz r11, 20(r10)
stw r11, 20(r12)
lwz r11, 24(r10)
stw r11, 24(r12)
lwz r11, 28(r10)
stw r11, 28(r12)
lwz r11, 32(r10)
stw r11, 32(r12)
lwz r11, 36(r10)
stw r11, 36(r12)
lwz r11, 40(r10)
stw r11, 40(r12)
lwz r11, 44(r10)
stw r11, 44(r12)
lwz r11, 48(r10)
stw r11, 48(r12)
lwz r11, 52(r10)
stw r11, 52(r12)
lwz r11, 56(r10)
stw r11, 56(r12)
lwz r11, 60(r10)
stw r11, 60(r12)
lwz r11, 64(r10)
stw r11, 64(r12)
lwz r11, 68(r10)
stw r11, 68(r12)
lwz r11, 72(r10)
stw r11, 72(r12)
lwz r11, 76(r10)
stw r11, 76(r12)
lwz r11, 80(r10)
stw r11, 80(r12)
lwz r11, 84(r10)
stw r11, 84(r12)
lwz r11, 88(r10)
stw r11, 88(r12)
lwz r11, 92(r10)
stw r11, 92(r12)
lwz r11, 96(r10)
stw r11, 96(r12)
lwz r11, 100(r10)
stw r11, 100(r12)
lwz r11, 104(r10)
stw r11, 104(r12)
lwz r11, 108(r10)
stw r11, 108(r12)
lwz r11, 112(r10)
stw r11, 112(r12)
lwz r11, 116(r10)
stw r11, 116(r12)
lwz r11, 120(r10)
stw r11, 120(r12)
lwz r11, 124(r10)
stw r11, 124(r12)
; third level: [camMgr+0x0C] is the camera container, [+0x34] is slot 0 of its
; six 0xD38-byte camera structs. Dump 64 words to catch a view matrix.
lis r12, mkCamMgrInfo@ha
addi r12, r12, mkCamMgrInfo@l
lwz r11, 8(r12)
cmpwi r11, 0
beq mkCamMgrDone
lwz r11, 0x0C(r11)
cmpwi r11, 0
beq mkCamMgrDone
lwz r10, 0x34(r11)
cmpwi r10, 0
beq mkCamMgrDone
lis r12, mkCamSlot64@ha
addi r12, r12, mkCamSlot64@l
lwz r11, 0(r10)
stw r11, 0(r12)
lwz r11, 4(r10)
stw r11, 4(r12)
lwz r11, 8(r10)
stw r11, 8(r12)
lwz r11, 12(r10)
stw r11, 12(r12)
lwz r11, 16(r10)
stw r11, 16(r12)
lwz r11, 20(r10)
stw r11, 20(r12)
lwz r11, 24(r10)
stw r11, 24(r12)
lwz r11, 28(r10)
stw r11, 28(r12)
lwz r11, 32(r10)
stw r11, 32(r12)
lwz r11, 36(r10)
stw r11, 36(r12)
lwz r11, 40(r10)
stw r11, 40(r12)
lwz r11, 44(r10)
stw r11, 44(r12)
lwz r11, 48(r10)
stw r11, 48(r12)
lwz r11, 52(r10)
stw r11, 52(r12)
lwz r11, 56(r10)
stw r11, 56(r12)
lwz r11, 60(r10)
stw r11, 60(r12)
lwz r11, 64(r10)
stw r11, 64(r12)
lwz r11, 68(r10)
stw r11, 68(r12)
lwz r11, 72(r10)
stw r11, 72(r12)
lwz r11, 76(r10)
stw r11, 76(r12)
lwz r11, 80(r10)
stw r11, 80(r12)
lwz r11, 84(r10)
stw r11, 84(r12)
lwz r11, 88(r10)
stw r11, 88(r12)
lwz r11, 92(r10)
stw r11, 92(r12)
lwz r11, 96(r10)
stw r11, 96(r12)
lwz r11, 100(r10)
stw r11, 100(r12)
lwz r11, 104(r10)
stw r11, 104(r12)
lwz r11, 108(r10)
stw r11, 108(r12)
lwz r11, 112(r10)
stw r11, 112(r12)
lwz r11, 116(r10)
stw r11, 116(r12)
lwz r11, 120(r10)
stw r11, 120(r12)
lwz r11, 124(r10)
stw r11, 124(r12)
lwz r11, 128(r10)
stw r11, 128(r12)
lwz r11, 132(r10)
stw r11, 132(r12)
lwz r11, 136(r10)
stw r11, 136(r12)
lwz r11, 140(r10)
stw r11, 140(r12)
lwz r11, 144(r10)
stw r11, 144(r12)
lwz r11, 148(r10)
stw r11, 148(r12)
lwz r11, 152(r10)
stw r11, 152(r12)
lwz r11, 156(r10)
stw r11, 156(r12)
lwz r11, 160(r10)
stw r11, 160(r12)
lwz r11, 164(r10)
stw r11, 164(r12)
lwz r11, 168(r10)
stw r11, 168(r12)
lwz r11, 172(r10)
stw r11, 172(r12)
lwz r11, 176(r10)
stw r11, 176(r12)
lwz r11, 180(r10)
stw r11, 180(r12)
lwz r11, 184(r10)
stw r11, 184(r12)
lwz r11, 188(r10)
stw r11, 188(r12)
lwz r11, 192(r10)
stw r11, 192(r12)
lwz r11, 196(r10)
stw r11, 196(r12)
lwz r11, 200(r10)
stw r11, 200(r12)
lwz r11, 204(r10)
stw r11, 204(r12)
lwz r11, 208(r10)
stw r11, 208(r12)
lwz r11, 212(r10)
stw r11, 212(r12)
lwz r11, 216(r10)
stw r11, 216(r12)
lwz r11, 220(r10)
stw r11, 220(r12)
lwz r11, 224(r10)
stw r11, 224(r12)
lwz r11, 228(r10)
stw r11, 228(r12)
lwz r11, 232(r10)
stw r11, 232(r12)
lwz r11, 236(r10)
stw r11, 236(r12)
lwz r11, 240(r10)
stw r11, 240(r12)
lwz r11, 244(r10)
stw r11, 244(r12)
lwz r11, 248(r10)
stw r11, 248(r12)
lwz r11, 252(r10)
stw r11, 252(r12)
mkCamMgrDone:
lwz r0, 0x34(r1)
mtlr r0
addi r1, r1, 0x30
blr

0x024ce59c = mkCamMgrNative:

0x024B0074 = bla mkCamMgrDump

# Current status and roadmap

Updated from the research log’s latest recorded session. Items below reflect the last documented experiments, not a claim about unrecorded changes.

## Proven working

- Cemu loads the MK8 VR graphic pack and recognizes its `CTFL` flow header and `CTM1` acknowledgement.
- Marker clears at the color-buffer copy sites are detected by the Vulkan layer. TV and DRC sources are identified separately.
- The pose mailbox handshake, token decoding, XR session setup, and stereo projection submission path have all been exercised.
- Setting `CEMUVR_REFERENCE_UNPOSED_SURFACE=0` and `CEMUVR_REFERENCE_POSE_STAMP=0` avoids the Virtual Desktop quad path that previously froze and selects the projection-layer path.
- TV and DRC can supply distinct images to the two eyes. This is Route A: reuse the two existing scan-target renders rather than render the entire scene twice.
- `context+0` at `FUN_026A5D98` is a projection matrix, but hooks around this pass/upload route were risky and it is not the preferred live camera path.
- A probe measured no calls to the candidate `[camera]+0xD0` handoff sites during a live race. Those sites describe dormant code and must not be treated as the active renderer path.

## Open work

1. **Find the live camera/view accessor.** Instrument the active per-frame update (`FUN_024B0060`) and trace its camera descriptors to the view data actually consumed by live rendering.
2. **Apply headset pose at a safe camera boundary.** Compose the game camera pose with the per-eye OpenXR pose. The current evidence does not yet establish a stable, live injection point for full rotational look and 6DOF.
3. **Frame-lock the stereo pair.** Ensure TV and DRC source images correspond to the same game frame before submission.
4. **Validate across scenes and sessions.** Menus, intro, races, restart, and runtime recovery need repeatable checks after camera changes.
5. **Retest resource settings on native hardware.** Earlier performance/freeze results came from a four-vCPU virtual machine with a virtualized RTX A4500 and streamed VR.

## Important corrections

- `FUN_02116B60` is a scan-target state reset, not present/flip.
- `FUN_0250E8E8` / `FUN_0250E74C` are setup/dispatch paths, not the scene draw itself. The relevant scene render loop is in `FUN_020A5DB4`, with pass recording below it.
- Static evidence for the camera object at `[camera]+0xD0` is valid, but runtime probes show that subsystem is not entered during the measured live race.
- An older report blamed any hook in `FUN_026A5D98` for boot crashes. Later tests found a malformed codecave trampoline caused one crash; a corrected trampoline ran. The matrix upload path remains risky and is not a demonstrated camera solution.
- A pose-token clear briefly caused Cemu fail-fast. Do not assume that experiment is safe; consult the session log for the unresolved ABI/reentrancy hypotheses.

## Milestones

| Milestone | State | Evidence |
|---|---|---|
| Marker transport into Cemu Vulkan layer | Complete | Runtime logs in research log §7 |
| Quest projection-layer submission | Demonstrated | Research log §§8.13–8.14 |
| Distinct TV/DRC source image per eye | Demonstrated, temporal pairing still needs work | Research log §§8.5, 8.14 |
| Live game camera plus headset pose | In progress | Research log §§9–13 |
| Stable, frame-locked 6DOF gameplay | Not complete | Requires work above |


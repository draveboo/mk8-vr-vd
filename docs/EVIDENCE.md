# Evidence index and confidence

## Evidence classes

- **Static:** Ghidra disassembly/decompilation, relocation tables, and official graphic-pack patches. Establishes what code/data says, not whether runtime dispatch reaches it.
- **Runtime:** Cemu layer logs, guest hook counters, and headset observations. Establishes behavior in the tested build and scene.
- **Controlled experiment:** one-variable change with a recorded before/after result. Strongest for causality, but only within the tested environment.
- **Inference:** a plausible interpretation of evidence that still needs a direct probe.

## High-confidence findings

| Finding | Evidence |
|---|---|
| The game target is v4.2 checksum `0x9F0A90B7` | Cemu module log; research log §0 |
| GX2 import call sites must be resolved through relocation records | `GX2CopyColorBufferToScanBuffer` symbol and call-site mapping; §§0–3 |
| TV/DRC copy sites can emit marker clears decoded by the layer | Runtime marker logs; §7 |
| The pose mailbox handshake and stereo projection submission have run | Runtime ack, token and submit logs; §§8.5, 8.13 |
| `[camera]+0xD0` candidate path was not entered in the measured live race | Entry counters remained zero while stereo frames submitted; §13 |
| `context+0` is a projection matrix | Upload helper receives the pointer and runtime capture matches projection behavior; §§8.2, 9 |

## Medium or unresolved

| Finding | Why unresolved |
|---|---|
| TV and DRC can serve as left/right source images | Distinct images observed, but frame pairing was not consistently locked |
| `FUN_024B0060` is the live camera-update route | It is active per-frame camera/viewport work; actual view-matrix consumer is not yet identified |
| Full second scene draw is feasible | Render-state and target side effects have not been validated |
| A full live headset camera pose can be composed at a camera accessor | Correct architecture is motivated by BetterVR, but the corresponding live MK8 site is unknown |

## Superseded claims

The findings log is chronological. Later sections explicitly correct earlier ones. Consult [Current status](STATUS.md) for the current reading, then use the log for the complete history and raw pointers. In particular:

- §6.1’s description of a present hook was corrected in §8.7.
- §6.1/§8.7’s setup-versus-render interpretation was corrected in §8.8.
- §11–12’s candidate camera handoff was shown dormant by runtime measurement in §13.
- Early reports of an unsafe pass hook were refined in §8.1; a corrected trampoline worked, though that path remains a poor live camera solution.


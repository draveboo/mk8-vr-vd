"""Run the MK8 pose-latch PPC helper with synthetic memory, offline.

Mirrors tools/test_mario_touch.py: assembles the graphic pack's codecave into a
tiny PPC interpreter, feeds it a synthetic CTPH mailbox, and checks that
mkPoseRead latches exactly the host-published words under the seqlock rules and
rejects everything else. No Cemu and no headset are involved.
"""
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]           # ...\mk8-vr\src-fork
asm = (ROOT.parent / 'graphicPacks/MarioKart8_VR/patch_vr.asm').read_text(encoding='utf-8')

f32 = lambda v: struct.unpack('>f', struct.pack('>f', v))[0]
bits = lambda v: struct.unpack('>I', struct.pack('>f', v))[0]
flt = lambda v: struct.unpack('>f', struct.pack('>I', v))[0]

labels, mem, code, pc = {}, {}, {}, 0x1800000
for raw in asm.splitlines():
    line = raw.split(';', 1)[0].strip()
    if not line or line.startswith(('moduleMatches', '.origin', '0x')):
        continue
    if line.endswith(':'):
        assert line[:-1] not in labels, line
        labels[line[:-1]] = pc
        continue
    if line.startswith('.int'):
        v = int(line.split()[1], 0)
        mem[pc] = v
        if v == 0x7C2004AC:
            code[pc] = ['raw', v]
    elif line.startswith('.float'):
        mem[pc] = bits(float(line.split()[1]))
    else:
        code[pc] = line.replace(',', ' ').split()
    pc += 4


class CPU:
    def __init__(self):
        self.m = mem.copy()
        self.r = [0x25000000 + i * 256 for i in range(32)]
        self.r[1] = 0x31000000
        self.f = [i + .375 for i in range(32)]
        self.lr = 0
        self.cr = 0
        self.events = []

    def val(self, t):
        if '@' in t:
            k, p = t.split('@')
            v = labels[k]
            return (v + 0x8000) >> 16 if p == 'ha' else ((v & 65535) ^ 32768) - 32768
        return labels[t] if t in labels else int(t, 0)

    def addr(self, t):
        off, r = t[:-1].split('(')
        i = int(r[1:])
        return ((self.r[i] if i else 0) + self.val(off)) & 0xffffffff

    def set(self, k, off, v):
        self.m[labels[k] + off] = v & 0xffffffff

    def get(self, k, off=0):
        return self.m.get(labels[k] + off, 0)

    def vec(self, p, v):
        for i, x in enumerate(v):
            self.m[p + 4 * i] = bits(x)

    def readvec(self, p, n=3):
        return [flt(self.m.get(p + 4 * i, 0)) for i in range(n)]

    def call(self, label):
        pc = labels[label]
        self.lr = 0x7ffffffc
        for _ in range(20000):
            if pc == 0x7ffffffc:
                return
            if pc not in code:
                self.events.append(pc)
                pc = self.lr
                continue
            op, *a = code[pc]
            nxt = pc + 4
            r = lambda t: int(t[1:])
            signed = lambda v: (v ^ 0x80000000) - 0x80000000
            if op == 'raw':
                pass
            elif op in ('li', 'lis'):
                self.r[r(a[0])] = (self.val(a[1]) << (16 if op == 'lis' else 0)) & 0xffffffff
            elif op == 'addi':
                self.r[r(a[0])] = ((self.r[r(a[1])] if r(a[1]) else 0) + self.val(a[2])) & 0xffffffff
            elif op == 'add':
                self.r[r(a[0])] = (self.r[r(a[1])] + self.r[r(a[2])]) & 0xffffffff
            elif op in ('stw', 'stwu'):
                at = self.addr(a[1])
                self.m[at] = self.r[r(a[0])]
                if op == 'stwu':
                    self.r[int(a[1][a[1].index('(r') + 2:-1])] = at
            elif op == 'lwz':
                self.r[r(a[0])] = self.m.get(self.addr(a[1]), 0)
            elif op == 'lfs':
                self.f[r(a[0])] = flt(self.m.get(self.addr(a[1]), 0))
            elif op == 'stfs':
                self.m[self.addr(a[1])] = bits(self.f[r(a[0])])
            elif op in ('fmuls', 'fadds', 'fsubs'):
                x, y = self.f[r(a[1])], self.f[r(a[2])]
                self.f[r(a[0])] = f32(x * y if op == 'fmuls' else x + y if op == 'fadds' else x - y)
            elif op == 'andi.':
                v = self.r[r(a[1])] & self.val(a[2])
                self.r[r(a[0])] = v
                self.cr = (v > 0)
            elif op in ('cmpw', 'cmpwi'):
                x = signed(self.r[r(a[0])])
                y = signed(self.val(a[1]) if op.endswith('i') else self.r[r(a[1])])
                self.cr = (x > y) - (x < y)
            elif op == 'mflr':
                self.r[r(a[0])] = self.lr
            elif op == 'mtlr':
                self.lr = self.r[r(a[0])]
            elif op == 'blr':
                nxt = self.lr
            elif op in ('b', 'bl', 'beq', 'bne'):
                take = {'b': True, 'bl': True, 'beq': self.cr == 0, 'bne': self.cr != 0}[op]
                if take:
                    if op == 'bl':
                        self.lr = nxt
                    nxt = self.val(a[0])
            else:
                raise AssertionError((op, a))
            pc = nxt
        raise AssertionError('instruction budget ' + label)


checks = 0


def check(ok, msg):
    global checks
    assert ok, msg
    checks += 1


WORD = 0x43545048
SEQ = 0x00010040          # even, non-zero


def mailbox(seq=SEQ, version_flag=1, fill=None):
    c = CPU()
    c.set('mkPoseHeader', 0, WORD)
    c.set('mkPoseHeader', 4, 5)
    c.set('mkPoseHeader', 8, seq)
    c.set('mkPoseHeader', 12, version_flag)
    for k in range(48):
        c.set('mkPoseHeader', 24 + k * 4, 0xA0000000 + k if fill is None else fill[k])
    return c


def latch(c):
    return [c.get('mkPoseLatch0', k * 4) for k in range(49)]


# 1. A valid, complete publication latches verbatim.
c = mailbox()
before = c.r.copy()
c.call('mkPoseRead')
slot = latch(c)
check(slot[0] == SEQ, 'sequence stored in latch word 0')
check(all(slot[1 + k] == 0xA0000000 + k for k in range(48)), 'all 48 payload words latched')
check(c.get('mkPoseHeader', 24) == 0xA0000000, 'first payload word is the left-eye matrix base')
check(c.get('mkPoseHeader', 212) == 0xA000002F, 'last payload word is the scalars tail')
check(c.r[1:] == before[1:], 'mkPoseRead preserves r1..r31 (mkCopyMarker needs r6 and r3/r4)')
check(c.r[0] == 0x7ffffffc, 'r0 is volatile and carries LR across the call, as the ABI allows')

# 2. Word 6 (payload[3]) is the left-eye matrix base; word 18 (payload[15]) the right eye.
c = CPU()
c.set('mkPoseHeader', 0, WORD)
c.set('mkPoseHeader', 8, SEQ)
c.set('mkPoseHeader', 12, 1)
c.set('mkPoseHeader', 24, bits(1.5))       # left eye m00
c.set('mkPoseHeader', 24 + 12 * 4, bits(-2.25))  # right eye m00 (payload[15])
c.call('mkPoseRead')
check(abs(flt(c.get('mkPoseLatch0', 4)) - 1.5) < 1e-6, 'left-eye m00 latched as float')
check(abs(flt(c.get('mkPoseLatch0', 4 + 48)) + 2.25) < 1e-6, 'right-eye base at latch+52 matches SM3DW layout')

# 3. Every rejection path leaves the slot invalidated.
for name, kw in [('odd sequence', dict(seq=SEQ + 1)),
                 ('zero sequence', dict(seq=0)),
                 ('payload[0] != 1', dict(version_flag=0))]:
    c = mailbox(**kw)
    c.call('mkPoseRead')
    check(c.get('mkPoseLatch0', 0) == 0, 'rejected and invalidated: ' + name)
    check(all(c.get('mkPoseLatch0', 4 + k * 4) == 0 for k in range(48)),
          'rejected publication leaves no partial payload: ' + name)

# 4. A fresh valid publication restores the latch after a rejection.
c = mailbox(seq=SEQ)
c.call('mkPoseRead')
check(c.get('mkPoseLatch0', 0) == SEQ, 'valid publication after rejection latches again')

# 5. Sequences are opaque: any even non-zero value is accepted, no cross-check.
for seq in (2, 0x1000002, 0xFFFFFFFE):
    c = mailbox(seq=seq)
    c.call('mkPoseRead')
    check(c.get('mkPoseLatch0', 0) == seq, 'even sequence %d accepted' % seq)

# 6. mkHeadLook: view-space rotation by the latched head rotation.
def headlook(enable=1, seq=1, D=((1, 0, 0), (0, 1, 0), (0, 0, 1))):
    c = CPU()
    c.set('mkHeadLookEnable', 0, enable)
    c.set('mkPoseLatch0', 0, seq)
    for k in range(3):
        for j in range(3):
            c.set('mkPoseLatch0', 4 + 4 * (k * 4 + j), bits(D[k][j]))
    return c


SRC, DST = 0x28000000, 0x29000000
M = [1.5, 0.25, -0.75, 12.0,
     -0.5, 2.0, 0.125, -3.5,
     0.875, -0.25, 1.25, 40.0,
     0.0, 0.0, 0.0, 1.0]


def run_headlook(D):
    c = headlook(D=D)
    c.vec(SRC, M)
    c.vec(DST, [999.0] * 16)
    c.r[3], c.r[4] = SRC, DST
    regs, fprs = c.r[14:].copy(), c.f[14:].copy()
    c.call('mkHeadLook')
    out = [flt(c.m.get(DST + 4 * i, 0)) for i in range(16)]
    return c, out, regs, fprs


# A real rigid rotation (transpose applied), not the identity.
rot = ((0.6, 0.8, 0.0), (-0.8, 0.6, 0.0), (0.0, 0.0, 1.0))
c, out, regs, fprs = run_headlook(rot)
want = [sum(rot[k][i] * M[k * 4 + j] for k in range(3)) for i in range(3) for j in range(4)]
check(all(abs(a - b) < 1e-5 for a, b in zip(out[:12], want)), 'mkHeadLook applies the transposed rotation')
check(out[12:] == [0.0, 0.0, 0.0, 1.0], 'mkHeadLook leaves the w row untouched')
check(c.r[14:] == regs and c.f[14:] == fprs, 'mkHeadLook preserves nonvolatile GPRs and FPRs')

# Identity head pose is a no-op, so a neutral head means the native camera.
c, out, _, _ = run_headlook(((1, 0, 0), (0, 1, 0), (0, 0, 1)))
check(all(abs(a - b) < 1e-6 for a, b in zip(out, M)), 'identity head rotation is a no-op')

# 180-degree yaw about z negates the x/y rows, leaves the z and w rows alone.
c, out, _, _ = run_headlook(((-1, 0, 0), (0, -1, 0), (0, 0, 1)))
check(all(abs(out[i] + M[i]) < 1e-5 for i in range(8)), 'yaw-180 negates the x/y basis and translation')
check(all(abs(out[i] - M[i]) < 1e-5 for i in (8, 9, 10, 11)), 'yaw-180 leaves the z row untouched')
check(out[12:] == [0.0, 0.0, 0.0, 1.0], 'yaw-180 leaves the w row untouched')

# Inert by default: disabled gate leaves the destination as it was.
c = headlook(enable=0, D=rot)
c.vec(SRC, M)
c.vec(DST, [999.0] * 16)
c.r[3], c.r[4] = SRC, DST
c.call('mkHeadLook')
check([flt(c.m.get(DST + 4 * i, 0)) for i in range(16)] == [999.0] * 16, 'disabled gate is inert')

# Inert with no fresh pose: a stale/absent latch must not rotate anything.
c = headlook(seq=0, D=rot)
c.vec(SRC, M)
c.vec(DST, [999.0] * 16)
c.r[3], c.r[4] = SRC, DST
c.call('mkHeadLook')
check([flt(c.m.get(DST + 4 * i, 0)) for i in range(16)] == [999.0] * 16, 'stale latch is inert')

# Source and destination are separate buffers: the source must never change.
c = headlook(D=rot)
c.vec(SRC, M)
c.vec(DST, [0.0] * 16)
c.r[3], c.r[4] = SRC, DST
c.call('mkHeadLook')
check([flt(c.m.get(SRC + 4 * i, 0)) for i in range(16)] == M, 'mkHeadLook never mutates the source')

# 7. mkHeadLookRight: M' = M * D (row-vector camera rotation), upper 3x3 only.
def headlookR(enable=1, seq=1, D=((1, 0, 0), (0, 1, 0), (0, 0, 1))):
    c = CPU()
    c.set('mkHeadLookEnable', 0, enable)
    c.set('mkPoseLatch0', 0, seq)
    for k in range(3):
        for j in range(3):
            c.set('mkPoseLatch0', 4 + 4 * (k * 4 + j), bits(D[k][j]))
    return c


def want_right(D=rot):
    w = [0.0] * 16
    for i in range(3):
        for j in range(3):
            w[i * 4 + j] = sum(M[i * 4 + k] * D[k][j] for k in range(3))
        w[i * 4 + 3] = M[i * 4 + 3]
    w[12:16] = M[12:16]
    return w


c = headlookR(D=rot)
c.vec(SRC, M)
c.vec(DST, [999.0] * 16)
c.r[3], c.r[4] = SRC, DST
c.call('mkHeadLookRight')
out = [flt(c.m.get(DST + 4 * i, 0)) for i in range(16)]
check(all(abs(a - b) < 1e-5 for a, b in zip(out, want_right())), 'mkHeadLookRight applies M*D on the upper 3x3')
check([flt(c.m.get(SRC + 4 * i, 0)) for i in range(16)] == M, 'mkHeadLookRight never mutates the source')

c = headlookR(D=((1, 0, 0), (0, 1, 0), (0, 0, 1)))
c.vec(SRC, M)
c.vec(DST, [999.0] * 16)
c.r[3], c.r[4] = SRC, DST
c.call('mkHeadLookRight')
check(all(abs(a - b) < 1e-6 for a, b in zip((flt(c.m.get(DST + 4 * i, 0)) for i in range(16)), M)),
      'mkHeadLookRight with an identity head pose is a no-op')

for name, kw in (('disabled', dict(enable=0)), ('stale', dict(seq=0))):
    c = headlookR(D=rot, **kw)
    c.vec(SRC, M)
    c.vec(DST, [999.0] * 16)
    c.r[3], c.r[4] = SRC, DST
    c.call('mkHeadLookRight')
    check([flt(c.m.get(DST + 4 * i, 0)) for i in range(16)] == [999.0] * 16,
          'mkHeadLookRight inert when ' + name)

# 8. mkCameraSubmit: the 0x025D887C hook over FUN_025D7884's stack camera block.
BLK, CONSUME = 0x2A000000, 0x7FFFFFF0
labels['mkCameraConsumeNative'] = CONSUME
TAIL = [1.0, 2.0, 3.0, 4.0]


def run_submit(enable=1, D=rot):
    c = headlookR(enable=enable, D=D)
    c.vec(BLK, M + TAIL)
    c.r[3], c.r[4] = 0x12345678, BLK
    c.call('mkCameraSubmit')
    got = [flt(c.m.get(BLK + 4 * i, 0)) for i in range(20)]
    dump = [flt(c.m.get(labels['mkDiagMatrix3'] + 4 * i, 0)) for i in range(16)]
    return c, got, dump


c, got, dump = run_submit()
check(all(abs(a - b) < 1e-5 for a, b in zip(got[:16], want_right())), 'mkCameraSubmit rotates the stack camera block')
check(got[16:] == TAIL, 'mkCameraSubmit leaves the trailing 4 words of the block alone')
check(all(abs(a - b) < 1e-6 for a, b in zip(dump, M)), 'mkCameraSubmit dumps the game matrix before rotating')
check(c.r[3] == 0x12345678 and c.r[4] == BLK, 'mkCameraSubmit passes the original call arguments through')

c, got, dump = run_submit(D=((1, 0, 0), (0, 1, 0), (0, 0, 1)))
check(all(abs(a - b) < 1e-6 for a, b in zip(got[:16], M)), 'mkCameraSubmit with a neutral head pose is a no-op')

c, got, dump = run_submit(enable=0)
check(all(abs(a - b) < 1e-6 for a, b in zip(got[:16], M)), 'mkCameraSubmit is inert while the gate is 0')

# 9. mkLoadTokMagic: f1 = menu magic (0.21875 -> layer token 0x10000) or pose magic.
for used, want1, name in ((0, 0.1875, 'pose-token'), (1, 0.21875, 'menu')):
    c = CPU()
    c.set('mkMenuUsed', 0, used)
    c.call('mkLoadTokMagic')
    check(abs(c.f[1] - want1) < 1e-6, 'mkLoadTokMagic loads the %s magic (%.5f)' % (name, want1))
    check(c.r[14:] == [0x25000000 + i * 256 for i in range(14, 32)],
          'mkLoadTokMagic leaves the nonvolatile GPRs alone (r11/r12 are volatile)')

print('%d production PPC pose-latch + head-look + camera-hook + menu checks passed; headset/game test pending' % checks)

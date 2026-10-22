import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.mem.Memory;

import java.io.FileWriter;
import java.io.PrintWriter;

public class Step4 extends GhidraScript {
    static PrintWriter w;
    static DecompInterface DI;

    Address A(long v) {
        return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v);
    }

    DecompInterface di() {
        if (DI == null) {
            DI = new DecompInterface();
            DI.openProgram(currentProgram);
        }
        return DI;
    }

    Function ensureFunc(long addr) {
        Address a = A(addr);
        Function f = getFunctionContaining(a);
        if (f == null && currentProgram.getListing().getInstructionAt(a) != null) f = createFunction(a, null);
        if (f == null) { disassemble(a); f = createFunction(a, null); }
        return f;
    }

    void decompile(long addr, String title, int maxChars) {
        Function f = ensureFunc(addr);
        w.println("--- decompile " + title + " : " + (f == null ? "NULL" : f.getName() + " " + f.getBody()) + " ---");
        if (f == null) return;
        try {
            DecompileResults res = di().decompileFunction(f, 120, monitor);
            if (res.decompileCompleted() && res.getDecompiledFunction() != null) {
                String c = res.getDecompiledFunction().getC();
                if (c.length() > maxChars) c = c.substring(0, maxChars) + "\n/* TRUNCATED */";
                w.println(c);
            } else w.println("  FAIL: " + res.getErrorMessage());
        } catch (Exception e) { w.println("  EXC " + e); }
        w.println();
    }

    void allRefsTo(long addr, String label) {
        Address a = A(addr);
        w.println("refs to " + label + " " + a + ":");
        int n = 0;
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(a);
        while (rit.hasNext()) {
            Reference r = rit.next();
            w.println("  " + r.getReferenceType() + " from " + r.getFromAddress()
                + " (" + (currentProgram.getListing().getInstructionContaining(r.getFromAddress()) != null ? "instr" : "data") + ")");
            n++;
            if (n > 40) { w.println("  ...more"); break; }
        }
        if (n == 0) w.println("  (none)");
    }

    void hexdump(long addr, int len) {
        try {
            Memory mem = currentProgram.getMemory();
            byte[] b = new byte[len];
            mem.getBytes(A(addr), b);
            for (int i = 0; i < len; i += 16) {
                StringBuilder sb = new StringBuilder(String.format("  %08x  ", addr + i));
                StringBuilder asc = new StringBuilder();
                for (int j = i; j < Math.min(i + 16, len); j++) {
                    sb.append(String.format("%02x ", b[j]));
                    char c = (char)(b[j] & 0xff);
                    asc.append((c >= 32 && c < 127) ? c : '.');
                }
                w.println(sb + " |" + asc + "|");
            }
        } catch (Exception e) { w.println("  dump fail " + e); }
    }

    @Override
    public void run() throws Exception {
        String outDir = "D:\\mk8-VR\\work\\re-out\\";
        w = new PrintWriter(new FileWriter(outDir + "step4.txt"));

        w.println("==== stub body bytes ====");
        hexdump(0xc0008860L, 64);

        w.println("\n==== all refs (data+call) to present/clear funcs ====");
        long[] addrs = {0x027afb74L, 0x02739424L, 0x024d4ee0L, 0x02734c3cL,
                        0x0273929cL, 0x027396e4L, 0x027afe24L, 0x024d4c58L};
        String[] labels = {"CopyToScan-heavy", "CopyToScan-small", "flip/swap TV", "flip/swap clone",
                           "clear-dispatch", "SetTVBuffer-wrap", "clear2", "DrawDone-wrap"};
        for (int i = 0; i < addrs.length; i++) allRefsTo(addrs[i], labels[i]);

        w.println("\n==== decompiles ====");
        decompile(0x0250e364L, "camera-update wrapper", 8000);
        decompile(0x0250e74cL, "frame dispatcher", 6000);
        decompile(0x0250e8e8L, "frame caller", 6000);
        decompile(0x0275eae8L, "post-copy helper", 6000);
        decompile(0x024af7b4L, "after-frame-landmark 1", 8000);
        decompile(0x024af8f4L, "after-frame-landmark 2", 8000);
        decompile(0x024afd28L, "after-frame-landmark 3", 8000);
        decompile(0x0250e41cL, "BIG render loop", 30000);

        w.close();
        println("step4 done");
    }
}

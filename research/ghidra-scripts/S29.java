import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import java.io.FileWriter;
import java.io.PrintWriter;

// S29: disassemble + create the camera functions, then decompile. S28 returned NULL
// because the project has almost no functions defined (auto-analysis was ~2s).
public class S29 extends GhidraScript {
    static PrintWriter w;
    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void showMaps() {
        w.println("--- memory blocks ---");
        w.println("  imageBase = " + currentProgram.getImageBase());
        for (MemoryBlock b : currentProgram.getMemory().getBlocks()) {
            w.println("  " + b.getName() + "  start=" + b.getStart() + " end=" + b.getEnd()
                + " r=" + b.isRead() + " x=" + b.isExecute());
        }
        w.println();
    }

    void ensure(long addr, int count) {
        Address a = A(addr);
        MemoryBlock b = currentProgram.getMemory().getBlock(a);
        if (b == null) { w.println("  [skip] 0x" + Long.toHexString(addr) + " is not in any block"); return; }
        Address cur = a;
        for (int i = 0; i < count; i++) {
            if (!disassemble(cur)) break;
            Instruction ins = getInstructionAt(cur);
            if (ins == null) break;
            cur = ins.getAddress().add(ins.getLength());
        }
        Function f = getFunctionContaining(a);
        if (f == null || !f.getEntryPoint().equals(a)) {
            f = createFunction(a, null);
        }
        if (f == null) w.println("  [warn] no function at 0x" + Long.toHexString(addr));
        else w.println("  [ok] function " + f.getName() + " " + f.getBody());
    }

    void decompileAt(long addr, String title, int maxChars) {
        w.println("--- decompile " + title + " (0x" + Long.toHexString(addr) + ") ---");
        Function f = getFunctionContaining(A(addr));
        if (f == null) { w.println("  NULL"); w.println(); return; }
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        DecompileResults res = di.decompileFunction(f, 300, monitor);
        if (res.decompileCompleted() && res.getDecompiledFunction() != null) {
            String c = res.getDecompiledFunction().getC();
            if (c.length() > maxChars) c = c.substring(0, maxChars) + "\n/* TRUNCATED */";
            w.println(c);
        } else w.println("  FAIL " + res.getErrorMessage());
        w.println();
    }

    void disasm(long start, int count, String title) {
        w.println("--- disasm " + title + " from 0x" + Long.toHexString(start) + " ---");
        Listing lst = currentProgram.getListing();
        Instruction ins = lst.getInstructionAt(A(start));
        int n = 0;
        while (ins != null && n < count) {
            w.println("  " + ins.getAddress() + "  " + ins);
            ins = ins.getNext(); n++;
        }
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s29.txt"));
        w.println("S29: camera accessor confirmation (MK8 v4.2)");
        w.println();
        showMaps();

        long[] targets = { 0x025D7884L, 0x024CE59CL, 0x024B0060L };
        for (long t : targets) ensure(t, 800);

        // the section 6.4 camera-copy site
        ensure(0x025d8780, 200);
        w.println("=== camera copy site ===");
        disasm(0x025d8780, 80, "around 0x025d87e8");

        decompileAt(0x025D7884, "FUN_025D7884 (flagged camera-matrix copy)", 9000);
        decompileAt(0x024B0060, "FUN_024B0060 (per-frame camera/viewport update)", 14000);

        w.close();
    }
}

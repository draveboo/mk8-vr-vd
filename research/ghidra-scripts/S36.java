import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import java.io.FileWriter;
import java.io.PrintWriter;

// S36: FUN_024dd9fc is what the NORMAL slot path calls to set the camera
// (FUN_025d8f84). Find where the view matrix lands.
public class S36 extends GhidraScript {
    static PrintWriter w;
    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void ensure(long addr, int count) {
        Address a = A(addr);
        if (currentProgram.getMemory().getBlock(a) == null) { w.println("  [skip] " + a); return; }
        Address cur = a;
        for (int i = 0; i < count; i++) {
            if (!disassemble(cur)) break;
            Instruction ins = getInstructionAt(cur);
            if (ins == null) break;
            cur = ins.getAddress().add(ins.getLength());
        }
        Function f = getFunctionContaining(a);
        if (f == null || !f.getEntryPoint().equals(a)) f = createFunction(a, null);
        if (f != null) w.println("  [ok] " + f.getName() + " " + f.getBody());
    }

    void decompileAt(long addr, String title, int max) {
        w.println("=== " + title + " (0x" + Long.toHexString(addr) + ") ===");
        Function f = getFunctionContaining(A(addr));
        if (f == null) { w.println("  NULL"); w.println(); return; }
        w.println("  " + f.getName() + " " + f.getBody());
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        DecompileResults res = di.decompileFunction(f, 300, monitor);
        if (res.decompileCompleted() && res.getDecompiledFunction() != null) {
            String c = res.getDecompiledFunction().getC();
            if (c.length() > max) c = c.substring(0, max) + "\n/* TRUNCATED */";
            w.println(c);
        } else w.println("  FAIL " + res.getErrorMessage());
        w.println();
    }

    void disasm(long start, int count, String title) {
        w.println("=== " + title + " from 0x" + Long.toHexString(start) + " ===");
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
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s36.txt"));
        w.println("S36: the NORMAL slot camera path (MK8 v4.2)");
        w.println();
        ensure(0x024dd9fc, 500);
        ensure(0x024de0d8, 500);
        disasm(0x024dd9fc, 35, "FUN_024dd9fc prologue");
        decompileAt(0x024dd9fc, "FUN_024dd9fc (normal camera setup)", 9000);
        decompileAt(0x024de0d8, "FUN_024de0d8", 6000);
        w.close();
    }
}

import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import java.io.FileWriter;
import java.io.PrintWriter;

// S30: FUN_025D7884's other branches. The armed hook sits in the r4==0x1d case,
// which never ran, so find where the camera matrix is handled for the other cases.
public class S30 extends GhidraScript {
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
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s30.txt"));
        w.println("S30: FUN_025D7884 branch analysis (MK8 v4.2)");
        w.println();
        ensure(0x025D7884, 900);
        ensure(0x023abbe8, 200);

        // the branch our hook is NOT in, plus the tail of the switch
        disasm(0x025d88a0, 70, "case != 0x1d (0x025d88a0)");
        disasm(0x025d8a00, 60, "later in FUN_025D7884");

        decompileAt(0x025D7884, "FUN_025D7884 (full: all camera cases)", 22000);
        decompileAt(0x023abbe8, "FUN_023abbe8 (manager getter used by the camera path)", 2500);
        w.close();
    }
}

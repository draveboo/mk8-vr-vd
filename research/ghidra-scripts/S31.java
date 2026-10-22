import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;

// S31: is FUN_02566c7c the single consumer of the camera block? And what cases does
// FUN_025D7884 dispatch? Then hook the funnel instead of one case.
public class S31 extends GhidraScript {
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

    void refs(long addr, String title) {
        w.println("=== refs to 0x" + Long.toHexString(addr) + " : " + title + " ===");
        Reference[] rs = getReferencesTo(A(addr));
        if (rs.length == 0) w.println("  (none)");
        for (Reference r : rs) {
            Function f = getFunctionContaining(r.getFromAddress());
            w.println("  " + r.getFromAddress() + "  " + r.getReferenceType() + "  in " + (f == null ? "?" : f.getName()));
        }
        w.println();
    }

    void decompileAt(long addr, String title, int maxChars) {
        w.println("=== decompile " + title + " (0x" + Long.toHexString(addr) + ") ===");
        Function f = getFunctionContaining(A(addr));
        if (f == null) { w.println("  NULL"); w.println(); return; }
        w.println("  function " + f.getName() + " " + f.getBody());
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

    // every case id compared inside FUN_025D7884, and every 0xd0/0xd4 camera-block reader
    void scan(long start, long end) {
        w.println("=== cmpwi/cmplwi immediates in 0x" + Long.toHexString(start) + "..0x" + Long.toHexString(end) + " ===");
        Listing lst = currentProgram.getListing();
        Instruction ins = lst.getInstructionAt(A(start));
        int n = 0;
        while (ins != null && ins.getAddress().getOffset() < end && n < 4000) {
            String s = ins.toString();
            if (s.startsWith("cmpwi") || s.startsWith("cmplwi")) w.println("  " + ins.getAddress() + "  " + ins);
            n++;
            ins = ins.getNext();
        }
        w.println();
        w.println("=== 'addi rX,rY,0xd0' / 0xd4 readers across the program ===");
        InstructionIterator it = lst.getInstructions(true);
        int m = 0;
        while (it.hasNext() && m < 80) {
            Instruction i2 = it.next();
            String s2 = i2.toString();
            if ((s2.startsWith("addi") || s2.startsWith("addic")) && (s2.endsWith(",0xd0") || s2.endsWith(",0xd4"))) {
                Function f = getFunctionContaining(i2.getAddress());
                w.println("  " + i2.getAddress() + "  " + i2 + "   in " + (f == null ? "?" : f.getName()));
                m++;
            }
        }
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s31.txt"));
        w.println("S31: camera-block consumer funnel (MK8 v4.2)");
        w.println();

        // drop the overlapping function a previous pass created mid-body
        Function bogus = getFunctionAt(A(0x025d8780));
        if (bogus != null) { removeFunction(bogus); w.println("removed overlapping FUN_025d8780"); }
        ensure(0x025D7884, 1200);
        ensure(0x02566c7c, 400);

        refs(0x02566c7c, "consumers of the 20-word camera block");
        refs(0x025D7884, "callers of the dispatcher");

        scan(0x025D7884, 0x025D8F84);
        decompileAt(0x02566c7c, "FUN_02566c7c (camera-block consumer)", 8000);
        w.close();
    }
}

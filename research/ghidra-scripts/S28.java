import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;

// S28: confirm MK8's camera object / accessor, following BetterVR's getRenderCamera idea.
public class S28 extends GhidraScript {
    static PrintWriter w;
    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void decompile(long addr, String title, int maxChars) {
        w.println("--- decompile " + title + " (0x" + Long.toHexString(addr) + ") ---");
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

    void disasm(long start, long end, String title) {
        w.println("--- disasm " + title + " 0x" + Long.toHexString(start) + "..0x" + Long.toHexString(end) + " ---");
        Listing lst = currentProgram.getListing();
        Instruction ins = lst.getInstructionAt(A(start));
        int n = 0;
        while (ins != null && ins.getAddress().getOffset() < end && n < 80) {
            w.println("  " + ins.getAddress() + "  " + ins);
            ins = ins.getNext(); n++;
        }
        w.println();
    }

    void xrefsTo(long addr, String title) {
        w.println("--- refs to 0x" + Long.toHexString(addr) + " : " + title + " ---");
        Reference[] refs = getReferencesTo(A(addr));
        if (refs.length == 0) w.println("  (none)");
        for (Reference r : refs) {
            Function f = getFunctionContaining(r.getFromAddress());
            w.println("  " + r.getFromAddress() + "  " + r.getReferenceType() + "  in " + (f == null ? "?" : f.getName()));
        }
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s28.txt"));
        w.println("S28: camera-object / accessor hunt (MK8 v4.2)");
        w.println();

        // section 6.4's candidate: 20-word (4x4+row) camera copy at [cam]+0xD0
        decompile(0x025D7884, "FUN_025D7884 (flagged camera-matrix copy site)", 9000);
        disasm(0x025d8780, 0x025d8860, "around 0x025d87e8");
        xrefsTo(0x025D7884, "callers of FUN_025D7884");

        // camera manager + per-frame camera update (the two other candidates)
        decompile(0x024CE59C, "FUN_024CE59C (camera manager)", 5000);
        decompile(0x024B0060, "FUN_024B0060 (per-frame camera/viewport update)", 16000);

        w.close();
    }
}

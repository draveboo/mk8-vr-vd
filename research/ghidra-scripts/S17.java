import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S17 extends GhidraScript {
    static PrintWriter w;

    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void decompile(long addr, String title, int maxChars) {
        w.println("--- decompile " + title + " (" + Long.toHexString(addr) + ") ---");
        Function f = getFunctionContaining(A(addr));
        if (f == null) { w.println("  NULL"); w.println(); return; }
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        DecompileResults res = di.decompileFunction(f, 180, monitor);
        if (res.decompileCompleted() && res.getDecompiledFunction() != null) {
            String c = res.getDecompiledFunction().getC();
            if (c.length() > maxChars) c = c.substring(0, maxChars) + "\n/* TRUNCATED */";
            w.println(c);
        } else w.println("  FAIL " + res.getErrorMessage());
        w.println();
    }

    void callersOf(long addr) {
        w.println("==== callers of " + Long.toHexString(addr) + " ====");
        Address a = A(addr);
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(a);
        int n = 0;
        while (rit.hasNext()) {
            Reference r = rit.next();
            if (!r.getReferenceType().isCall()) continue;
            Function cf = getFunctionContaining(r.getFromAddress());
            w.println("  call@" + r.getFromAddress() + " in " + (cf == null ? "?" : cf.getName() + " " + cf.getBody()));
            n++;
        }
        if (n == 0) w.println("  (none)");
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s17.txt"));

        // The pass table infrastructure.
        decompile(0x026a358cL, "FUN_026a358c (references pass table base)", 20000);
        decompile(0x026a453cL, "FUN_026a453c (references pass table base)", 20000);
        decompile(0x026a5d98L, "record driver FUN_026a5d98 (full)", 20000);

        callersOf(0x026a358cL);
        callersOf(0x026a453cL);

        w.close();
        println("s17 done");
    }
}

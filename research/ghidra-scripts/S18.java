import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S18 extends GhidraScript {
    static PrintWriter w;

    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void decompile(long addr, String title, int maxChars) {
        w.println("--- " + title + " (" + Long.toHexString(addr) + ") ---");
        Function f = getFunctionContaining(A(addr));
        if (f == null) { w.println("  NULL"); w.println(); return; }
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        DecompileResults res = di.decompileFunction(f, 200, monitor);
        if (res.decompileCompleted() && res.getDecompiledFunction() != null) {
            String c = res.getDecompiledFunction().getC();
            if (c.length() > maxChars) c = c.substring(0, maxChars) + "\n/* TRUNCATED */";
            w.println(c);
        } else w.println("  FAIL " + res.getErrorMessage());
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s18.txt"));
        decompile(0x02695df0L, "FUN_02695df0 (pass setup, caller of FUN_026a358c)", 18000);
        decompile(0x02699ba0L, "FUN_02699ba0 (caller of FUN_026a358c x2)", 18000);
        decompile(0x026984f0L, "FUN_026984f0 (caller of FUN_026a358c)", 18000);
        // The uniform upload helper and its neighbours.
        decompile(0x02695674L, "FUN_02695674 (matrix upload)", 4000);
        decompile(0x02695660L, "FUN_02695660 (uniform offset)", 4000);
        w.close();
        println("s18 done");
    }
}

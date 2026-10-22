import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S14 extends GhidraScript {
    static PrintWriter w;

    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void callersOf(long addr, String name) {
        w.println("==== callers (bl) of " + Long.toHexString(addr) + " : " + name + " ====");
        Address a = A(addr);
        if (getFunctionContaining(a) == null) { w.println("  <not a function>"); w.println(); return; }
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(a);
        int n = 0;
        while (rit.hasNext()) {
            Reference r = rit.next();
            if (!r.getReferenceType().isCall()) continue;
            Address from = r.getFromAddress();
            Function cf = getFunctionContaining(from);
            w.println("  call@" + from + " in " + (cf == null ? "?" : cf.getName() + " " + cf.getBody()));
            n++;
        }
        if (n == 0) w.println("  (no call refs)");
        w.println();
    }

    void decompile(long addr, String title, int maxChars) {
        w.println("--- decompile " + title + " ---");
        Function f = getFunctionContaining(A(addr));
        if (f == null) { w.println("  NULL"); w.println(); return; }
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        DecompileResults res = di.decompileFunction(f, 150, monitor);
        if (res.decompileCompleted() && res.getDecompiledFunction() != null) {
            String c = res.getDecompiledFunction().getC();
            if (c.length() > maxChars) c = c.substring(0, maxChars) + "\n/* TRUNCATED */";
            w.println(c);
        } else w.println("  FAIL " + res.getErrorMessage());
        w.println();
    }

    void callersDeep(long addr, String name, int depth) {
        w.println("==== callers-batch of " + Long.toHexString(addr) + " : " + name + " ====");
        Address a = A(addr);
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(a);
        int n = 0;
        while (rit.hasNext()) {
            Reference r = rit.next();
            if (!r.getReferenceType().isCall()) continue;
            Address from = r.getFromAddress();
            Function cf = getFunctionContaining(from);
            w.println("  call@" + from + " in " + (cf == null ? "?" : cf.getName() + " " + cf.getBody()));
            n++;
            if (n > 200) { w.println("  ...more"); break; }
        }
        if (n == 0) w.println("  (none)");
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s14.txt"));

        callersOf(0x02695674L, "matrix upload FUN_02695674");
        callersOf(0x02695660L, "candidate FUN_02695660");
        callersOf(0x02695720L, "deferred submit FUN_02695720");

        decompile(0x0209F8A4L, "per-renderable dispatch FUN_0209F8A4", 16000);
        decompile(0x020a5d14L, "FUN_020a5d14", 12000);
        decompile(0x020a5d94L, "FUN_020a5d94", 12000);
        decompile(0x0250e74cL, "TV path target 0x0250E74C", 12000);

        callersDeep(0x0209F8A4L, "per-renderable dispatch", 1);

        w.close();
        println("s14 done");
    }
}

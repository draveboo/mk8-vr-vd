import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S13 extends GhidraScript {
    static PrintWriter w;

    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void refsTo(long addr, String title) {
        w.println("==== refs/pointers to " + Long.toHexString(addr) + " : " + title + " ====");
        Address a = A(addr);
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(a);
        int n = 0;
        while (rit.hasNext()) {
            Reference r = rit.next();
            Address from = r.getFromAddress();
            Function cf = getFunctionContaining(from);
            w.println("  " + r.getReferenceType() + " from " + from + " in " + (cf == null ? "?" : cf.getName() + " " + cf.getBody()));
            n++;
        }
        if (n == 0) w.println("  (none)");
        w.println();
    }

    void callersOf(long addr) {
        w.println("==== callers (bl) of " + Long.toHexString(addr) + " ====");
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
        DecompileResults res = di.decompileFunction(f, 120, monitor);
        if (res.decompileCompleted() && res.getDecompiledFunction() != null) {
            String c = res.getDecompiledFunction().getC();
            if (c.length() > maxChars) c = c.substring(0, maxChars) + "\n/* TRUNCATED */";
            w.println(c);
        } else w.println("  FAIL " + res.getErrorMessage());
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s13.txt"));
        refsTo(0x026A5D98L, "record driver (pointer refs = vtable)");
        callersOf(0x026A5D98L);
        callersOf(0x020A5DB4L);
        callersOf(0x020A64E4L);
        decompile(0x020A5DB4L, "scene render entry FUN_020A5DB4", 14000);
        w.close();
        println("s13 done");
    }
}

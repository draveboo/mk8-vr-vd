import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S24 extends GhidraScript {
    static PrintWriter w;

    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void disasm(long start, long end, String title) {
        w.println("---- disasm " + title + " [" + Long.toHexString(start) + "," + Long.toHexString(end) + "] ----");
        Listing lst = currentProgram.getListing();
        Instruction ins = lst.getInstructionAt(A(start));
        while (ins != null && ins.getAddress().getOffset() < end) {
            w.println("  " + ins.getAddress() + "  " + ins);
            ins = ins.getNext();
        }
        w.println();
    }

    void decompile(long addr, String title, int maxChars) {
        w.println("--- decompile " + title + " (" + Long.toHexString(addr) + ") ---");
        Function f = getFunctionContaining(A(addr));
        if (f == null) { w.println("  NULL"); w.println(); return; }
        w.println("  function " + f.getName() + " " + f.getBody());
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

    void callsIn(long addr, String title) {
        w.println("==== calls inside " + title + " ====");
        Function f = getFunctionContaining(A(addr));
        if (f == null) { w.println("  NULL"); w.println(); return; }
        Listing lst = currentProgram.getListing();
        Instruction ins = lst.getInstructionAt(f.getEntryPoint());
        int n = 0;
        while (ins != null && f.getBody().contains(ins.getAddress())) {
            if (ins.getFlowType().isCall()) {
                String tgt = ins.toString().replaceAll(".*0x", "0x");
                w.println("  " + ins.getAddress() + "  " + ins);
                n++;
            }
            ins = ins.getNext();
        }
        w.println("  total calls=" + n);
        w.println();
    }

    void refsTo(long addr, String title, int max) {
        w.println("==== refs to " + Long.toHexString(addr) + " : " + title + " ====");
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(A(addr));
        int n = 0;
        while (rit.hasNext()) {
            Reference r = rit.next();
            Function cf = getFunctionContaining(r.getFromAddress());
            w.println("  " + r.getReferenceType() + " from " + r.getFromAddress() + " in " + (cf == null ? "?" : cf.getName()));
            if (++n >= max) { w.println("  ..."); break; }
        }
        if (n == 0) w.println("  (none)");
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s24.txt"));

        for (long fn : new long[]{0x0250E364L, 0x0250DAC4L, 0x0250DCECL, 0x0250D9E0L}) {
            Function f = getFunctionContaining(A(fn));
            w.println("FUN @" + Long.toHexString(fn) + " = " + (f==null?"NULL":f.getName()+" "+f.getBody()));
        }
        w.println();

        decompile(0x0250E364L, "FUN_0250E364 (camera/viewport update)", 6000);
        callsIn(0x0250E364L, "FUN_0250E364");

        decompile(0x0250DAC4L, "FUN_0250DAC4", 6000);
        callsIn(0x0250DAC4L, "FUN_0250DAC4");

        decompile(0x0250DCECL, "FUN_0250DCEC", 4000);

        // §6.1: FUN_0250E364 -> FUN_024B0060 (camera update). Confirm.
        refsTo(0x024B0060L, "callers of FUN_024B0060", 20);

        w.close();
        println("s24 done");
    }
}

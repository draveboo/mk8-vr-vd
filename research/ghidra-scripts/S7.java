import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

import java.io.FileWriter;
import java.io.PrintWriter;

public class S7 extends GhidraScript {
    static PrintWriter w;
    static DecompInterface DI;

    Address A(long v) {
        return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v);
    }

    DecompInterface di() {
        if (DI == null) {
            DI = new DecompInterface();
            DI.openProgram(currentProgram);
        }
        return DI;
    }

    Function ensureFunc(long addr) {
        Address a = A(addr);
        Function f = getFunctionContaining(a);
        if (f == null && currentProgram.getListing().getInstructionAt(a) != null) {
            f = createFunction(a, null);
        }
        if (f == null) {
            disassemble(a);
            f = createFunction(a, null);
        }
        return f;
    }

    void decompile(long addr, String title, int maxChars) {
        Function f = ensureFunc(addr);
        w.println("--- decompile " + title + " : " + (f == null ? "NULL" : f.getName() + " " + f.getBody()) + " ---");
        if (f == null) return;
        try {
            DecompileResults res = di().decompileFunction(f, 180, monitor);
            if (res.decompileCompleted() && res.getDecompiledFunction() != null) {
                String c = res.getDecompiledFunction().getC();
                if (c.length() > maxChars) c = c.substring(0, maxChars) + "\n/* TRUNCATED */";
                w.println(c);
            } else {
                w.println("  FAIL: " + res.getErrorMessage());
            }
        } catch (Exception e) {
            w.println("  EXC " + e);
        }
        w.println();
    }

    void callersOf(long addr, int depth, String indent) {
        Address a = A(addr);
        int shown = 0;
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(a);
        while (rit.hasNext()) {
            Reference r = rit.next();
            if (!(r.getReferenceType().isCall())) continue;
            Address from = r.getFromAddress();
            Function cf = getFunctionContaining(from);
            w.println(indent + "caller " + (cf == null ? "?" : cf.getName() + " " + cf.getBody()) + "  call@" + from);
            shown++;
            if (depth > 1 && cf != null) callersOf(cf.getEntryPoint().getOffset(), depth - 1, indent + "    ");
        }
        if (shown == 0) w.println(indent + "(no call refs to " + Long.toHexString(addr) + ")");
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s7.txt"));

        // function containing the uniform-reg uploads just before record helper FUN_02695720
        Function f = ensureFunc(0x02695698L);
        w.println("func containing 0x02695698: " + (f == null ? "?" : f.getName() + " " + f.getBody()));
        decompile(0x02695698L, "uniform-reg upload near record helper", 20000);

        decompile(0x020a5db4L, "scene render entry FUN_020A5DB4", 10000);
        decompile(0x020a64e4L, "render entry2 FUN_020A64E4", 10000);
        decompile(0x026438a8L, "FUN_026438A8 (calls pass-setup)", 12000);
        decompile(0x02644360L, "FUN_02644360 (calls pass-setup)", 8000);

        decompile(0x024af8f4L, "projection update FUN_024AF8F4", 12000);
        decompile(0x024afd28L, "projection update FUN_024AFD28", 12000);

        w.println("==== callers of FUN_02695720 (record) ====");
        callersOf(0x02695720L, 3, "  ");
        w.println("==== callers of FUN_024b25cc-family via data (skip) ; callers of FUN_0269e4dc ====");
        callersOf(0x0269e4dcL, 3, "  ");
        w.println("==== callers of FUN_026a04e4 ====");
        callersOf(0x026a04e4L, 2, "  ");

        w.close();
        println("s7 done");
    }
}

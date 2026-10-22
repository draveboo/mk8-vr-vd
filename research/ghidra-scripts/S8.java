import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

import java.io.FileWriter;
import java.io.PrintWriter;

public class S8 extends GhidraScript {
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
            boolean isCall = r.getReferenceType().isCall();
            Address from = r.getFromAddress();
            Function cf = getFunctionContaining(from);
            w.println(indent + (isCall ? "caller " : "ref ") + (cf == null ? "?" : cf.getName() + " " + cf.getBody()) + "  @" + from);
            shown++;
            if (isCall && depth > 1 && cf != null) callersOf(cf.getEntryPoint().getOffset(), depth - 1, indent + "    ");
        }
        if (shown == 0) w.println(indent + "(no refs to " + Long.toHexString(addr) + ")");
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s8.txt"));

        decompile(0x02695660L, "uniform reg id helper FUN_02695660", 4000);
        w.println("==== callers of FUN_02695674 (16-reg matrix upload) ====");
        callersOf(0x02695674L, 4, "  ");

        decompile(0x026a5d98L, "record driver FUN_026A5D98", 14000);

        w.println("==== callers of FUN_024af8f4 (eye-rect select) ====");
        callersOf(0x024af8f4L, 3, "  ");
        w.println("==== callers of FUN_024afd28 (rect to +0x440) ====");
        callersOf(0x024afd28L, 3, "  ");
        w.println("==== callers of FUN_024aee78 ====");
        callersOf(0x024aee78L, 3, "  ");

        decompile(0x025326dcL, "FUN_025326DC (calls big record)", 8000);
        decompile(0x02539408L, "FUN_02539408", 8000);

        w.close();
        println("s8 done");
    }
}

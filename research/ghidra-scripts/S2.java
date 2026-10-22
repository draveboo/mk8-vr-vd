import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

import java.io.FileWriter;
import java.io.PrintWriter;

public class S2 extends GhidraScript {
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
            DecompileResults res = di().decompileFunction(f, 120, monitor);
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

    void instrWindow(long addr, int before, int after) {
        Address a = A(addr);
        try {
            Instruction at = currentProgram.getListing().getInstructionAt(a);
            if (at == null) { disassemble(a); at = currentProgram.getListing().getInstructionAt(a); }
            if (at == null) { w.println("  no instruction at " + a); return; }
            InstructionIterator it = currentProgram.getListing().getInstructions(A(addr).subtract(before), true);
            int n = 0, total = before / 4 + after / 4;
            while (it.hasNext() && n < total) {
                Instruction i = it.next();
                w.println("    " + i.getAddress() + "  " + i + (i.getAddress().equals(a) ? "  <== SITE" : ""));
                n++;
            }
        } catch (Exception e) { w.println("  window fail " + e); }
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s2.txt"));

        w.println("==== decompiles ====");
        decompile(0x0250dac4L, "mode-switch draw helper (called by all 8 modes)", 20000);
        decompile(0x0250d9e0L, "mode-switch prologue helper", 8000);
        decompile(0x024b25ccL, "CallDisplayList #1 (device region)", 14000);
        decompile(0x024b3108L, "CallDisplayList #2", 14000);
        decompile(0x024b39d0L, "CallDisplayList #3", 14000);
        decompile(0x0269e3c8L, "Begin/End display list helper A", 6000);
        decompile(0x0269e4dcL, "Begin/End + CallDisplayList helper B", 6000);
        decompile(0x024e2688L, "DrawDone func 024E", 6000);
        decompile(0x0254a818L, "DrawDone func 0254", 6000);

        w.println("\n==== callers ====");
        w.println("callers of FUN_0250dac4 depth3:");
        callersOf(0x0250dac4L, 3, "  ");
        w.println("callers of FUN_024b25cc depth3:");
        callersOf(0x024b25ccL, 3, "  ");
        w.println("callers of FUN_024b3108 depth3:");
        callersOf(0x024b3108L, 3, "  ");
        w.println("callers of FUN_024b39d0 depth3:");
        callersOf(0x024b39d0L, 3, "  ");
        w.println("callers of FUN_0269e4dc depth3:");
        callersOf(0x0269e4dcL, 3, "  ");
        w.println("callers of FUN_0269e3c8 depth3:");
        callersOf(0x0269e3c8L, 3, "  ");
        w.println("callers of FUN_024e2688 depth3:");
        callersOf(0x024e2688L, 3, "  ");
        w.println("callers of FUN_0254a818 depth3:");
        callersOf(0x0254a818L, 3, "  ");
        w.println("callers of FUN_0250e41c depth3:");
        callersOf(0x0250e41cL, 3, "  ");

        w.close();
        println("s2 done");
    }
}

import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

import java.io.FileWriter;
import java.io.PrintWriter;

public class Step3 extends GhidraScript {
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
            DecompileResults res = di().decompileFunction(f, 90, monitor);
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
        if (shown == 0) w.println(indent + "(no call refs to " + a + ")");
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
        String outDir = "D:\\mk8-VR\\work\\re-out\\";
        w = new PrintWriter(new FileWriter(outDir + "step3.txt"));

        w.println("==== import stub bodies (.imp15) ====");
        instrWindow(0xc0008860L, 0x10, 0x50);

        w.println("\n==== decompiles ====");
        decompile(0x024b0060L, "KEY parent (calls both landmarks)", 20000);
        decompile(0x0273929cL, "clear func", 10000);
        decompile(0x027afe24L, "clear func2", 6000);
        decompile(0x024d4c58L, "DrawDone func", 6000);
        decompile(0x027396e4L, "SetTVBuffer func", 8000);

        w.println("\n==== callers of present/clear funcs ====");
        w.println("callers of FUN_027afb74 (CopyToScan heavy) depth3:");
        callersOf(0x027afb74L, 3, "  ");
        w.println("callers of FUN_02739424 (CopyToScan small) depth3:");
        callersOf(0x02739424L, 3, "  ");
        w.println("callers of FUN_024d4ee0 (flip/swap TV) depth3:");
        callersOf(0x024d4ee0L, 3, "  ");
        w.println("callers of FUN_02734c3c (flip/swap clone) depth3:");
        callersOf(0x02734c3cL, 3, "  ");
        w.println("callers of FUN_0273929c (clears) depth2:");
        callersOf(0x0273929cL, 2, "  ");
        w.println("callers of FUN_027396e4 (SetTVBuffer) depth2:");
        callersOf(0x027396e4L, 2, "  ");

        w.println("\n==== callers of higher chain ====");
        w.println("callers of FUN_024b0060 depth3:");
        callersOf(0x024b0060L, 3, "  ");
        w.println("callers of FUN_0250e74c depth2:");
        callersOf(0x0250e74cL, 2, "  ");

        w.close();
        println("step3 done");
    }
}

import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

import java.io.FileWriter;
import java.io.PrintWriter;

public class S3 extends GhidraScript {
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

    void allRefs(long addr, String title) {
        Address a = A(addr);
        w.println("refs (all types) to " + Long.toHexString(addr) + " [" + title + "]:");
        int shown = 0;
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(a);
        while (rit.hasNext()) {
            Reference r = rit.next();
            Function cf = getFunctionContaining(r.getFromAddress());
            w.println("   " + r.getReferenceType() + " from " + r.getFromAddress()
                + (cf != null ? "  in " + cf.getName() + " " + cf.getBody() : "  (data)"));
            shown++;
        }
        if (shown == 0) w.println("   (none)");
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
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s3.txt"));

        w.println("==== all refs to DL-submit / present functions (vtable hunt) ====");
        allRefs(0x024b25ccL, "DL submit A");
        allRefs(0x024b3108L, "DL submit B");
        allRefs(0x024b39d0L, "DL submit C");
        allRefs(0x024d4ee0L, "TV present");
        allRefs(0x02734c3cL, "present clone");
        allRefs(0x027b88d0L, "swap3");
        allRefs(0x027afb74L, "copy-to-scan heavy");
        allRefs(0x02739424L, "copy-to-scan small");

        w.println("\n==== callers up from scene render-mode chain ====");
        w.println("callers of FUN_020a5db4 depth4:");
        callersOf(0x020a5db4L, 4, "  ");
        w.println("callers of FUN_024cffac depth4:");
        callersOf(0x024cffacL, 4, "  ");
        w.println("callers of FUN_025d7884 depth4:");
        callersOf(0x025d7884L, 4, "  ");

        w.println("\n==== decompiles ====");
        decompile(0x02695720L, "caller of Begin/End/Call helper B", 8000);
        decompile(0x026a04e4L, "caller of Begin/End helper A", 20000);
        decompile(0x027b1cd4L, "DL-record-start helper", 8000);

        w.println("\n==== function containing 0x02416B74 (60FPS pack site 3) ====");
        Function f3 = ensureFunc(0x02416b74L);
        w.println("  " + (f3 == null ? "NULL" : f3.getName() + " " + f3.getBody()));
        instrWindow(0x02416b74L, 0x40, 0x80);
        decompile(0x02416b74L, "containing func of 0x02416B74", 20000);

        w.close();
        println("s3 done");
    }
}

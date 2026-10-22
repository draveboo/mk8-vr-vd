import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

import java.io.FileWriter;
import java.io.PrintWriter;

public class S6 extends GhidraScript {
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

    void printWindow(long addr, int beforeInsns) {
        try {
            Address a = A(addr);
            Instruction at = currentProgram.getListing().getInstructionAt(a);
            if (at == null) { disassemble(a); at = currentProgram.getListing().getInstructionAt(a); }
            if (at == null) return;
            InstructionIterator it = currentProgram.getListing().getInstructions(A(addr).subtract(beforeInsns * 4), true);
            int n = 0, total = beforeInsns + 14;
            while (it.hasNext() && n < total) {
                Instruction i = it.next();
                w.println("      " + i.getAddress() + "  " + i);
                n++;
            }
        } catch (Exception e) { w.println("    window fail " + e); }
    }

    void vcallAt(long addr, String label) {
        Function f = ensureFunc(addr);
        w.println("=== vcall " + label + " at 0x" + Long.toHexString(addr)
            + " in " + (f == null ? "?" : f.getName() + " " + f.getBody()) + " ===");
        printWindow(addr, 12);
        if (f != null) {
            decompile(f.getEntryPoint().getOffset(), label + " containing", 20000);
        }
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s6.txt"));

        // vcall sites loading slot idx39 (disp 0x138) -> submitA/B/C
        long[] sites = {0x02219c9cL, 0x0221abd0L, 0x0232daccL, 0x02342294L, 0x023431c8L,
                        0x0234413cL, 0x0235cb84L, 0x023d8588L, 0x0241308cL, 0x024ed688L,
                        0x025aa7ccL, 0x025afe4cL, 0x0267610cL, 0x02aa0338L, 0x02aa06e4L};
        for (long s : sites) {
            Function f = ensureFunc(s);
            w.println("vcall-site 0x" + Long.toHexString(s) + " in "
                + (f == null ? "?" : f.getName() + " " + f.getBody()));
        }
        w.println();

        vcallAt(0x024ed688L, "DL-submitA caller (device region)");
        vcallAt(0x025afe4cL, "DL-submitA caller (scene region)");
        vcallAt(0x025aa7ccL, "DL-submitA caller (scene region 2)");
        vcallAt(0x0241308cL, "DL-submitA caller (02413)");

        decompile(0x024af330L, "per-draw flag landmark FUN_024AF330", 20000);
        decompile(0x024ce59cL, "global getter FUN_024CE59C", 4000);
        decompile(0x028623bcL, "stereo-ish FUN_028623BC", 8000);
        decompile(0x028623fcL, "stereo-ish FUN_028623FC", 8000);

        w.println("==== callers of key per-frame funcs ====");
        w.println("callers of FUN_0250E8E8 depth3:");
        callersOf(0x0250e8e8L, 3, "  ");
        w.println("callers of FUN_024B0060 depth3:");
        callersOf(0x024b0060L, 3, "  ");
        w.println("callers of FUN_024AEE78 depth3:");
        callersOf(0x024aee78L, 3, "  ");

        decompile(0x025d7884L, "big scene func FUN_025D7884", 30000);

        w.close();
        println("s6 done");
    }
}

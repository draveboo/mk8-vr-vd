import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

import java.io.FileWriter;
import java.io.PrintWriter;

public class S9 extends GhidraScript {
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

    void disasm(long start, long end, String title) {
        w.println("--- disasm " + title + " [" + Long.toHexString(start) + ".." + Long.toHexString(end) + "] ---");
        for (Address a = A(start); a.getOffset() < end; ) {
            Instruction i = currentProgram.getListing().getInstructionAt(a);
            if (i == null) {
                a = a.add(4);
                continue;
            }
            w.println(String.format("  %08x  %s", a.getOffset(), i.toString()));
            a = i.getAddress().add(i.getLength());
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
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s9.txt"));

        disasm(0x02695674L, 0x026956b0L, "FUN_02695674 raw");
        disasm(0x02695660L, 0x02695674L, "FUN_02695660 raw");

        decompile(0x026a4700L, "record body A FUN_026A4700", 16000);
        decompile(0x026a5158L, "record body B FUN_026A5158", 10000);
        decompile(0x026a53e0L, "record body C FUN_026A53E0", 8000);
        decompile(0x026a599cL, "record body D FUN_026A599C", 8000);

        w.println("==== refs to FUN_026a5d98 (record driver) ====");
        callersOf(0x026a5d98L, 2, "  ");

        decompile(0x020a2290L, "target present helper FUN_020A2290", 8000);
        decompile(0x020a0654L, "FUN_020A0654 (rect update caller)", 5000);
        decompile(0x020a2eb0L, "FUN_020A2EB0 (rect update caller)", 5000);

        w.close();
        println("s9 done");
    }
}

import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.mem.MemoryBlock;

import java.io.FileWriter;
import java.io.PrintWriter;

public class Step2 extends GhidraScript {
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

    void decompile(Function f, String title, int maxChars) {
        w.println("--- decompile " + title + " : " + (f == null ? "NULL" : f.getName() + " " + f.getBody()) + " ---");
        if (f == null) return;
        try {
            DecompileResults res = di().decompileFunction(f, 60, monitor);
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

    void instrWindow(long addr, int before, int after) {
        Address a = A(addr);
        try {
            Instruction at = currentProgram.getListing().getInstructionAt(a);
            if (at == null) {
                disassemble(a);
                at = currentProgram.getListing().getInstructionAt(a);
            }
            if (at == null) { w.println("  no instruction at " + a); return; }
            Address start = A(addr).subtract(before);
            InstructionIterator it = currentProgram.getListing().getInstructions(start, true);
            int n = 0;
            int total = before / 4 + after / 4;
            while (it.hasNext() && n < total) {
                Instruction i = it.next();
                w.println("    " + i.getAddress() + "  " + i + (i.getAddress().equals(a) ? "  <== SITE" : ""));
                n++;
            }
        } catch (Exception e) {
            w.println("  window fail " + e);
        }
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

    @Override
    public void run() throws Exception {
        String outDir = "D:\\mk8-VR\\work\\re-out\\";
        w = new PrintWriter(new FileWriter(outDir + "step2.txt"));

        w.println("==== memory blocks c000 range ====");
        for (MemoryBlock b : currentProgram.getMemory().getBlocks()) {
            if (b.getStart().getOffset() >= 0xc0000000L)
                w.println("  " + b.getName() + " " + b.getStart() + "-" + b.getEnd() + " x=" + b.isExecute());
        }

        long[] sites = {
            0x0273951cL, 0x027afd58L, 0x027afd78L,           // CopyColorBufferToScanBuffer
            0x024d4fa8L, 0x02734d04L, 0x027b8928L,           // SwapScanBuffers
            0x024d4c84L, 0x024d4f20L, 0x024d4facL,           // DrawDone near frame loop
            0x02739368L, 0x027afe24L,                         // ClearColor
            0x027396e4L                                        // SetTVBuffer
        };
        String[] labels = {
            "CopyToScan@273951c", "CopyToScan@27afd58", "CopyToScan@27afd78",
            "Swap@24d4fa8", "Swap@2734d04", "Swap@27b8928",
            "DrawDone@24d4c84", "DrawDone@24d4f20", "DrawDone@24d4fac",
            "ClearColor@2739368", "ClearColor@27afe24",
            "SetTVBuffer@27396e4"
        };

        w.println("\n==== call sites: instructions + containing funcs ====");
        for (int i = 0; i < sites.length; i++) {
            w.println("[ " + labels[i] + " ]");
            Function f = ensureFunc(sites[i]);
            w.println("  func: " + (f == null ? "NONE" : f.getName() + " " + f.getBody()));
            instrWindow(sites[i], 0x30, 0x10);
        }

        w.println("\n==== caller chains ====");
        w.println("callers of FUN_024af330 (frame landmark, depth 4):");
        callersOf(0x024af330L, 4, "  ");
        w.println("callers of FUN_024aee78 (projection landmark, depth 3):");
        callersOf(0x024aee78L, 3, "  ");

        w.println("\n==== decompiles ====");
        decompile(ensureFunc(0x027afd58L), "CopyToScan caller 0x27afd58", 14000);
        decompile(ensureFunc(0x0273951cL), "CopyToScan caller 0x273951c", 14000);
        decompile(ensureFunc(0x024d4fa8L), "Swap caller 0x24d4fa8", 14000);
        w.close();
        println("step2 done");
    }
}

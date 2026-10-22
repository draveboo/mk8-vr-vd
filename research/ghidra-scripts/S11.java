import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSet;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

import java.io.FileWriter;
import java.io.PrintWriter;

public class S11 extends GhidraScript {
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

    void fixAndDecompile(long addr, String title, int maxChars) {
        Address a = A(addr);
        Function f = getFunctionAt(a);
        if (f == null) f = getFunctionContaining(a);
        if (f != null && f.getBody().getNumAddresses() < 64) {
            try {
                removeFunction(f);
            } catch (Exception e) {
                w.println("  removeFunc fail: " + e);
            }
            f = null;
        }
        if (f == null) {
            try {
                clearListing(a, a.add(0x800));
            } catch (Exception e) {
                w.println("  clearListing fail: " + e);
            }
            disassemble(a);
            f = createFunction(a, null);
        }
        w.println("--- decompile " + title + " : " + (f == null ? "NULL" : f.getName() + " " + f.getBody()) + " ---");
        if (f == null) return;
        try {
            DecompileResults res = di().decompileFunction(f, 240, monitor);
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

    void disasmFunc(long addr, int maxInstr, String title) {
        Function f = getFunctionContaining(A(addr));
        w.println("--- disasmfunc " + title + " : " + (f == null ? "?" : f.getName() + " " + f.getBody()) + " ---");
        if (f == null) return;
        long end = f.getBody().getMaxAddress().getOffset() + 1;
        int n = 0;
        for (Address a = f.getEntryPoint(); a.getOffset() < end && n < maxInstr; ) {
            Instruction i = currentProgram.getListing().getInstructionAt(a);
            if (i == null) {
                a = a.add(4);
                n++;
                continue;
            }
            w.println(String.format("  %08x  %s", a.getOffset(), i.toString()));
            a = i.getAddress().add(i.getLength());
            n++;
        }
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s11.txt"));

        fixAndDecompile(0x026a4700L, "record body A FUN_026A4700", 30000);
        fixAndDecompile(0x026a5158L, "record body B FUN_026A5158", 16000);
        fixAndDecompile(0x026a53e0L, "record body C FUN_026A53E0", 16000);

        disasmFunc(0x026a4700L, 200, "body A after fix");

        w.close();
        println("s11 done");
    }
}

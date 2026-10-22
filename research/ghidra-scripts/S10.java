import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

import java.io.FileWriter;
import java.io.PrintWriter;

public class S10 extends GhidraScript {
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
        int n = 0;
        for (Address a = A(start); a.getOffset() < end && n < 400; ) {
            Instruction i = currentProgram.getListing().getInstructionAt(a);
            if (i == null) {
                w.println(String.format("  %08x  <no instr>", a.getOffset()));
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

    void disasmFunc(long addr, int maxInstr, String title) {
        Function f = ensureFunc(addr);
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
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s10.txt"));

        disasmFunc(0x026a5d98L, 120, "FUN_026A5D98 record driver");
        disasmFunc(0x02695720L, 60, "FUN_02695720 record begin/rotate");
        disasmFunc(0x0269e4dcL, 80, "FUN_0269E4DC end+call helper");

        disasm(0x026a4700L, 0x026a4800L, "record body A raw");
        disasm(0x026a5158L, 0x026a5220L, "record body B raw");
        disasm(0x026a53e0L, 0x026a5480L, "record body C raw");

        decompile(0x024af330L, "per-draw flag landmark FUN_024AF330", 14000);
        decompile(0x0269e4ccL, "uniform offset calc FUN_0269E4CC", 6000);

        disasmFunc(0x020a5db4L, 80, "FUN_020A5DB4 frame render");

        w.close();
        println("s10 done");
    }
}

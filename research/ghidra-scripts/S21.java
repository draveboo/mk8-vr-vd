import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S21 extends GhidraScript {
    static PrintWriter w;

    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void disasm(long start, long end, String title) {
        w.println("---- disasm " + title + " [" + Long.toHexString(start) + "," + Long.toHexString(end) + "] ----");
        Listing lst = currentProgram.getListing();
        Instruction ins = lst.getInstructionAt(A(start));
        while (ins != null && ins.getAddress().getOffset() < end) {
            w.println("  " + ins.getAddress() + "  " + ins);
            ins = ins.getNext();
        }
        w.println();
    }

    void decompile(long addr, String title, int maxChars) {
        w.println("--- decompile " + title + " (" + Long.toHexString(addr) + ") ---");
        Function f = getFunctionContaining(A(addr));
        if (f == null) { w.println("  NULL"); w.println(); return; }
        w.println("  function " + f.getName() + " " + f.getBody());
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        DecompileResults res = di.decompileFunction(f, 180, monitor);
        if (res.decompileCompleted() && res.getDecompiledFunction() != null) {
            String c = res.getDecompiledFunction().getC();
            if (c.length() > maxChars) c = c.substring(0, maxChars) + "\n/* TRUNCATED */";
            w.println(c);
        } else w.println("  FAIL " + res.getErrorMessage());
        w.println();
    }

    void callsIn(long addr, String title) {
        w.println("==== calls inside " + title + " (" + Long.toHexString(addr) + ") ====");
        Function f = getFunctionContaining(A(addr));
        if (f == null) { w.println("  NULL"); w.println(); return; }
        Listing lst = currentProgram.getListing();
        Instruction ins = lst.getInstructionAt(f.getEntryPoint());
        int n = 0;
        while (ins != null && f.getBody().contains(ins.getAddress())) {
            if (ins.getFlowType().isCall()) {
                w.println("  " + ins.getAddress() + "  " + ins);
                n++;
            }
            ins = ins.getNext();
        }
        w.println("  total calls=" + n);
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s21.txt"));

        decompile(0x0250E74CL, "calc+record+submit FUN_0250E74C", 8000);
        disasm(0x0250E74CL, 0x0250E8E8L, "FUN_0250E74C full");
        callsIn(0x0250E74CL, "FUN_0250E74C");

        w.close();
        println("s21 done");
    }
}

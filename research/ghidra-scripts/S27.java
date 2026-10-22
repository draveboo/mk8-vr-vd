import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.scalar.Scalar;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S27 extends GhidraScript {
    static PrintWriter w;
    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    // Find immediates equal to a value in the whole program.
    void findImm(long value, String title) {
        w.println("==== immediates == " + value + " (0x" + Long.toHexString(value) + ") : " + title + " ====");
        Listing lst = currentProgram.getListing();
        InstructionIterator it = lst.getInstructions(true);
        int n = 0;
        while (it.hasNext()) {
            Instruction ins = it.next();
            for (int op = 0; op < ins.getNumOperands(); op++) {
                Object[] objs = ins.getOpObjects(op);
                for (Object o : objs) {
                    if (o instanceof Scalar) {
                        long v = ((Scalar)o).getUnsignedValue();
                        if (v == value) {
                            Function f = getFunctionContaining(ins.getAddress());
                            w.println("  " + ins.getAddress() + "  " + ins + "   in " + (f==null?"?":f.getName()));
                            if (++n >= 60) { w.println("  ...(truncated)"); w.println(); return; }
                        }
                    }
                }
            }
        }
        if (n == 0) w.println("  (none)");
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

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s27.txt"));

        // DRC/GamePad scan buffer dimensions observed live: 854x480 and 864x480.
        findImm(854, "DRC width 854");
        findImm(480, "DRC height 480");
        findImm(864, "DRC width 864");
        // 854 = 0x356, 480 = 0x1E0, 864 = 0x360. Also try 0x358 (856) nearby.
        findImm(720, "TV height 720 / DRC alt");
        findImm(1280, "TV width 1280");
        // Common neighbouring sizes.
        findImm(856, "0x358 alt width");

        // Refs to the target-setup functions' data.
        w.close();
        println("s27 done");
    }
}

import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import java.io.FileWriter;
import java.io.PrintWriter;

// S35: FUN_025d8f84 is entered live (camloop=138) but never reaches the two
// FUN_02566c7c call sites. Read the whole function to see which path runs.
public class S35 extends GhidraScript {
    static PrintWriter w;
    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s35.txt"));
        Address a = A(0x025d8f84);
        Address cur = a;
        for (int i = 0; i < 600; i++) {
            if (!disassemble(cur)) break;
            Instruction ins = getInstructionAt(cur);
            if (ins == null) break;
            cur = ins.getAddress().add(ins.getLength());
        }
        Function f = getFunctionContaining(a);
        if (f == null || !f.getEntryPoint().equals(a)) f = createFunction(a, null);
        w.println("function: " + (f == null ? "NULL" : f.getName() + " " + f.getBody()));
        if (f != null) {
            DecompInterface di = new DecompInterface();
            di.openProgram(currentProgram);
            DecompileResults res = di.decompileFunction(f, 300, monitor);
            if (res.decompileCompleted() && res.getDecompiledFunction() != null)
                w.println(res.getDecompiledFunction().getC());
            else w.println("FAIL " + res.getErrorMessage());
        }
        w.close();
    }
}

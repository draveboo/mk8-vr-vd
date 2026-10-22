import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import java.io.FileWriter;
import java.io.PrintWriter;

// S32: what do FUN_025d8f84's two calls to the camera-block consumer pass in r4?
public class S32 extends GhidraScript {
    static PrintWriter w;
    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void ensure(long addr, int count) {
        Address a = A(addr);
        if (currentProgram.getMemory().getBlock(a) == null) { w.println("  [skip] " + a); return; }
        Address cur = a;
        for (int i = 0; i < count; i++) {
            if (!disassemble(cur)) break;
            Instruction ins = getInstructionAt(cur);
            if (ins == null) break;
            cur = ins.getAddress().add(ins.getLength());
        }
        Function f = getFunctionContaining(a);
        if (f == null || !f.getEntryPoint().equals(a)) f = createFunction(a, null);
        if (f != null) w.println("  [ok] " + f.getName() + " " + f.getBody());
    }

    void disasm(long start, int count, String title) {
        w.println("=== " + title + " from 0x" + Long.toHexString(start) + " ===");
        Listing lst = currentProgram.getListing();
        Instruction ins = lst.getInstructionAt(A(start));
        int n = 0;
        while (ins != null && n < count) {
            w.println("  " + ins.getAddress() + "  " + ins);
            ins = ins.getNext(); n++;
        }
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s32.txt"));
        w.println("S32: FUN_025d8f84 camera-block call sites (MK8 v4.2)");
        w.println();
        ensure(0x025d8f84, 1400);
        disasm(0x025d8f84, 30, "FUN_025d8f84 entry");
        disasm(0x025d9150, 30, "first consumer call");
        disasm(0x025d9360, 30, "second consumer call");
        w.close();
    }
}

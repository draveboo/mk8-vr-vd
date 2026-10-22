import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import java.io.FileWriter;
import java.io.PrintWriter;

// S34: exact tail of FUN_024B0060 (slots are filled by then) so the descriptor
// dump can hook a real instruction.
public class S34 extends GhidraScript {
    static PrintWriter w;
    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s34.txt"));
        w.println("S34: FUN_024B0060 tail (MK8 v4.2)");
        w.println();
        Address a = A(0x024b0060);
        Address cur = a;
        for (int i = 0; i < 900; i++) {
            if (!disassemble(cur)) break;
            Instruction ins = getInstructionAt(cur);
            if (ins == null) break;
            cur = ins.getAddress().add(ins.getLength());
        }
        w.println("=== disasm 0x024b03a0..0x024b03e7 ===");
        Instruction ins = getInstructionAt(A(0x024b03a0));
        int n = 0;
        while (ins != null && ins.getAddress().getOffset() <= 0x024b03e7 && n < 40) {
            w.println("  " + ins.getAddress() + "  " + ins);
            ins = ins.getNext(); n++;
        }
        w.close();
    }
}

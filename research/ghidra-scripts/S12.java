import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Instruction;

import java.io.FileWriter;
import java.io.PrintWriter;

public class S12 extends GhidraScript {
    static PrintWriter w;

    Address A(long v) {
        return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v);
    }

    void dump(long start, int n, String title) throws Exception {
        w.println("==== " + title + " @ " + Long.toHexString(start) + " (" + n + " insns) ====");
        Address a = A(start);
        for (int i = 0; i < n; i++) {
            Instruction ins = currentProgram.getListing().getInstructionAt(a);
            if (ins == null) {
                disassemble(a);
                ins = currentProgram.getListing().getInstructionAt(a);
            }
            if (ins == null) { w.println("  " + a + "  <no insn>"); break; }
            StringBuilder bytes = new StringBuilder();
            for (byte b : ins.getBytes()) bytes.append(String.format("%02X ", b));
            w.println(String.format("%s  %-32s  %s", a, bytes.toString().trim(), ins.toString()));
            a = ins.getAddress().add(ins.getLength());
        }
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s12.txt"));

        dump(0x020a5db4L, 14, "scene render entry FUN_020A5DB4 prologue");
        dump(0x020a5e20L, 14, "around frame hook 0x020A5E34 / present 0x020A5EC0");
        dump(0x026a5d98L, 12, "record driver FUN_026A5D98 prologue");
        dump(0x026a5e44L, 32, "record driver tail: matrix upload branch target 0x026A5E94");
        dump(0x026a5df0L, 24, "record driver around eye-id 0x026A5E04 and matrix bl 0x026A5E98");
        dump(0x0250e8e8L, 10, "TV path mode-5 dispatch FUN_0250E8E8");

        w.close();
        println("s12 done");
    }
}

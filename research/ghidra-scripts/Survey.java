import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.MemoryBlock;

import java.io.FileWriter;
import java.io.PrintWriter;

public class Survey extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outDir = "D:\\mk8-VR\\work\\re-out\\";
        PrintWriter w = new PrintWriter(new FileWriter(outDir + "survey.txt"));
        Program p = currentProgram;
        w.println("Program: " + p.getName());
        w.println("ImageBase: " + p.getImageBase());

        int fcount = 0;
        for (Function f : p.getFunctionManager().getFunctions(true)) fcount++;
        w.println("Function count: " + fcount);

        w.println("Memory blocks:");
        for (MemoryBlock b : p.getMemory().getBlocks()) {
            w.println("  " + b.getName() + " " + b.getStart() + "-" + b.getEnd()
                + " r=" + b.isRead() + " w=" + b.isWrite() + " x=" + b.isExecute());
        }

        long[] anchors = { 0x024AEF70L, 0x024AF384L };
        for (long a : anchors) {
            Address addr = p.getAddressFactory().getDefaultAddressSpace().getAddress(a);
            w.println("--- anchor " + addr + " ---");
            Function f = getFunctionContaining(addr);
            if (f == null) {
                Function nf = createFunction(addr, null);
                w.println("createFunction: " + (nf == null ? "null" : nf.getName() + " " + nf.getBody()));
            } else {
                w.println("existing function: " + f.getName() + " " + f.getBody());
            }
            // ensure code defined around anchor
            disassemble(addr.subtract(0x10));
            w.println("=== instructions around " + addr + " ===");
            InstructionIterator it = p.getListing().getInstructions(addr.subtract(0x80), true);
            int n = 0;
            while (it.hasNext() && n < 160) {
                Instruction i = it.next();
                w.println(i.getAddress() + "  " + i.toString());
                if (i.getAddress().compareTo(addr.add(0x140)) >= 0) break;
                n++;
            }
        }
        w.close();
        println("survey done");
    }
}

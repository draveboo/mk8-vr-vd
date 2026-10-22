import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.mem.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S25 extends GhidraScript {
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

    void ptrTable(long base, int n, String title) {
        w.println("==== pointer table " + title + " @" + Long.toHexString(base) + " ====");
        Memory mem = currentProgram.getMemory();
        for (int i = 0; i < n; i++) {
            try {
                long v = mem.getInt(A(base + i*4L)) & 0xFFFFFFFFL;
                String note = "";
                if (v >= 0x02000000L && v < 0x03000000L) {
                    Function f = getFunctionContaining(A(v));
                    note = "-> func " + (f==null? "?" : f.getName());
                }
                w.println("  +0x" + Integer.toHexString(i*4) + ": " + String.format("%08X", v) + " " + note);
            } catch (Exception e) { w.println("  +0x" + Integer.toHexString(i*4) + ": ERR"); }
        }
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s25.txt"));

        // Record driver has no static callers (vtable). Find the vtable holding it.
        // S16 said slot @0x100F3FE4 = FUN_026A5D98. Dump that table and find who sets it.
        decompile(0x0250DAC4L, "FUN_0250DAC4 (slot order filler) [again, focus]", 4000);

        decompile(0x024B0060L, "camera update FUN_024B0060", 9000);

        // Where is FUN_026A5D98 referenced as data (vtable slot)?
        w.println("==== data refs to FUN_026A5D98 (0x026A5D98) ====");
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(A(0x026A5D98L));
        int n=0;
        while (rit.hasNext()) {
            Reference r = rit.next();
            w.println("  " + r.getReferenceType() + " from " + r.getFromAddress());
            if(++n>=40){w.println("  ...");break;}
        }
        if(n==0)w.println("  (none)");
        w.println();

        w.close();
        println("s25 done");
    }
}

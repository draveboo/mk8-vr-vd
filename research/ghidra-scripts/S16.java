import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S16 extends GhidraScript {
    static PrintWriter w;

    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void hexdump(long addr, int words) {
        Memory mem = currentProgram.getMemory();
        StringBuilder sb = new StringBuilder();
        try {
            for (int i = 0; i < words; i++) {
                long v = mem.getInt(A(addr + i*4L)) & 0xFFFFFFFFL;
                sb.append(String.format("%08X ", v));
            }
        } catch (Exception e) { sb.append("ERR " + e); }
        w.println("  dump@" + Long.toHexString(addr) + ": " + sb);
    }

    void refsTo(long addr, String title, int max) {
        w.println("==== refs to " + Long.toHexString(addr) + " : " + title + " ====");
        Address a = A(addr);
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(a);
        int n = 0;
        while (rit.hasNext()) {
            Reference r = rit.next();
            Function cf = getFunctionContaining(r.getFromAddress());
            w.println("  " + r.getReferenceType() + " from " + r.getFromAddress() + " in " + (cf == null ? "?" : cf.getName() + " " + cf.getBody()));
            if (++n >= max) { w.println("  ..."); break; }
        }
        if (n == 0) w.println("  (none)");
        w.println();
    }

    void decompile(long addr, String title, int maxChars) {
        w.println("--- decompile " + title + " (" + Long.toHexString(addr) + ") ---");
        Function f = getFunctionContaining(A(addr));
        if (f == null) { w.println("  NULL"); w.println(); return; }
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        DecompileResults res = di.decompileFunction(f, 150, monitor);
        if (res.decompileCompleted() && res.getDecompiledFunction() != null) {
            String c = res.getDecompiledFunction().getC();
            if (c.length() > maxChars) c = c.substring(0, maxChars) + "\n/* TRUNCATED */";
            w.println(c);
        } else w.println("  FAIL " + res.getErrorMessage());
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s16.txt"));

        // The vtable/table that holds FUN_026A5D98 lives near 0x100F3FE4.
        w.println("==== table around 0x100F3FE4 (FUN_026A5D98 slot) ====");
        hexdump(0x100F3FC0L, 24);
        w.println();
        refsTo(0x100F3FE4L, "slot holding FUN_026A5D98", 20);
        refsTo(0x100F3FC0L, "table base probe", 20);

        // Find what reads the vtable: search for the base 0x100F3Fxx as a lis/ori pair
        // is hard; instead dump the pointer refs of a few nearby slots.
        for (long s = 0x100F3FC0L; s <= 0x100F4020L; s += 4) {
            Memory mem = currentProgram.getMemory();
            long v = mem.getInt(A(s)) & 0xFFFFFFFFL;
            if (v >= 0x02000000L && v < 0x03000000L) {
                w.println("  table@" + Long.toHexString(s) + " -> func " + Long.toHexString(v));
            }
        }
        w.println();

        w.close();
        println("s16 done");
    }
}

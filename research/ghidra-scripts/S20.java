import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S20 extends GhidraScript {
    static PrintWriter w;

    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void disasm(long start, long end, String title) {
        w.println("---- disasm " + title + " [" + Long.toHexString(start) + "," + Long.toHexString(end) + "] ----");
        Listing lst = currentProgram.getListing();
        Instruction ins = lst.getInstructionAt(A(start));
        StringBuilder sb = new StringBuilder();
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

    void refsTo(long addr, String title, int max) {
        w.println("==== refs to " + Long.toHexString(addr) + " : " + title + " ====");
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(A(addr));
        int n = 0;
        while (rit.hasNext()) {
            Reference r = rit.next();
            Function cf = getFunctionContaining(r.getFromAddress());
            w.println("  " + r.getReferenceType() + " from " + r.getFromAddress() + " in " + (cf == null ? "?" : cf.getName()));
            if (++n >= max) { w.println("  ..."); break; }
        }
        if (n == 0) w.println("  (none)");
        w.println();
    }

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

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s20.txt"));

        disasm(0x020A5DB4L, 0x020A5EF8L, "scene render entry FUN_020A5DB4");

        decompile(0x02116B60L, "present/flip FUN_02116B60", 4000);
        disasm(0x02116B60L, 0x02116C40L, "present FUN_02116B60");

        decompile(0x020A2290L, "scan-target switch FUN_020A2290", 2500);
        disasm(0x020A2290L, 0x020A2340L, "FUN_020A2290");

        decompile(0x0250E8E8L, "TV dispatch FUN_0250E8E8", 2500);
        disasm(0x0250E8E8L, 0x0250E960L, "FUN_0250E8E8");

        decompile(0x0209F8A4L, "sub-renderable FUN_0209F8A4", 2500);
        disasm(0x0209F8A4L, 0x0209F940L, "FUN_0209F8A4");

        decompile(0x020A5D14L, "frame teardown FUN_020A5D14", 2000);

        refsTo(0x020A5DB4L, "callers of scene render entry", 20);

        decompile(0x024CE59CL, "camera mgr FUN_024CE59C", 1500);

        w.println("==== DAT_10188530 region ====");
        hexdump(0x10188530L, 24);
        refsTo(0x10188530L, "DAT_10188530", 20);

        w.close();
        println("s20 done");
    }
}

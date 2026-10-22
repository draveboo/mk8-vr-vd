import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S26 extends GhidraScript {
    static PrintWriter w;
    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }
    void hexdump(long addr, int words) {
        Memory mem = currentProgram.getMemory();
        StringBuilder sb = new StringBuilder();
        try {
            for (int i = 0; i < words; i++) {
                long v = mem.getInt(A(addr + i*4L)) & 0xFFFFFFFFL;
                sb.append(String.format("%08X ", v));
                if ((i+1)%8==0) { w.println("  " + Long.toHexString(addr+i*4L-28) + ": " + sb); sb.setLength(0); }
            }
            if (sb.length()>0) w.println("  tail: " + sb);
        } catch (Exception e) { sb.append("ERR "+e); w.println("  dump@"+Long.toHexString(addr)+": "+sb); }
    }
    void refsTo(long addr, String title, int max) {
        w.println("==== refs to " + Long.toHexString(addr) + " : " + title + " ====");
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(A(addr));
        int n = 0;
        while (rit.hasNext()) {
            Reference r = rit.next();
            Function cf = getFunctionContaining(r.getFromAddress());
            w.println("  " + r.getReferenceType() + " from " + r.getFromAddress() + " in " + (cf==null?"?":cf.getName()));
            if (++n>=max){w.println("  ...");break;}
        }
        if(n==0)w.println("  (none)");
        w.println();
    }
    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s26.txt"));
        w.println("==== DAT_101d62fc region (per-mode/target camera descriptor table) ====");
        hexdump(0x101d62fcL, 48);
        w.println();
        refsTo(0x101d62fcL, "DAT_101d62fc", 20);
        w.println("==== DAT_101d6944 / DAT_1018999c / DAT_101d693c ====");
        hexdump(0x101d6944L, 8);
        hexdump(0x1018999cL, 8);
        hexdump(0x101d693cL, 8);
        w.println("==== DAT_10188530 + 0x124 chain (rect tables) ====");
        Memory mem = currentProgram.getMemory();
        try { long p = mem.getInt(A(0x10188530L + 0x124)) & 0xFFFFFFFFL; w.println("  *10188530+0x124 = " + String.format("%08X",p)); }
        catch(Exception e){ w.println("  err "+e); }
        w.close();
        println("s26 done");
    }
}

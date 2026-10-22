import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S15 extends GhidraScript {
    static PrintWriter w;

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s15.txt"));
        long target = 0x026A5D98L;
        byte[] pat = new byte[] { 0x02, 0x6A, 0x5D, (byte)0x98 };
        Memory mem = currentProgram.getMemory();
        Address cur = currentProgram.getMinAddress();
        Address end = currentProgram.getMaxAddress();
        int found = 0;
        w.println("scanning for pointer 026A5D98 ...");
        while (cur != null && cur.compareTo(end) < 0) {
            Address hit = mem.findBytes(cur, end, pat, null, true, monitor);
            if (hit == null) break;
            Function cf = getFunctionContaining(hit);
            // read a bit of context
            StringBuilder ctx = new StringBuilder();
            try {
                for (int i = -8; i < 12; i++) ctx.append(String.format("%02X ", mem.getByte(hit.add(i)) & 0xFF));
            } catch (Exception e) {}
            w.println("  ptr@ " + hit + " in " + (cf == null ? "?" : cf.getName() + " " + cf.getBody()) + "  ctx=" + ctx);
            found++;
            if (found > 40) { w.println("  ...more"); break; }
            cur = hit.add(4);
        }
        w.println("found=" + found);
        w.close();
        println("s15 done");
    }
}

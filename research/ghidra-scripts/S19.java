import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class S19 extends GhidraScript {
    static PrintWriter w;

    Address A(long v) { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v); }

    void branchTargetsTo(long addr, String title) {
        w.println("==== refs to " + Long.toHexString(addr) + " : " + title + " ====");
        Address a = A(addr);
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(a);
        int n = 0;
        while (rit.hasNext()) {
            Reference r = rit.next();
            Function cf = getFunctionContaining(r.getFromAddress());
            w.println("  " + r.getReferenceType() + " from " + r.getFromAddress() + " in " + (cf == null ? "?" : cf.getName()));
            n++;
        }
        if (n == 0) w.println("  (none)");
        w.println();
    }

    void scanBranchesTo(long lo, long hi, String title) {
        w.println("==== branches landing in [" + Long.toHexString(lo) + "," + Long.toHexString(hi) + ") : " + title + " ====");
        Listing lst = currentProgram.getListing();
        Address cur = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(lo);
        Address end = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(hi);
        InstructionIterator it = lst.getInstructions(cur, true);
        int n = 0;
        while (it.hasNext()) {
            Instruction ins = it.next();
            if (ins.getAddress().getOffset() >= hi) break;
            if (ins.getFlowType().isJump() || ins.getFlowType().isCall()) {
                Reference[] refs = ins.getReferencesFrom();
                for (Reference r : refs) {
                    Address t = r.getToAddress();
                    if (t != null && t.getOffset() >= lo && t.getOffset() < hi)
                        w.println("  " + ins.getAddress() + " " + ins + " -> " + t + " (" + r.getReferenceType() + ")");
                }
                n++;
            }
        }
        w.println("  scanned " + n + " flow instructions");
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s19.txt"));

        // Is our hook address a branch target from anywhere?
        branchTargetsTo(0x026A5E20L, "hook site A");
        branchTargetsTo(0x026A5E94L, "hook site B");
        branchTargetsTo(0x026A5D98L, "function entry");

        // Who branches INTO the function range (internal branches are fine; external matter).
        scanBranchesTo(0x026A5D98L, 0x026A5F17L, "FUN_026A5D98 body");

        w.close();
        println("s19 done");
    }
}

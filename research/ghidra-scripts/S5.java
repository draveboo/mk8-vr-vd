import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSet;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

import java.io.FileWriter;
import java.io.PrintWriter;

public class S5 extends GhidraScript {
    static PrintWriter w;
    static DecompInterface DI;

    Address A(long v) {
        return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v);
    }

    DecompInterface di() {
        if (DI == null) {
            DI = new DecompInterface();
            DI.openProgram(currentProgram);
        }
        return DI;
    }

    Function ensureFunc(long addr) {
        Address a = A(addr);
        Function f = getFunctionContaining(a);
        if (f == null && currentProgram.getListing().getInstructionAt(a) != null) {
            f = createFunction(a, null);
        }
        if (f == null) {
            disassemble(a);
            f = createFunction(a, null);
        }
        return f;
    }

    void decompile(long addr, String title, int maxChars) {
        Function f = ensureFunc(addr);
        w.println("--- decompile " + title + " : " + (f == null ? "NULL" : f.getName() + " " + f.getBody()) + " ---");
        if (f == null) return;
        try {
            DecompileResults res = di().decompileFunction(f, 120, monitor);
            if (res.decompileCompleted() && res.getDecompiledFunction() != null) {
                String c = res.getDecompiledFunction().getC();
                if (c.length() > maxChars) c = c.substring(0, maxChars) + "\n/* TRUNCATED */";
                w.println(c);
            } else {
                w.println("  FAIL: " + res.getErrorMessage());
            }
        } catch (Exception e) {
            w.println("  EXC " + e);
        }
        w.println();
    }

    void callersOf(long addr, int depth, String indent) {
        Address a = A(addr);
        int shown = 0;
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(a);
        while (rit.hasNext()) {
            Reference r = rit.next();
            if (!(r.getReferenceType().isCall())) continue;
            Address from = r.getFromAddress();
            Function cf = getFunctionContaining(from);
            w.println(indent + "caller " + (cf == null ? "?" : cf.getName() + " " + cf.getBody()) + "  call@" + from);
            shown++;
            if (depth > 1 && cf != null) callersOf(cf.getEntryPoint().getOffset(), depth - 1, indent + "    ");
        }
        if (shown == 0) w.println(indent + "(no call refs to " + Long.toHexString(addr) + ")");
    }

    void printWindow(long addr, int beforeInsns) {
        try {
            Address a = A(addr);
            Instruction at = currentProgram.getListing().getInstructionAt(a);
            if (at == null) { disassemble(a); at = currentProgram.getListing().getInstructionAt(a); }
            if (at == null) return;
            InstructionIterator it = currentProgram.getListing().getInstructions(A(addr).subtract(beforeInsns * 4), true);
            int n = 0, total = beforeInsns + 10;
            while (it.hasNext() && n < total) {
                Instruction i = it.next();
                w.println("      " + i.getAddress() + "  " + i);
                n++;
            }
        } catch (Exception e) { w.println("    window fail " + e); }
    }

    void scanVCalls(long startAddr, long endAddr, String label) {
        w.println("vcall sites (lwz disp; mtspr CTR; bctrl) in " + label
            + " [0x" + Long.toHexString(startAddr) + ",0x" + Long.toHexString(endAddr) + "):");
        AddressSet set = new AddressSet(A(startAddr), A(endAddr));
        InstructionIterator it = currentProgram.getListing().getInstructions(set, true);
        while (it.hasNext()) {
            Instruction i = it.next();
            if (!i.getMnemonicString().equals("lwz")) continue;
            ghidra.program.model.scalar.Scalar sc = i.getScalar(1);
            if (sc == null) continue;
            long dv = sc.getValue();
            // look ahead up to 6 insns for mtspr CTR + bctrl
            boolean v = false;
            InstructionIterator it2 = currentProgram.getListing().getInstructions(i.getAddress(), true);
            int k = 0;
            while (it2.hasNext() && k < 8) {
                Instruction j = it2.next();
                String m = j.toString();
                if (m.contains("mtspr") && m.contains("CTR")) v = true;
                k++;
            }
            if (v) {
                w.println("  " + i.getAddress() + "  " + i);
                printWindow(i.getAddress().getOffset(), 6);
            }
        }
        w.println();
    }

    void allRefs(long addr, String title) {
        Address a = A(addr);
        w.println("refs (all types) to " + Long.toHexString(addr) + " [" + title + "]:");
        int shown = 0;
        ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(a);
        while (rit.hasNext()) {
            Reference r = rit.next();
            Function cf = getFunctionContaining(r.getFromAddress());
            w.println("   " + r.getReferenceType() + " from " + r.getFromAddress()
                + (cf != null ? "  in " + cf.getName() + " " + cf.getBody() : "  (data)"));
            shown++;
        }
        if (shown == 0) w.println("   (none)");
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s5.txt"));

        allRefs(0x100c3818L, "DL_submitA slot addr (vtable?)");
        allRefs(0x100c36e0L, "DL_submitA vtable base candidate");
        allRefs(0x100c3860L, "DL_submitB vtable base candidate");
        allRefs(0x100c39e0L, "DL_submitC vtable base candidate");

        scanVCalls(0x024b0000L, 0x024b4000L, "device region");
        scanVCalls(0x0250e400L, 0x0250e940L, "render-mode switch");
        scanVCalls(0x02695000L, 0x026a1000L, "record helper region");
        scanVCalls(0x025d7800L, 0x025d9000L, "big scene func region");

        decompile(0x024b0060L, "camera/viewport update FUN_024B0060", 25000);
        decompile(0x024aee78L, "projection/aspect FUN_024AEE78", 20000);
        decompile(0x0250e8e8L, "scene entry FUN_0250E8E8", 6000);
        decompile(0x0250e74cL, "FUN_0250E74C", 6000);

        w.close();
        println("s5 done");
    }
}


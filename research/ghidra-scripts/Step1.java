import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

import java.io.FileWriter;
import java.io.PrintWriter;

public class Step1 extends GhidraScript {
    static PrintWriter w;
    static DecompInterface DI;

    static String fmt(Instruction i) {
        return i.getAddress() + "  " + i;
    }

    void dumpInstrWindow(Address center, int before, int after) {
        try {
            Address start = center.subtract(before);
            InstructionIterator it = currentProgram.getListing().getInstructions(start, true);
            int n = 0;
            while (it.hasNext() && n < before / 4 + after / 4) {
                Instruction i = it.next();
                String mark = i.getAddress().equals(center) ? "  <== LANDMARK" : "";
                w.println("    " + i.getAddress() + "  " + i + mark);
                n++;
            }
        } catch (Exception e) {
            w.println("    (dump failed: " + e + ")");
        }
    }

    void decompileAt(long addr, String title) {
        Address a = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(addr);
        Function f = getFunctionContaining(a);
        w.println("=== " + title + " @ " + a + " : function " + (f == null ? "NONE" : f.getName() + " " + f.getBody()) + " ===");
        if (f == null) return;
        try {
            if (DI == null) {
                DI = new DecompInterface();
                DI.openProgram(currentProgram);
            }
            DecompileResults res = DI.decompileFunction(f, 60, monitor);
            if (res != null && res.decompileCompleted() && res.getDecompiledFunction() != null) {
                String c = res.getDecompiledFunction().getC();
                if (c.length() > 24000) c = c.substring(0, 24000) + "\n/* TRUNCATED */";
                w.println(c);
            } else {
                w.println("  decompile failed: " + (res == null ? "null" : res.getErrorMessage()));
            }
        } catch (Exception e) {
            w.println("  decompile exception: " + e);
        }
        w.println();
    }

    void stubXrefs(long[] stubs, String[] names) throws Exception {
        for (int k = 0; k < stubs.length; k++) {
            Address s = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(stubs[k]);
            ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(s);
            StringBuilder sb = new StringBuilder();
            int cnt = 0;
            while (rit.hasNext()) {
                Reference r = rit.next();
                Address from = r.getFromAddress();
                Function cf = getFunctionContaining(from);
                sb.append(String.format("    from %s  in %s (%s) type=%s%n",
                    from, cf == null ? "?" : cf.getName(), cf == null ? "?" : cf.getBody(), r.getReferenceType()));
                cnt++;
            }
            w.println("  STUB " + names[k] + " @ " + s + " : " + cnt + " refs");
            w.print(sb);
        }
    }

    // dump ~40 instructions before each bl-to-stub call site (arg setup)
    void callSites(long[] stubs, String[] names) throws Exception {
        for (int k = 0; k < stubs.length; k++) {
            Address s = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(stubs[k]);
            ReferenceIterator rit = currentProgram.getReferenceManager().getReferencesTo(s);
            w.println("--- call sites of " + names[k] + " (" + s + ") ---");
            while (rit.hasNext()) {
                Reference r = rit.next();
                Address from = r.getFromAddress();
                Function cf = getFunctionContaining(from);
                w.println("  call @ " + from + "  caller=" + (cf == null ? "?" : cf.getName() + cf.getBody()));
                try {
                    Address start = from.subtract(0x50);
                    InstructionIterator it = currentProgram.getListing().getInstructions(start, true);
                    int limit = -0x50 / 4;
                    int n = 0;
                    while (it.hasNext()) {
                        Instruction i = it.next();
                        w.println("      " + i.getAddress() + "  " + i + (i.getAddress().equals(from) ? "  <==" : ""));
                        if (i.getAddress().equals(from)) break;
                        n++;
                        if (n > 60) break;
                    }
                } catch (Exception e) {
                    w.println("      (dump failed " + e + ")");
                }
            }
        }
    }

    @Override
    public void run() throws Exception {
        String outDir = "D:\\mk8-VR\\work\\re-out\\";
        Program p = currentProgram;
        int fc = 0;
        for (Function f : p.getFunctionManager().getFunctions(true)) fc++;

        w = new PrintWriter(new FileWriter(outDir + "step1.txt"));
        w.println("program=" + p.getName() + " functions=" + fc);

        w.println("\n######## landmark windows ########");
        Address l1 = p.getAddressFactory().getDefaultAddressSpace().getAddress(0x024AEF70L);
        Address l2 = p.getAddressFactory().getDefaultAddressSpace().getAddress(0x024AF384L);
        w.println("window 0x024AEF70:"); dumpInstrWindow(l1, 0x80, 0x180);
        w.println("window 0x024AF384:"); dumpInstrWindow(l2, 0x80, 0x180);

        decompileAt(0x024AEF70L, "PROJECTION landmark");
        decompileAt(0x024AF384L, "FRAME landmark");

        w.println("\n######## GX2 stub xrefs ########");
        long[] stubs = {
            0xc0008860L, 0xc0008870L, 0xc0008888L, 0xc0008bf8L,
            0xc0008b80L, 0xc0008a00L, 0xc0008be8L, 0xc0008b38L,
            0xc00088b8L, 0xc0008878L, 0xc0008858L, 0xc0008a28L,
            0xc0008b78L, 0xc0008890L
        };
        String[] names = {
            "GX2ClearColor", "GX2CopyColorBufferToScanBuffer", "GX2DrawDone", "GX2SwapScanBuffers",
            "GX2SetTVBuffer", "GX2SetColorBuffer", "GX2SetViewport", "GX2SetScissor",
            "GX2Flush", "GX2CopySurface", "GX2ClearBuffersEx", "GX2SetDRCBuffer",
            "GX2SetSwapInterval", "GX2DrawEx"
        };
        stubXrefs(stubs, names);

        w.println("\n######## key call sites ########");
        callSites(new long[]{0xc0008870L, 0xc0008860L, 0xc0008bf8L},
                  new String[]{"GX2CopyColorBufferToScanBuffer", "GX2ClearColor", "GX2SwapScanBuffers"});
        w.close();
        println("step1 done");
    }
}

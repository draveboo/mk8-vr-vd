import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class S1 extends GhidraScript {
    static PrintWriter w;

    Address A(long v) {
        return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(v);
    }

    static long[] CALLDL = {0x024B26E8L,0x024B3224L,0x024B3AECL,0x0269E578L,0x0275EB30L,0x02764A48L,0x0276878CL,0x027688ACL,0x02768E38L,0x02769138L,0x0276A768L,0x0276A8C4L,0x0276AB58L,0x0276D068L,0x0276D728L,0x0277A150L,0x0277E390L,0x0277E78CL,0x02785CD0L,0x02786120L,0x027886D8L,0x027889D4L,0x02788CCCL,0x027C380CL,0x027C8F08L,0x027CAD38L,0x027CBEA4L,0x027CDD08L,0x027E8188L,0x027F63DCL,0x027F9F94L,0x027FA258L,0x0269E578L,0x028A4424L,0x02A91A30L,0x02AA816CL,0x02AA90D4L,0x02AA94B0L,0x02AA97B8L,0x02AAA0C0L,0x02AF1104L,0x02AF149CL,0x02AEE714L,0x02AEEF68L,0x02AEF670L,0x02AF01C4L};
    static long[] DRAWDONE = {0x024D4C84L,0x024D4F20L,0x024D4FACL,0x024E274CL,0x0254A8E0L,0x02734C7CL,0x02734D08L,0x0278A824L,0x0278AB54L,0x0278AB8CL,0x027B6C20L,0x0280B620L,0x0280BA1CL,0x0280BA80L,0x0283DE04L,0x02A96390L,0x02AA6DE0L,0x02AA6E98L,0x02AF445CL};
    static long[] BEGLIST = {0x0269E404L,0x0269E530L,0x027B71C0L,0x028A437CL,0x028A43ECL,0x02A9114CL};
    static long[] ENDLIST = {0x0269E434L,0x0269E560L,0x027B7204L,0x028A438CL,0x028A43FCL,0x02A91188L};
    static long[] DIRECTCALL = {0x027D3F98L,0x027D4038L};

    String fstr(Function f) {
        if (f == null) return "NULL";
        long e = f.getEntryPoint().getOffset();
        Address mx = f.getBody().getMaxAddress();
        long end = mx == null ? e : mx.getOffset();
        return String.format("%s [%08X..%08X] len=0x%X", f.getName(), e, end, (end - e + 4));
    }

    void mapSites(String title, long[] sites, Set<String> seen) {
        w.println("==== " + title + " ====");
        for (long s : sites) {
            Function f = getFunctionContaining(A(s));
            String key = f == null ? "NONE" : String.format("%08X", f.getEntryPoint().getOffset());
            w.println(String.format("  %08X -> %s%s", s, fstr(f), seen.add(key) ? "" : " (dup)"));
        }
        w.println();
    }

    @Override
    public void run() throws Exception {
        w = new PrintWriter(new FileWriter("D:\\mk8-VR\\work\\re-out2\\s1.txt"));
        Set<String> seen = new LinkedHashSet<>();
        mapSites("GX2CallDisplayList sites -> functions", CALLDL, seen);
        mapSites("GX2DrawDone sites -> functions", DRAWDONE, seen);
        mapSites("GX2BeginDisplayListEx sites -> functions", BEGLIST, seen);
        mapSites("GX2EndDisplayList sites -> functions", ENDLIST, seen);
        mapSites("GX2DirectCallDisplayList sites -> functions", DIRECTCALL, seen);
        w.close();
        println("s1 done");
    }
}

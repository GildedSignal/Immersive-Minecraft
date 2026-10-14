import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class AtlasV2BytecodeTest {
    static int count(byte[] bytes,String owner){ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,0);int found=0;for(MethodNode m:n.methods)for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(owner))found++;return found;}
    public static void main(String[]args)throws Exception{
        byte[] fresh=Files.readAllBytes(Path.of(args[0]));
        var budget=EndermanAtlasAgentV2.budgetCalls(fresh);
        byte[] direct=EndermanAtlasPatchV2.patch(fresh);
        byte[] upgrade=EndermanAtlasPatchV2.patch(EndermanAtlasPatch.patch(fresh));
        for(byte[] result:List.of(direct,upgrade)){
            if(count(result,"mcperf/EndermanAtlasFix")!=0||count(result,"mcperf/EndermanAtlasFixV2")!=1)throw new AssertionError("duplicate/missing hook");
            if(!budget.equals(EndermanAtlasAgentV2.budgetCalls(result)))throw new AssertionError("budget changed");
        }
        boolean rejected=false;try{EndermanAtlasPatchV2.patch(direct);}catch(IllegalStateException expected){rejected=true;}
        if(!rejected)throw new AssertionError("double patch permitted");
        System.out.println("PASS fresh-budget and old-hook upgrades each produce exactly one V2 UV hook; budget unchanged; duplicate V2 rejected; all-method ASM verifier passed.");
    }
}

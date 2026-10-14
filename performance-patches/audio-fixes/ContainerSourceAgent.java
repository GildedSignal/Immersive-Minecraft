import java.lang.instrument.*;
import java.nio.file.*;
import java.util.jar.JarFile;
import com.sun.tools.attach.VirtualMachine;
public final class ContainerSourceAgent {
    static boolean active;
    public static void agentmain(String args,Instrumentation inst)throws Exception{
        if(active)throw new IllegalStateException("Container source fix already active");Class<?> target=null;for(var c:inst.getAllLoadedClasses())if(c.getName().equals("com.redsmods.sound_physics_perfected.RaycastingHelper"))target=c;
        if(target==null)throw new IllegalStateException("Sound Physics not loaded");var own=Path.of(ContainerSourceAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());inst.appendToBootstrapClassLoaderSearch(new JarFile(own.resolveSibling("container-source-helper.jar").toFile()));
        var applied=new java.util.concurrent.atomic.AtomicBoolean();var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var transformer=new ClassFileTransformer(){public byte[] transform(ClassLoader l,String name,Class<?> c,java.security.ProtectionDomain p,byte[] input){if(!name.equals("com/redsmods/sound_physics_perfected/RaycastingHelper"))return null;try{var result=ContainerSourcePatch.patch(input);applied.set(true);return result;}catch(Throwable e){failure.set(e);return null;}}};
        inst.addTransformer(transformer,true);try{inst.retransformClasses(target);if(!applied.get())throw new IllegalStateException("Container source hook failed",failure.get());active=true;}catch(Throwable e){inst.removeTransformer(transformer);throw e;}
        Files.writeString(Path.of(args),"PASS: exclude only the connected second half of a source chest. Ray/bounce, reverb, walls and attenuation settings unchanged.\n");
    }
    public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}}
}

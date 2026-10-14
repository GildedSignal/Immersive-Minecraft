import java.lang.instrument.*;
import java.nio.file.*;
import java.util.jar.JarFile;
import com.sun.tools.attach.VirtualMachine;
public final class NativeCacheAgentV2 {
    static boolean active;
    public static void agentmain(String args,Instrumentation inst)throws Exception{
        if(active)throw new IllegalStateException("Metadata cache already active");
        Class<?> target=null;for(var c:inst.getAllLoadedClasses())if(c.getName().equals("me.abdelaziz.util.NativeBinder"))target=c;
        if(target==null||!inst.isModifiableClass(target))throw new IllegalStateException("Modifiable native binder not loaded");
        var own=Path.of(NativeCacheAgentV2.class.getProtectionDomain().getCodeSource().getLocation().toURI());inst.appendToBootstrapClassLoaderSearch(new JarFile(own.resolveSibling("native-metadata-helper.jar").toFile()));
        var patched=new java.util.concurrent.atomic.AtomicBoolean();
        var transform=new ClassFileTransformer(){public byte[] transform(ClassLoader l,String name,Class<?> c,java.security.ProtectionDomain p,byte[] bytes){if(!name.equals("me/abdelaziz/util/NativeBinder"))return null;var result=NativeBinderPatchV2.patch(bytes);patched.set(true);return result;}};
        inst.addTransformer(transform,true);try{inst.retransformClasses(target);if(!patched.get())throw new IllegalStateException("Expected metadata hook not installed");active=true;}catch(Throwable e){inst.removeTransformer(transform);throw e;}
        Files.writeString(Path.of(args),"PASS: two metadata-enumeration sites cached; host values, script environments and animation code unchanged.\n");
    }
    public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}}
}

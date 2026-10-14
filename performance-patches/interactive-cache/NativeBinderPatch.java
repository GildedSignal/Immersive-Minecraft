import java.nio.file.*;
import org.objectweb.asm.*;
public final class NativeBinderPatch {
    public static byte[] patch(byte[] input){
        int[] sites={0,0};var reader=new ClassReader(input);var writer=new ClassWriter(reader,0);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
            var original=super.visitMethod(access,name,descriptor,signature,exceptions);
            if(!name.equals("lambda$bind$3"))return original;
            return new MethodVisitor(Opcodes.ASM9,original){public void visitMethodInsn(int op,String owner,String method,String desc,boolean iface){
                if(op==Opcodes.INVOKEVIRTUAL&&owner.equals("java/lang/Class")&&method.equals("getMethods")&&desc.equals("()[Ljava/lang/reflect/Method;")){
                    sites[0]++;super.visitMethodInsn(Opcodes.INVOKESTATIC,"mcperf/interactive/NativeMetadata","methods","(Ljava/lang/Class;)[Ljava/lang/reflect/Method;",false);
                }else if(op==Opcodes.INVOKEVIRTUAL&&owner.equals("java/lang/Class")&&method.equals("getConstructors")&&desc.equals("()[Ljava/lang/reflect/Constructor;")){
                    sites[1]++;super.visitMethodInsn(Opcodes.INVOKESTATIC,"mcperf/interactive/NativeMetadata","constructors","(Ljava/lang/Class;)[Ljava/lang/reflect/Constructor;",false);
                }else super.visitMethodInsn(op,owner,method,desc,iface);
            }};
        }},0);
        if(sites[0]!=1||sites[1]!=1)throw new IllegalStateException("Expected one method and constructor enumeration, found "+sites[0]+"/"+sites[1]);
        return writer.toByteArray();
    }
    public static void main(String[] args)throws Exception{Files.write(Path.of(args[1]),patch(Files.readAllBytes(Path.of(args[0]))));}
}

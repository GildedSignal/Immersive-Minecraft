import org.objectweb.asm.*;
import java.nio.file.*;
public final class ContainerSourcePatch {
    public static byte[] patch(byte[] input){int[] sites={0};var reader=new ClassReader(input);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
            var original=super.visitMethod(access,name,desc,signature,exceptions);if(!name.equals("countBlocksBetween"))return original;
            return new MethodVisitor(Opcodes.ASM9,original){public void visitMethodInsn(int op,String owner,String name,String desc,boolean iface){
                if(op==Opcodes.INVOKEVIRTUAL&&owner.equals("net/minecraft/core/BlockPos")&&name.equals("equals")&&desc.equals("(Ljava/lang/Object;)Z")){
                    sites[0]++;super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,"mcperf/audio/ContainerSource","isSource","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",false);
                }else super.visitMethodInsn(op,owner,name,desc,iface);
            }};
        }},0);if(sites[0]!=1)throw new IllegalStateException("Expected exactly one source-block exclusion; found "+sites[0]);return writer.toByteArray();
    }
    public static void main(String[] args)throws Exception{Files.write(Path.of(args[1]),patch(Files.readAllBytes(Path.of(args[0]))));}
}

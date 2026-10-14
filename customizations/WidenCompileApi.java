import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
/** Compile-only equivalent of ETF's declared access widener. Never a runtime jar. */
public class WidenCompileApi {
 public static void main(String[] args) throws Exception {
  try(ZipFile mc=new ZipFile(args[0]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
   String name="net/minecraft/world/entity/LivingEntity.class";
   ClassReader reader=new ClassReader(mc.getInputStream(mc.getEntry(name)).readAllBytes());
   ClassWriter writer=new ClassWriter(reader,0);
   reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
    @Override public FieldVisitor visitField(int access,String field,String descriptor,String signature,Object value) {
     if(field.equals("lastEquipmentItems")) access=(access & ~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC;
     return super.visitField(access,field,descriptor,signature,value);
    }
   },0);
   out.putNextEntry(new ZipEntry(name));out.write(writer.toByteArray());out.closeEntry();
  }
 }
}

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/** Patches only original disk classes. Never accepts captured live/mixin-transformed classes. */
public final class PatchPhysicsJarV2 {
  static final String EXPECTED="423801027870ad8f3309d52d2ceb5f09cee42dee1f8c9cec31b11a0d136ed6b4";
  static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
  public static void main(String[] args)throws Exception {
    if(args.length!=3)throw new IllegalArgumentException("Usage: PatchPhysicsJarV2 original.jar output.jar helper.jar");
    Path input=Path.of(args[0]).toAbsolutePath(),output=Path.of(args[1]).toAbsolutePath();
    if(input.equals(output)||Files.exists(output))throw new IllegalStateException("Output must be a new, separate artifact");
    if(!sha(Files.readAllBytes(input)).equals(EXPECTED))throw new IllegalStateException("Installed original SHA256 mismatch; refusing unknown/already-patched jar");
    LinkedHashMap<String,byte[]> content=new LinkedHashMap<>();Map<String,ZipEntry> metadata=new HashMap<>();
    try(JarFile original=new JarFile(input.toFile())) {
      var entries=original.entries();while(entries.hasMoreElements()) {
        ZipEntry e=entries.nextElement();String n=e.getName();
        if(n.matches("(?i)META-INF/[^/]+\\.(SF|RSA|DSA|EC)"))throw new IllegalStateException("Signed jar requires an official rebuilding workflow");
        if(content.put(n,original.getInputStream(e).readAllBytes())!=null)throw new IllegalStateException("Duplicate original entry: "+n);
        metadata.put(n,e);
      }
    }
    Map<String,byte[]> before=new HashMap<>(content);
    for(String target:List.of(PhysicsBudgetAgentV2.MOD,PhysicsBudgetAgentV2.WORLD)) {
      String name=target+".class";byte[] source=content.get(name);
      if(source==null)throw new IllegalStateException("Original target missing "+name);
      content.put(name,PhysicsBudgetAgentV2.patch(target,source));
    }
    try(JarFile helper=new JarFile(Path.of(args[2]).toFile())) {
      int count=0;var entries=helper.entries();while(entries.hasMoreElements()) {
        ZipEntry e=entries.nextElement();String n=e.getName();
        if(n.startsWith("mcperf/PhysicsBudgetV2")&&n.endsWith(".class")) {
          if(content.containsKey(n))throw new IllegalStateException("Helper collision "+n);
          content.put(n,helper.getInputStream(e).readAllBytes());count++;
        }
      }
      if(count<2)throw new IllegalStateException("Helper classes incomplete");
    }
    Files.createDirectories(output.getParent());
    try(JarOutputStream jar=new JarOutputStream(Files.newOutputStream(output,StandardOpenOption.CREATE_NEW))) {
      for(var e:content.entrySet()) {
        ZipEntry original=metadata.get(e.getKey()),copy=new ZipEntry(e.getKey());
        if(original!=null){copy.setTime(original.getTime());if(original.getComment()!=null)copy.setComment(original.getComment());if(original.getExtra()!=null)copy.setExtra(original.getExtra());}
        jar.putNextEntry(copy);jar.write(e.getValue());jar.closeEntry();
      }
    }
    // Independently reopen the archive and prove all unrelated entry contents are identical.
    int unchanged=0;
    try(JarFile check=new JarFile(output.toFile())) {
      if(check.size()!=content.size())throw new IllegalStateException("Archive entry count mismatch");
      for(var e:content.entrySet()) {
        byte[] roundTrip=check.getInputStream(check.getJarEntry(e.getKey())).readAllBytes();
        if(!Arrays.equals(roundTrip,e.getValue()))throw new IllegalStateException("Round-trip mismatch "+e.getKey());
        if(before.containsKey(e.getKey())&&!e.getKey().equals(PhysicsBudgetAgentV2.MOD+".class")&&!e.getKey().equals(PhysicsBudgetAgentV2.WORLD+".class")) {
          if(!Arrays.equals(before.get(e.getKey()),roundTrip))throw new IllegalStateException("Unrelated entry changed "+e.getKey());unchanged++;
        }
      }
      if(!Arrays.equals(before.get("fabric.mod.json"),check.getInputStream(check.getJarEntry("fabric.mod.json")).readAllBytes()))throw new IllegalStateException("Fabric metadata changed");
    }
    System.out.println("PASS original SHA guard; two original disk classes patched; "+unchanged+" unrelated entries byte-identical; Fabric mod ID metadata unchanged");
    System.out.println("OUTPUT_SHA256="+sha(Files.readAllBytes(output)));
    System.out.println("STAGED_OUTPUT="+output);
  }
}

package mcperf;
import java.lang.reflect.*;
import java.util.*;
import java.lang.ref.WeakReference;

/** Staged body-only UV correction; disabled until explicitly enabled by a reviewed installer. */
public final class EndermanAtlasFixV2 {
  public static volatile boolean enabled=false;
  public static long remapped,skipped,failures;
  public static String lastError="";
  static final IdentityHashMap<Object,Integer> cubes=new IdentityHashMap<>();
  static WeakReference<Object> lastEntity=new WeakReference<>(null);
  static Object lastView;
  static boolean lastAllowed;
  static Method getType,getKey;
  static Field uvX,uvY;
  static final String PACK="file/FA+All_Extensions-v1.9.2.zip";
  static Object call(Object o,String name)throws Exception{return o.getClass().getMethod(name).invoke(o);}
  static float f(Object o,String n)throws Exception{return o.getClass().getField(n).getFloat(o);}
  static Class<?> gameClass(Object entity,String name)throws Exception{return Class.forName(name,false,entity.getClass().getClassLoader());}
  static String source(Object manager,Object id,Class<?> resourceProvider,Class<?> resourceClass)throws Exception {
    Optional<?> r=(Optional<?>)resourceProvider.getMethod("getResource",id.getClass()).invoke(manager,id);
    return r.isPresent()?(String)resourceClass.getMethod("sourcePackId").invoke(r.get()):"missing";
  }
  static boolean isAtlas(Object entity,Object view)throws Exception {
    Class<?> mc=gameClass(entity,"net.minecraft.client.Minecraft"),gpu=gameClass(entity,"com.mojang.blaze3d.textures.GpuTextureView");
    boolean result=false;
    if((int)gpu.getMethod("getWidth",int.class).invoke(view,0)==64&&(int)gpu.getMethod("getHeight",int.class).invoke(view,0)==64) {
      Object game=mc.getMethod("getInstance").invoke(null),tm=mc.getMethod("getTextureManager").invoke(game),rm=mc.getMethod("getResourceManager").invoke(game);
      Class<?> tmClass=gameClass(entity,"net.minecraft.client.renderer.texture.TextureManager"),at=gameClass(entity,"net.minecraft.client.renderer.texture.AbstractTexture"),rp=gameClass(entity,"net.minecraft.server.packs.resources.ResourceProvider"),rc=gameClass(entity,"net.minecraft.server.packs.resources.Resource"),idClass=gameClass(entity,"net.minecraft.resources.Identifier");
      Field byPath=tmClass.getDeclaredField("byPath");byPath.setAccessible(true);
      for(var e:((Map<?,?>)byPath.get(tm)).entrySet()) {
        String path=e.getKey().toString();
        if(!(path.equals("minecraft:textures/entity/enderman/enderman.png")||path.equals("minecraft:optifine/random/entity/enderman/enderman2.png")))continue;
        if(at.getMethod("getTextureView").invoke(e.getValue())!=view)continue;
        Object model=idClass.getMethod("withDefaultNamespace",String.class).invoke(null,"optifine/cem/enderman.jem");
        result=PACK.equals(source(rm,e.getKey(),rp,rc))&&PACK.equals(source(rm,model,rp,rc));break;
      }
    }
    return result;
  }
  static int role(Object box)throws Exception {
    Integer cached=cubes.get(box);if(cached!=null)return cached;
    int role=-1;
    if(box.getClass().getName().equals("net.minecraft.client.model.geom.ModelPart$Cube")) {
      float w=f(box,"maxX")-f(box,"minX"),h=f(box,"maxY")-f(box,"minY"),d=f(box,"maxZ")-f(box,"minZ");
      Object[] polygons=(Object[])box.getClass().getField("polygons").get(box);
      float minU=Float.POSITIVE_INFINITY,minV=minU,maxU=Float.NEGATIVE_INFINITY,maxV=maxU;
      if(polygons.length==6)for(Object polygon:polygons)for(Object vertex:(Object[])call(polygon,"vertices")){
        float u=((Number)call(vertex,"u")).floatValue(),v=((Number)call(vertex,"v")).floatValue();
        minU=Math.min(minU,u);maxU=Math.max(maxU,u);minV=Math.min(minV,v);maxV=Math.max(maxV,v);
      }
      if(w==8&&h==8&&d==8&&f(box,"minX")==-4&&f(box,"minY")==-8&&f(box,"minZ")==-4&&minU==0&&maxU==.5f) {
        if(minV==0&&maxV==.5f)role=0;
        else if(minV==.5f&&maxV==1)role=1;
      } else if(w==8&&h==12&&d==4&&f(box,"minX")==-4&&f(box,"minY")==0&&f(box,"minZ")==-2&&minU==.5f&&maxU==.875f&&minV==.5f&&maxV==1)role=2;
      else if(w==2&&h==30&&d==2&&f(box,"minX")==-1&&f(box,"minZ")==-1&&minU==.875f&&maxU==1&&minV==0&&maxV==1){
        if(f(box,"minY")==-2)role=3;else if(f(box,"minY")==0)role=4;
      }
    }
    if(cubes.size()>=128)cubes.clear();cubes.put(box,role);return role;
  }
  public static Object remap(Object uv,Object box,Object entity,Object view,Object feature) {
    if(!enabled)return uv;
    try {
      if(feature!=null||view==null){skipped++;return uv;}
      if(lastEntity.get()!=entity||lastView!=view){
        lastEntity=new WeakReference<>(entity);lastView=view;lastAllowed=false;
        if(getType==null){
          Class<?> type=gameClass(entity,"net.minecraft.world.entity.EntityType");
          Method typeGetter=gameClass(entity,"net.minecraft.world.entity.Entity").getMethod("getType");
          Method keyGetter=type.getMethod("getKey",type);
          getType=typeGetter;getKey=keyGetter;
        }
        // Resource provider is checked again for every new corpse, never cached across reloads.
        lastAllowed="minecraft:enderman".equals(String.valueOf(getKey.invoke(null,getType.invoke(entity))))&&isAtlas(entity,view);
      }
      if(!lastAllowed){skipped++;return uv;}
      int role=role(box);if(role<0){skipped++;return uv;}
      if(uvX==null){
        if(!uv.getClass().getName().equals("org.joml.Vector2f"))throw new IllegalArgumentException("Unexpected UV class");
        Field x=uv.getClass().getField("x"),y=uv.getClass().getField("y");
        if(x.getType()!=float.class||y.getType()!=float.class||Modifier.isFinal(x.getModifiers())||Modifier.isFinal(y.getModifiers())||!x.canAccess(uv)||!y.canAccess(uv))throw new IllegalArgumentException("Unexpected UV fields");
        uvX=x;uvY=y;
      }
      float u=uvX.getFloat(uv),v=uvY.getFloat(uv);
      float outU=switch(role){case 0->u;case 1->u+.5f;case 2,3->u-.25f;case 4->u-.875f;default->u;};
      float outV=switch(role){case 0,2->v*.5f;case 1->v*.5f-.25f;case 3->v*.5f+.25f;case 4->v<=.0625f?v*.5f+.25f:(18f+(v*32f-2f)*.9f)/64f;default->v;};
      try{uvX.setFloat(uv,outU);uvY.setFloat(uv,outV);}
      catch(Exception e){uvX.setFloat(uv,u);uvY.setFloat(uv,v);throw e;}
      remapped++;
    }catch(Exception e){failures++;Throwable error=e;while(error instanceof InvocationTargetException&&error.getCause()!=null)error=error.getCause();lastError=error.toString();}
    return uv;
  }
}


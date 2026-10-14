import java.util.*;
public final class AtlasMathTest {
  static float[][] faceCorners(int tx,int ty,int w,int h,int d,int th) {
    int[][] faces={{tx+d,ty,w,d},{tx+d+w,ty,w,d},{tx,ty+d,d,h},{tx+d,ty+d,w,h},{tx+d+w,ty+d,d,h},{tx+d+w+d,ty+d,w,h}};
    float[][] result=new float[24][2];int at=0;
    for(int[] f:faces)for(int corner=0;corner<4;corner++) {
      result[at++]=new float[]{(f[0]+((corner==0||corner==3)?0:f[2]))/64f,(f[1]+(corner<2?0:f[3]))/(float)th};
    }
    return result;
  }
  static void check(EndermanAtlasMath.Part p,int stx,int sty,int w,int h,int d,int dtx,int dty,int targetHeight){
    float[][] source=faceCorners(stx,sty,w,h,d,32),target=faceCorners(dtx,dty,w,targetHeight,d,64);
    for(int i=0;i<24;i++){
      float u=EndermanAtlasMath.u(p,source[i][0]),v=EndermanAtlasMath.v(p,source[i][1]);
      if(Float.floatToRawIntBits(u)!=Float.floatToRawIntBits(target[i][0])||Float.floatToRawIntBits(v)!=Float.floatToRawIntBits(target[i][1]))
        throw new AssertionError(p+" vertex"+i+" got="+u+","+v+" expected="+Arrays.toString(target[i]));
    }
  }
  public static void main(String[] args){
    check(EndermanAtlasMath.Part.HEAD,0,0,8,8,8,0,0,8);
    check(EndermanAtlasMath.Part.HAT,0,16,8,8,8,32,0,8);
    check(EndermanAtlasMath.Part.BODY,32,16,8,12,4,16,16,12);
    check(EndermanAtlasMath.Part.ARM,56,0,2,30,2,40,16,30);
    check(EndermanAtlasMath.Part.LEG,56,0,2,30,2,0,16,27);
    String pack="file/FA+All_Extensions-v1.9.2.zip";
    if(!EndermanAtlasMath.bodyAtlasGuard(pack,pack,"minecraft:textures/entity/enderman/enderman.png",64,64))throw new AssertionError();
    if(!EndermanAtlasMath.bodyAtlasGuard(pack,pack,"minecraft:optifine/random/entity/enderman/enderman2.png",64,64))throw new AssertionError();
    if(EndermanAtlasMath.bodyAtlasGuard(pack,pack,"minecraft:textures/entity/enderman/enderman_eyes.png",64,64))throw new AssertionError();
    if(EndermanAtlasMath.bodyAtlasGuard(pack,"file/FreshAnimations_v1.10.5.zip","minecraft:textures/entity/enderman/enderman.png",64,32))throw new AssertionError();
    System.out.println("PASS all120 face-corner UV mappings raw-float equal intended extension base layout; body variants accepted, eyes/Fresh fallback rejected.");
  }
}

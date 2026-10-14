/** Offline correction candidate. No game hook: preserves geometry, normals, colours, texture identity. */
public final class EndermanAtlasMath {
  enum Part {HEAD,HAT,BODY,ARM,LEG}
  static float u(Part part,float vanillaU) {
    return switch(part) {
      case HEAD -> vanillaU;
      case HAT -> vanillaU+.5f;
      case BODY,ARM -> vanillaU-.25f;
      case LEG -> vanillaU-.875f;
    };
  }
  static float v(Part part,float vanillaV) {
    return switch(part) {
      case HEAD -> vanillaV*.5f;
      case HAT -> vanillaV*.5f-.25f;
      case BODY -> vanillaV*.5f;
      case ARM -> vanillaV*.5f+.25f;
      // Vanilla leg is30 high; FA extension base leg27 high, with identical2×2caps.
      case LEG -> vanillaV<=.0625f?vanillaV*.5f+.25f:(18f+(vanillaV*32f-2f)*.9f)/64f;
    };
  }
  static boolean bodyAtlasGuard(String modelPack,String texturePack,String textureLabel,int width,int height){
    String expected="file/FA+All_Extensions-v1.9.2.zip";
    return expected.equals(modelPack)&&expected.equals(texturePack)&&width==64&&height==64
      &&textureLabel!=null&&!textureLabel.contains("eyes")
      &&(textureLabel.endsWith("/enderman.png")||textureLabel.endsWith("/enderman2.png"));
  }
}

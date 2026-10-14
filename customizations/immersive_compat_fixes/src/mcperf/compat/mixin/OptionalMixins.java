package mcperf.compat.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

public final class OptionalMixins implements IMixinConfigPlugin {
    public boolean shouldApplyMixin(String target, String mixin) {
        return FabricLoader.getInstance().isModLoaded(mixin.contains("Interactive") ? "interactivestuff" : "fieldguide");
    }
    public void onLoad(String p) {}
    public String getRefMapperConfig() { return null; }
    public void acceptTargets(Set<String> mine, Set<String> other) {}
    public List<String> getMixins() {
        var result=new java.util.ArrayList<String>();
        if(FabricLoader.getInstance().isModLoaded("interactivestuff")) result.add("InteractiveSoundMixin");
        if(FabricLoader.getInstance().isModLoaded("fieldguide")) result.addAll(List.of("FieldGuideOpenMixin","FieldGuideProgressMixin","FieldGuideProgressAccess"));
        return result;
    }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}

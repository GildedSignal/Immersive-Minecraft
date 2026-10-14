package mcperf.compat.mixin;
import mcperf.compat.ChestDirectPath;
import com.redsmods.sound_physics_perfected.RaycastingHelper;
import com.redsmods.sound_physics_perfected.storageclasses.AveragedSoundData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(value=RaycastingHelper.class,remap=false)
public abstract class SoundPhysicsChestMixin {
    @ModifyVariable(method="playMuffled",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private static AveragedSoundData directChest(AveragedSoundData value,Minecraft client,AveragedSoundData data,Vec3 listener,float volume,float pitch,Level world,Player player) {
        return ChestDirectPath.adjust(value,listener,world,player);
    }
}

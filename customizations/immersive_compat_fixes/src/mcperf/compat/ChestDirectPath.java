package mcperf.compat;
import com.redsmods.sound_physics_perfected.RaycastingHelper;
import com.redsmods.sound_physics_perfected.config.Config;
import com.redsmods.sound_physics_perfected.storageclasses.AveragedSoundData;
import com.redsmods.sound_physics_perfected.wrappers.RedTickableInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
/** The existing direct-source shortcut must also use the unobstructed direct path's gain. */
public final class ChestDirectPath {
    private static final Set<String> EVENTS=Set.of("minecraft:block.chest.open","minecraft:block.chest.close","minecraft:block.ender_chest.open","minecraft:block.ender_chest.close","minecraft:block.trapped_chest.open","minecraft:block.trapped_chest.close");
    public static long corrected;
    public static AveragedSoundData adjust(AveragedSoundData data,Vec3 listener,Level world,Player player) {
        if(data==null||world==null||player==null||listener==null||!Config.getInstance().shortcutDirectionality)return data;
        var sound=data.soundEntity.sound;
        if(!EVENTS.contains(sound.getIdentifier().toString())||!RaycastingHelper.hasLineOfSight(world,player,sound))return data;
        Vec3 source=sound instanceof RedTickableInstance tick?tick.getOriginalPosition():new Vec3(sound.getX(),sound.getY(),sound.getZ());
        Vec3 delta=source.subtract(listener);corrected++;
        return new AveragedSoundData(data.soundEntity,delta.normalize(),delta.length(),Math.max(data.totalWeight,1),data.rayCount,data.individualRays,1);
    }
}

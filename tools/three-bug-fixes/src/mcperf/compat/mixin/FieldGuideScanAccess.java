package mcperf.compat.mixin;
import com.evandev.fieldguide.network.ScanUnlockPacket;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=ScanUnlockPacket.class,remap=false)
public interface FieldGuideScanAccess {
    @Accessor("entryId") Identifier localEntry();
    @Accessor("variantId") String localVariant();
}

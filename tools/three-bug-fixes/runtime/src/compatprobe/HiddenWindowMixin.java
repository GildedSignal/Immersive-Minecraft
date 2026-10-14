package compatprobe.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(Minecraft.class)
public abstract class HiddenWindowMixin {
    @Redirect(method="<init>",at=@At(value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwShowWindow(J)V"),require=1)
    private void hide(long window) {}
}

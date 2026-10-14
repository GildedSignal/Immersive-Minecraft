package mcperf.papercompat.mixin;
import mcperf.papercompat.PaperChannels;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @ModifyVariable(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At("HEAD"),argsOnly=true)
    private Packet<?> advertise(Packet<?> packet) { return PaperChannels.outgoing((Connection)(Object)this,packet); }
    @Inject(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",at=@At("HEAD"))
    private void incoming(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) { PaperChannels.incoming((Connection)(Object)this,packet); }
}

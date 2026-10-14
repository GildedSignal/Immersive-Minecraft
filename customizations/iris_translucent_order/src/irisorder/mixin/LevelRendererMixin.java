/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
 *  com.llamalad7.mixinextras.sugar.Local
 *  net.minecraft.client.renderer.LevelRenderer
 *  net.minecraft.client.renderer.feature.FeatureRenderDispatcher$PreparedFrame
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package irisorder.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import irisorder.IrisTranslucentOrder;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LevelRenderer.class})
public abstract class LevelRendererMixin {
    @Unique
    private static boolean ito$ranEarly;

    @Inject(method={"lambda$addMainPass$0", "lambda$addMainPass$1"}, require=0, at={@At(value="INVOKE", shift=At.Shift.AFTER, target="Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid()V")})
    private void ito$translucentBeforeDeferred(CallbackInfo callbackInfo, @Local(argsOnly=true) FeatureRenderDispatcher.PreparedFrame preparedFrame) {
        ito$ranEarly = false;
        if (!IrisTranslucentOrder.shouldReorder()) {
            return;
        }
        preparedFrame.executeTranslucent();
        ito$ranEarly = true;
    }

    @WrapOperation(method={"lambda$addMainPass$0", "lambda$addMainPass$1"}, require=0, at={@At(value="INVOKE", target="Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeTranslucent()V")})
    private void ito$skipLateTranslucent(FeatureRenderDispatcher.PreparedFrame preparedFrame, Operation<Void> operation) {
        if (ito$ranEarly) {
            ito$ranEarly = false;
            return;
        }
        operation.call(new Object[]{preparedFrame});
    }
}


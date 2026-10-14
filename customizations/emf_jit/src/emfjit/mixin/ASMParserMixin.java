/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.objectweb.asm.ClassWriter
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  traben.entity_model_features.models.animation.math.asm.ASMParser
 */
package emfjit.mixin;

import emfjit.EmfJit;
import org.objectweb.asm.ClassWriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import traben.entity_model_features.models.animation.math.asm.ASMParser;

@Mixin(value={ASMParser.class}, remap=false)
public abstract class ASMParserMixin {
    @Redirect(method={"compileOrNull"}, at={@At(value="INVOKE", target="Lorg/objectweb/asm/ClassWriter;toByteArray()[B")}, require=0, remap=false)
    private static byte[] emfjit$splitHugeEval(ClassWriter classWriter) {
        return EmfJit.process(classWriter.toByteArray());
    }
}


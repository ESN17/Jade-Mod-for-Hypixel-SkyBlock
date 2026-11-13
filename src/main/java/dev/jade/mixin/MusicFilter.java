package dev.jade.mixin;

import dev.jade.Jade;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
abstract class MusicFilter {
    @Inject(method="play", at=@At("HEAD"), cancellable=true)
    private void jadeFilter(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> ci) {
        if (Jade.suppress(sound)) ci.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }
}

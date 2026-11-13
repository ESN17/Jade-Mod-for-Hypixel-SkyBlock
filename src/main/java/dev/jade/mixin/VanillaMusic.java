package dev.jade.mixin;

import dev.jade.Jade;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
abstract class VanillaMusic {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void jadePauseScheduler(CallbackInfo ci) {
        if (Jade.blockVanillaScheduler()) ci.cancel();
    }
}

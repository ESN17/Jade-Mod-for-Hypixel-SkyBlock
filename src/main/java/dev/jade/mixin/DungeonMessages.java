package dev.jade.mixin;

import dev.jade.Jade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class DungeonMessages {
    @Inject(method="handleSystemChat",at=@At("HEAD"))
    private void jadeDungeonMessage(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread() && !packet.overlay()) Jade.systemMessage(mc,packet.content().getString());
    }
}

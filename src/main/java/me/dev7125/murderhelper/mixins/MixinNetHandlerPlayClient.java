package me.dev7125.murderhelper.mixins;

import me.dev7125.murderhelper.util.GameStateDetector;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S45PacketTitle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {
    
    @Inject(method = "handleTitle", at = @At("HEAD"))
    private void onHandleTitle(S45PacketTitle packetIn, CallbackInfo ci) {
        GameStateDetector.getInstance().onTitlePacket(packetIn);
    }
}
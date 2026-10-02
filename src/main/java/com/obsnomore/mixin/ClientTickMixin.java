package com.obsnomore.mixin;

import com.obsnomore.ObsNoMore;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client tick hook: frame-pump throttle, scene text refresh, hotkeys.
 */
@Mixin(MinecraftClient.class)
public abstract class ClientTickMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void obsnomore$onTick(CallbackInfo ci) {
        try {
            ObsNoMore.onClientTick(MinecraftClient.getInstance());
        } catch (Throwable ignored) {
        }
    }
}

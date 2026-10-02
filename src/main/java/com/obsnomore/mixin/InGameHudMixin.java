package com.obsnomore.mixin;

import com.obsnomore.render.OverlayRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gameplay HUD hook: overlays + end-of-HUD frame capture for stream/record.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void obsnomore$renderOverlays(float partialTicks, boolean inScreen,
                                          int mouseX, int mouseY, CallbackInfo ci) {
        try {
            net.minecraft.client.MinecraftClient client =
                    net.minecraft.client.MinecraftClient.getInstance();
            OverlayRenderer.renderHud(client, inScreen);
        } catch (Throwable t) {
            // Never crash the HUD because of an overlay.
        }
        // Wayland frame pipe (no-op unless a session attached it): reads
        // after the HUD so 1-POV frames carry overlays baked in, while
        // 2-POV frames stay clean via cleanLocal().
        try {
            com.obsnomore.stream.PipeFeed.offer();
        } catch (Throwable t) {
            // Never crash the HUD because of the recorder.
        }
    }
}

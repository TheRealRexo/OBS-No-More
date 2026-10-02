package com.obsnomore.mixin;

import com.obsnomore.render.OverlayRenderer;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders text labels over GUI screens with proper alpha blending, and
 * captures the finished frame (screen included) for stream/record.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void obsnomore$renderChatOverScreen(int mouseX, int mouseY,
                                                float tickDelta, CallbackInfo ci) {
        Object self = this;
        try {
            net.minecraft.client.MinecraftClient client =
                    net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.currentScreen != self) return;
            String n = self.getClass().getName();
            boolean editor = n.contains("StreamOverlayEditorScreen")
                    || n.contains("StreamSettingsScreen");
            if (!editor) OverlayRenderer.renderOverScreen(client);
        } catch (Throwable t) {
            // Never break vanilla screens because of an overlay.
        }
    }
}

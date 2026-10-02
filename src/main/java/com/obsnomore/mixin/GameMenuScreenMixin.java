package com.obsnomore.mixin;

import com.obsnomore.gui.ObsButton;

import com.obsnomore.gui.StreamOverlayEditorScreen;

import net.minecraft.client.gui.screen.GameMenuScreen;

import net.minecraft.client.gui.screen.Screen;

import net.minecraft.client.gui.widget.ButtonWidget;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**

 * Pause menu (GuiIngameMenu): OBS icon button bottom-right opens the editor.

 */

@Mixin(GameMenuScreen.class)

public abstract class GameMenuScreenMixin extends Screen {

    @Inject(method = "init", at = @At("RETURN"))

    private void obsnomore$addObsButton(CallbackInfo ci) {

        buttons.add(new ObsButton(StreamOverlayEditorScreen.obsButtonId(),

                width - 30, height - 30));

    }

    @Inject(method = "render", at = @At("HEAD"))

    private void obsnomore$ensureButton(int mouseX, int mouseY, float tickDelta,

            CallbackInfo ci) {

        ObsButton.ensurePresent(buttons, width, height,
                StreamOverlayEditorScreen.obsButtonId());

    }

    @Inject(method = "buttonClicked",

            at = @At("HEAD"), cancellable = true)

    private void obsnomore$onObsButton(ButtonWidget button, CallbackInfo ci) {

        if (button.id == StreamOverlayEditorScreen.obsButtonId()) {

            StreamOverlayEditorScreen.open(client, (GameMenuScreen) (Object) this);

            ci.cancel();

        }

    }

}

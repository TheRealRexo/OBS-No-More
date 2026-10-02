package com.obsnomore.gui;

import com.obsnomore.render.PreviewTextures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 20x20 menu button with a procedurally drawn OBS glyph (red dot + "OBS"
 * label via fills/text, which render on every stack) plus the replaceable
 * icon PNG ({@code assets/obsnomore/textures/gui/obs_button.png}) drawn
 * over it when the resource pipeline resolves it.
 */
public class ObsButton extends ButtonWidget {
    public ObsButton(int id, int x, int y) {
        super(id, x, y, 20, 20, "");
    }

    /**
     * Re-adds the button if another mod rebuilt the menu button list after
     * us. Call from the menu render hook; cheap list scan, runs rarely.
     */
    @SuppressWarnings("unchecked")
    public static void ensurePresent(java.util.List<ButtonWidget> buttons,
                                     int width, int height, int id) {
        try {
            for (ButtonWidget b : buttons) {
                if (b != null && b.id == id) return;
            }
            buttons.add(new ObsButton(id, width - 30, height - 30));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void render(MinecraftClient client, int mouseX, int mouseY) {
        // Custom flat button: the vanilla gray background is deliberately
        // NOT drawn (super.render skipped) so the icon sits on a clean
        // dark panel instead of a double button look.
        if (!this.visible) return;
        boolean hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
        // Clean GUI ortho so custom menu projections can't fling the icon.
        int sw = 427;
        int sh = 240;
        try {
            if (client != null && client.currentScreen != null) {
                sw = client.currentScreen.width;
                sh = client.currentScreen.height;
            }
        } catch (Throwable ignored) {
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, sw, sh, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslated(0.0D, 0.0D, -2000.0D);
        try {
            com.obsnomore.render.OverlayRenderer.enableBlend();
            try {
                com.obsnomore.render.OverlayRenderer.fill(x, y, x + width, y + height,
                        hovered ? 0xFF2A2A2A : 0xFF161616);
                com.obsnomore.render.OverlayRenderer.drawBorder(x, y, x + width, y + height,
                        hovered ? 0xFF55FFFF : 0xFF45484D);
            } finally {
                com.obsnomore.render.OverlayRenderer.disableBlend();
            }
            Identifier icon = PreviewTextures.getIcon();
            if (icon != null) {
                PreviewTextures.drawIcon(icon, x + 2, y + 2, 16, 16);
            } else if (client != null && client.textRenderer != null) {
                // Procedural fallback only: the text sits where the icon
                // goes, so it must not paint when the icon is up (the "S"
                // of "OBS" would peek out from behind the art).
                com.obsnomore.render.OverlayRenderer.enableBlend();
                try {
                    com.obsnomore.render.OverlayRenderer.fill(x + 7, y + 2, x + 13, y + 8, 0xFFFF3B30);
                    com.obsnomore.render.OverlayRenderer.drawBorder(x + 7, y + 2, x + 13, y + 8, 0xFF7A0000);
                } finally {
                    com.obsnomore.render.OverlayRenderer.disableBlend();
                }
                client.textRenderer.draw("OBS", x + 3, y + 10, 0xFFFFFFFF);
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
        }
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }
}

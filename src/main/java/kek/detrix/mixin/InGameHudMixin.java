package kek.detrix.mixin;

import kek.detrix.overlay.DetrixRenderDemo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "render", at = @At("TAIL"))
    private void detrix$render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (this.client.world == null || this.client.player == null) {
            return;
        }

        Window window = this.client.getWindow();
        int framebufferWidth = window.getFramebufferWidth();
        int framebufferHeight = window.getFramebufferHeight();
        int scaleFactor = window.scaleFactor;

        DetrixRenderDemo.render(framebufferWidth, framebufferHeight, scaleFactor);
    }
}

package kek.detrix.mixin;

import kek.detrix.overlay.DetrixRenderDemo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Detrix");
    private static boolean logged;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/Window;swapBuffers(Lnet/minecraft/client/util/tracy/TracyFrameCapturer;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void detrix$renderAfterBlit(boolean tick, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.world == null) {
            return;
        }

        Window window = client.getWindow();
        int framebufferWidth = window.getFramebufferWidth();
        int framebufferHeight = window.getFramebufferHeight();
        int scaleFactor = window.scaleFactor;

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, framebufferWidth, framebufferHeight);
        GL11.glDrawBuffer(GL11.GL_BACK);
        GL11.glReadBuffer(GL11.GL_BACK);

        if (!logged) {
            logged = true;
            int drawFb = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int readFb = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int vpW;
            int vpH;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer vp = stack.mallocInt(4);
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
                vpW = vp.get(2);
                vpH = vp.get(3);
            }
            LOGGER.info("Detrix hook active: fb={}x{} scale={} drawFb={} readFb={} viewport={}x{}", framebufferWidth, framebufferHeight, scaleFactor, drawFb, readFb, vpW, vpH);
        }

        DetrixRenderDemo.render(framebufferWidth, framebufferHeight, scaleFactor);
    }
}

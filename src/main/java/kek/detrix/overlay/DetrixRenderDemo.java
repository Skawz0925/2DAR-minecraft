package kek.detrix.overlay;

import kek.detrix.render.ColorRGBA;
import kek.detrix.render.Detrix2D;

public final class DetrixRenderDemo {
    private DetrixRenderDemo() {
    }

    public static void render(int framebufferWidth, int framebufferHeight, int scaleFactor) {
        Detrix2D.beginFrame(framebufferWidth, framebufferHeight, scaleFactor);

        float x = 20f;
        float y = 20f;
        int round = 8;

        Detrix2D.roundedRect(x, 150f, 60f, 60f, 2f, ColorRGBA.rgba(255, 255, 255, 255));
        Detrix2D.roundedRectOutline(x, 250f, 60f, 60f, 2f, 2f, ColorRGBA.rgba(0, 0, 0, 255));
        Detrix2D.roundedRect(x + 80f, y + 70f, 120f, 120f, round, ColorRGBA.rgba(0, 0, 0, 100));
        Detrix2D.roundedRectOutline(x + 80f, 250f, 60f, 60f, round, 2f, ColorRGBA.rgba(255, 255, 255, 255));
        Detrix2D.blurredRoundedRect(x + 80f, y + 70f, 120f, 120f, round, 0.8f, ColorRGBA.rgba(0, 0, 0, 100));
        Detrix2D.roundedRect(x, y + 64f, 60f, 60f, 3f, ColorRGBA.rgba(0, 0, 0, 255));
        Detrix2D.roundedRectOutline(x, y + 300f, 60f, 60f, 3f, 1f, ColorRGBA.rgba(255, 255, 255, 220));
        Detrix2D.text("Detrix AaBb", x + 16f, y + 28f, 10f, ColorRGBA.rgba(240, 240, 240, 255));
        Detrix2D.text("сосиска", x + 16f, y + 38f, 10f, ColorRGBA.rgba(240, 240, 240, 255));
        Detrix2D.imageRounded(x + 80f, y + 300f, 60f, 60f, 4f, ColorRGBA.rgba(255, 255, 255, 255));

        Detrix2D.endFrame();
    }
}

package kek.detrix.render;

import kek.detrix.render.gl.QuadMesh;
import kek.detrix.render.gl.Framebuffer;
import kek.detrix.render.gl.ShaderProgram;
import kek.detrix.render.gl.Texture2D;
import kek.detrix.render.font.TtfFont;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public final class Detrix2D {
    private static boolean initialized;
    private static final int BLUR_DOWNSAMPLE = 1;

    private static QuadMesh quad;
    private static ShaderProgram roundedRectProgram;
    private static ShaderProgram roundedRectOutlineProgram;
    private static ShaderProgram blitProgram;
    private static ShaderProgram blurProgram;
    private static ShaderProgram texturedRoundedRectProgram;
    private static ShaderProgram textProgram;
    private static ShaderProgram fontAtlasDebugProgram;
    private static ShaderProgram imageRoundedProgram;

    private static float resolutionX;
    private static float resolutionY;
    private static int framebufferWidth;
    private static int framebufferHeight;
    private static int scaleFactor;

    private static GlStateBackup stateBackup;

    private static int captureTextureId;
    private static int captureTextureWidth;
    private static int captureTextureHeight;

    private static Framebuffer blurA;
    private static Framebuffer blurB;
    private static boolean blurPrepared;
    private static float preparedBlurRadius;

    private static final FontCache OPEN_SANS = new FontCache("detrix/fonts/opnsans.ttf");
    private static final String PACK_PNG_PATH = "detrix/pack.png";
    private static final Map<String, Texture2D> textureCache = new HashMap<>();
    private static boolean fontAtlasReadbackLogged;

    private Detrix2D() {
    }

    public static void beginFrame(int framebufferWidth, int framebufferHeight, int scaleFactor) {
        ensureInit();
        Detrix2D.stateBackup = GlStateBackup.capture();
        Detrix2D.scaleFactor = Math.max(1, scaleFactor);
        int vpW = stateBackup.viewportW();
        int vpH = stateBackup.viewportH();
        Detrix2D.framebufferWidth = vpW > 0 ? vpW : framebufferWidth;
        Detrix2D.framebufferHeight = vpH > 0 ? vpH : framebufferHeight;
        Detrix2D.resolutionX = Detrix2D.framebufferWidth / (float) Detrix2D.scaleFactor;
        Detrix2D.resolutionY = Detrix2D.framebufferHeight / (float) Detrix2D.scaleFactor;
        Detrix2D.blurPrepared = false;

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    public static void endFrame() {
        if (stateBackup != null) {
            stateBackup.restore();
            stateBackup = null;
        }
    }

    public static float width() {
        return resolutionX;
    }

    public static float height() {
        return resolutionY;
    }

    public static void roundedRect(float x, float y, float width, float height, float radius, ColorRGBA color) {
        ensureInit();

        roundedRectProgram.use();
        roundedRectProgram.uniform2f("uResolution", resolutionX, resolutionY);
        roundedRectProgram.uniform4f("uRect", x, y, width, height);
        roundedRectProgram.uniform1f("uRadius", radius);
        roundedRectProgram.uniform4f("uColor", color.r(), color.g(), color.b(), color.a());

        quad.bind();
        quad.draw();
        quad.unbind();

        ShaderProgram.stop();
    }

    public static void roundedRectOutline(float x, float y, float width, float height, float radius, float thickness, ColorRGBA color) {
        ensureInit();

        roundedRectOutlineProgram.use();
        roundedRectOutlineProgram.uniform2f("uResolution", resolutionX, resolutionY);
        roundedRectOutlineProgram.uniform4f("uRect", x, y, width, height);
        roundedRectOutlineProgram.uniform1f("uRadius", radius);
        roundedRectOutlineProgram.uniform1f("uThickness", thickness);
        roundedRectOutlineProgram.uniform4f("uColor", color.r(), color.g(), color.b(), color.a());

        quad.bind();
        quad.draw();
        quad.unbind();

        ShaderProgram.stop();
    }

    public static void blurredRoundedRect(float x, float y, float width, float height, float radius, float blurRadius, ColorRGBA color) {
        ensureInit();

        prepareBlur(blurRadius);

        texturedRoundedRectProgram.use();
        texturedRoundedRectProgram.uniform2f("uResolution", resolutionX, resolutionY);
        texturedRoundedRectProgram.uniform4f("uRect", x, y, width, height);
        texturedRoundedRectProgram.uniform1f("uRadius", radius);
        texturedRoundedRectProgram.uniform4f("uColor", color.r(), color.g(), color.b(), color.a());
        texturedRoundedRectProgram.uniform1i("uTexture", 0);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, blurA.textureId());
        quad.bind();
        quad.draw();
        quad.unbind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        ShaderProgram.stop();
    }

    public static void text(String text, float x, float y, float size, ColorRGBA color) {
        ensureInit();

        int pixelHeight = Math.max(8, Math.round(size * scaleFactor));
        TtfFont font = OPEN_SANS.get(pixelHeight);
        float invScale = 1f / scaleFactor;

        textProgram.use();
        textProgram.uniform2f("uResolution", resolutionX, resolutionY);
        textProgram.uniform4f("uColor", color.r(), color.g(), color.b(), color.a());
        textProgram.uniform1i("uTexture", 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, font.textureId());
        quad.bind();

        float cursorX = x;
        float baselineY = y + font.ascentPx() * invScale;
        float cursorY = baselineY;
        float lineHeight = font.lineHeightPx() * invScale;

        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            i += Character.charCount(codepoint);

            if (codepoint == '\n') {
                cursorX = x;
                cursorY += lineHeight;
                continue;
            }

            var ch = font.glyph(codepoint);

            float x0 = cursorX + ch.xoff() * invScale;
            float y0 = cursorY + ch.yoff() * invScale;
            float x1 = cursorX + ch.xoff2() * invScale;
            float y1 = cursorY + ch.yoff2() * invScale;

            float w = x1 - x0;
            float h = y1 - y0;

            if (w > 0f && h > 0f) {
                float u0 = ch.x0() / (float) font.atlasWidth();
                float v0 = 1f - (ch.y0() / (float) font.atlasHeight());
                float u1 = ch.x1() / (float) font.atlasWidth();
                float v1 = 1f - (ch.y1() / (float) font.atlasHeight());

                textProgram.uniform4f("uRect", x0, y0, w, h);
                textProgram.uniform4f("uUvRect", u0, v0, u1, v1);
                quad.draw();
            }

            cursorX += ch.xadvance() * invScale;
        }

        quad.unbind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        ShaderProgram.stop();
    }

    public static void debugFontAtlas(float x, float y, float width, float height, float size) {
        ensureInit();

        int pixelHeight = Math.max(8, Math.round(size * scaleFactor));
        TtfFont font = OPEN_SANS.get(pixelHeight);

        fontAtlasDebugProgram.use();
        fontAtlasDebugProgram.uniform2f("uResolution", resolutionX, resolutionY);
        fontAtlasDebugProgram.uniform1i("uTexture", 0);
        fontAtlasDebugProgram.uniform4f("uRect", x, y, width, height);
        fontAtlasDebugProgram.uniform4f("uUvRect", 0f, 1f, 1f, 0f);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, font.textureId());
        if (!fontAtlasReadbackLogged) {
            fontAtlasReadbackLogged = true;
            int w = font.atlasWidth();
            int h = font.atlasHeight();
            var buf = org.lwjgl.system.MemoryUtil.memAlloc(w * h * 4);
            int prevPackAlign = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            int prevPackRowLength = GL11.glGetInteger(GL12.GL_PACK_ROW_LENGTH);
            int prevPackSkipRows = GL11.glGetInteger(GL12.GL_PACK_SKIP_ROWS);
            int prevPackSkipPixels = GL11.glGetInteger(GL12.GL_PACK_SKIP_PIXELS);
            int prevPackImageHeight = GL11.glGetInteger(GL12.GL_PACK_IMAGE_HEIGHT);
            int prevPackSkipImages = GL11.glGetInteger(GL12.GL_PACK_SKIP_IMAGES);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL12.GL_PACK_IMAGE_HEIGHT, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_IMAGES, 0);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, prevPackAlign);
            GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, prevPackRowLength);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, prevPackSkipRows);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, prevPackSkipPixels);
            GL11.glPixelStorei(GL12.GL_PACK_IMAGE_HEIGHT, prevPackImageHeight);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_IMAGES, prevPackSkipImages);
            int maxR = 0;
            int maxA = 0;
            for (int i = 0; i < w * h; i++) {
                int r = buf.get(i * 4) & 0xFF;
                int a = buf.get(i * 4 + 3) & 0xFF;
                if (r > maxR) maxR = r;
                if (a > maxA) maxA = a;
            }
            org.lwjgl.system.MemoryUtil.memFree(buf);
            System.out.println("Detrix font atlas readback: size=" + w + "x" + h + " maxR=" + maxR + " maxA=" + maxA);
        }
        quad.bind();
        quad.draw();
        quad.unbind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        ShaderProgram.stop();
    }

    public static void imageRounded(float x, float y, float width, float height, float radius, ColorRGBA tint) {
        ensureInit();
        Texture2D texture = texture(PACK_PNG_PATH);
        imageRounded(texture, x, y, width, height, radius, tint);
    }

    public static void imageRounded(String resourcePath, float x, float y, float width, float height, float radius, ColorRGBA tint) {
        ensureInit();
        Texture2D texture = texture(resourcePath);
        imageRounded(texture, x, y, width, height, radius, tint);
    }

    private static void imageRounded(Texture2D texture, float x, float y, float width, float height, float radius, ColorRGBA tint) {
        imageRoundedProgram.use();
        imageRoundedProgram.uniform2f("uResolution", resolutionX, resolutionY);
        imageRoundedProgram.uniform4f("uRect", x, y, width, height);
        imageRoundedProgram.uniform1f("uRadius", radius);
        imageRoundedProgram.uniform4f("uColor", tint.r(), tint.g(), tint.b(), tint.a());
        imageRoundedProgram.uniform4f("uUvRect", 0f, 0f, 1f, 1f);
        imageRoundedProgram.uniform1i("uTexture", 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.id());
        quad.bind();
        quad.draw();
        quad.unbind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        ShaderProgram.stop();
    }

    private static Texture2D texture(String resourcePath) {
        Texture2D cached = textureCache.get(resourcePath);
        if (cached != null) {
            return cached;
        }
        Texture2D created = Texture2D.fromPngResource(resourcePath);
        textureCache.put(resourcePath, created);
        return created;
    }

    private static void ensureInit() {
        if (initialized) {
            return;
        }
        quad = new QuadMesh();
        roundedRectProgram = new ShaderProgram("detrix/shaders/quad.vert", "detrix/shaders/rounded_rect.frag");
        roundedRectOutlineProgram = new ShaderProgram("detrix/shaders/quad.vert", "detrix/shaders/rounded_rect_outline.frag");
        blitProgram = new ShaderProgram("detrix/shaders/quad.vert", "detrix/shaders/blit.frag");
        blurProgram = new ShaderProgram("detrix/shaders/quad.vert", "detrix/shaders/blur.frag");
        texturedRoundedRectProgram = new ShaderProgram("detrix/shaders/quad.vert", "detrix/shaders/textured_round_rect.frag");
        textProgram = new ShaderProgram("detrix/shaders/quad.vert", "detrix/shaders/text.frag");
        fontAtlasDebugProgram = new ShaderProgram("detrix/shaders/quad.vert", "detrix/shaders/font_atlas_debug.frag");
        imageRoundedProgram = new ShaderProgram("detrix/shaders/quad.vert", "detrix/shaders/image_rounded.frag");
        initialized = true;
    }

    private static void prepareBlur(float blurRadius) {
        if (blurPrepared && Float.compare(preparedBlurRadius, blurRadius) == 0) {
            return;
        }

        ensureCaptureTexture(framebufferWidth, framebufferHeight);
        ensureBlurFramebuffers(framebufferWidth, framebufferHeight);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureTextureId);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, framebufferWidth, framebufferHeight);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        float radiusPx = Math.max(0f, blurRadius) * scaleFactor / BLUR_DOWNSAMPLE;

        blurA.bind();
        blitProgram.use();
        blitProgram.uniform2f("uResolution", blurA.width(), blurA.height());
        blitProgram.uniform4f("uRect", 0f, 0f, blurA.width(), blurA.height());
        blitProgram.uniform1i("uTexture", 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureTextureId);
        quad.bind();
        quad.draw();
        quad.unbind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        ShaderProgram.stop();

        blurB.bind();
        blurProgram.use();
        blurProgram.uniform2f("uResolution", blurB.width(), blurB.height());
        blurProgram.uniform4f("uRect", 0f, 0f, blurB.width(), blurB.height());
        blurProgram.uniform1i("uTexture", 0);
        blurProgram.uniform2f("uTextureResolution", blurA.width(), blurA.height());
        blurProgram.uniform2f("uDirection", 1f, 0f);
        blurProgram.uniform1f("uRadius", radiusPx);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, blurA.textureId());
        quad.bind();
        quad.draw();
        quad.unbind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        ShaderProgram.stop();

        blurA.bind();
        blurProgram.use();
        blurProgram.uniform2f("uResolution", blurA.width(), blurA.height());
        blurProgram.uniform4f("uRect", 0f, 0f, blurA.width(), blurA.height());
        blurProgram.uniform1i("uTexture", 0);
        blurProgram.uniform2f("uTextureResolution", blurB.width(), blurB.height());
        blurProgram.uniform2f("uDirection", 0f, 1f);
        blurProgram.uniform1f("uRadius", radiusPx);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, blurB.textureId());
        quad.bind();
        quad.draw();
        quad.unbind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        ShaderProgram.stop();

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, stateBackup.drawFramebuffer());
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, stateBackup.readFramebuffer());
        GL11.glViewport(stateBackup.viewportX(), stateBackup.viewportY(), stateBackup.viewportW(), stateBackup.viewportH());

        blurPrepared = true;
        preparedBlurRadius = blurRadius;
    }

    private static void ensureCaptureTexture(int width, int height) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        if (captureTextureId == 0) {
            captureTextureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureTextureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        if (captureTextureWidth != w || captureTextureHeight != h) {
            captureTextureWidth = w;
            captureTextureHeight = h;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureTextureId);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
    }

    private static void ensureBlurFramebuffers(int fbWidth, int fbHeight) {
        int w = Math.max(1, fbWidth / BLUR_DOWNSAMPLE);
        int h = Math.max(1, fbHeight / BLUR_DOWNSAMPLE);
        if (blurA == null) {
            blurA = new Framebuffer(w, h);
            blurB = new Framebuffer(w, h);
            return;
        }
        if (blurA.width() != w || blurA.height() != h) {
            blurA.resize(w, h);
            blurB.resize(w, h);
        }
    }

    private record GlStateBackup(
            int program,
            int vao,
            int drawFramebuffer,
            int readFramebuffer,
            int activeTexture,
            int texture2D,
            boolean blend,
            boolean depthTest,
            boolean cull,
            boolean scissor,
            boolean stencil,
            int colorMask,
            int blendSrcRgb,
            int blendDstRgb,
            int blendSrcAlpha,
            int blendDstAlpha,
            int viewportX,
            int viewportY,
            int viewportW,
            int viewportH
    ) {
        static GlStateBackup capture() {
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int texture2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

            int viewportX;
            int viewportY;
            int viewportW;
            int viewportH;
            int colorMask;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer viewport = stack.mallocInt(4);
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                viewportX = viewport.get(0);
                viewportY = viewport.get(1);
                viewportW = viewport.get(2);
                viewportH = viewport.get(3);

                var mask = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
                colorMask = (mask.get(0) != 0 ? 1 : 0)
                        | (mask.get(1) != 0 ? 2 : 0)
                        | (mask.get(2) != 0 ? 4 : 0)
                        | (mask.get(3) != 0 ? 8 : 0);
            }

            return new GlStateBackup(
                    program,
                    vao,
                    drawFramebuffer,
                    readFramebuffer,
                    activeTexture,
                    texture2D,
                    blend,
                    depthTest,
                    cull,
                    scissor,
                    stencil,
                    colorMask,
                    blendSrcRgb,
                    blendDstRgb,
                    blendSrcAlpha,
                    blendDstAlpha,
                    viewportX,
                    viewportY,
                    viewportW,
                    viewportH
            );
        }

        void restore() {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL13.glActiveTexture(activeTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2D);

            if (blend) {
                GL11.glEnable(GL11.GL_BLEND);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (depthTest) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            } else {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            if (cull) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            } else {
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
            if (scissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            if (stencil) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }

            GL11.glColorMask((colorMask & 1) != 0, (colorMask & 2) != 0, (colorMask & 4) != 0, (colorMask & 8) != 0);

            GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            GL11.glViewport(viewportX, viewportY, viewportW, viewportH);
        }
    }

    private static final class FontCache {
        private final String resourcePath;
        private final Map<Integer, TtfFont> fonts = new HashMap<>();

        private FontCache(String resourcePath) {
            this.resourcePath = resourcePath;
        }

        private TtfFont get(int pixelHeight) {
            TtfFont cached = fonts.get(pixelHeight);
            if (cached != null) {
                return cached;
            }
            TtfFont font = TtfFont.fromResource(resourcePath, pixelHeight);
            fonts.put(pixelHeight, font);
            return font;
        }
    }
}

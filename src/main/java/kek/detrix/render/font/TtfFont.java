package kek.detrix.render.font;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TtfFont {
    private static final int ASCII_FIRST = 32;
    private static final int ASCII_COUNT = 95;
    private static final int CYR_FIRST = 0x0400;
    private static final int CYR_COUNT = 256;

    private final int pixelHeight;
    private final int atlasWidth;
    private final int atlasHeight;
    private final int textureId;
    private final Map<Integer, GlyphInfo> glyphs;
    private final float ascentPx;
    private final float lineHeightPx;
    private static boolean atlasCpuLogged;

    private TtfFont(
            int pixelHeight,
            int atlasWidth,
            int atlasHeight,
            int textureId,
            Map<Integer, GlyphInfo> glyphs,
            float ascentPx,
            float lineHeightPx
    ) {
        this.pixelHeight = pixelHeight;
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
        this.textureId = textureId;
        this.glyphs = glyphs;
        this.ascentPx = ascentPx;
        this.lineHeightPx = lineHeightPx;
    }

    public static class GlyphInfo {
        final int x0;
        final int y0;
        final int x1;
        final int y1;
        final float xoff;
        final float yoff;
        final float xoff2;
        final float yoff2;
        final float xadvance;

        GlyphInfo(int x0, int y0, int x1, int y1, float xoff, float yoff, float xoff2, float yoff2, float xadvance) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.xoff = xoff;
            this.yoff = yoff;
            this.xoff2 = xoff2;
            this.yoff2 = yoff2;
            this.xadvance = xadvance;
        }

        public float x0() {
            return x0;
        }

        public float y0() {
            return y0;
        }

        public float x1() {
            return x1;
        }

        public float y1() {
            return y1;
        }

        public float xoff() {
            return xoff;
        }

        public float yoff() {
            return yoff;
        }

        public float xoff2() {
            return xoff2;
        }

        public float yoff2() {
            return yoff2;
        }

        public float xadvance() {
            return xadvance;
        }
    }

    public static TtfFont fromResource(String resourcePath, int pixelHeight) {
        Font font;
        try (InputStream input = TtfFont.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Resource not found: " + resourcePath);
            }
            font = Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(Font.PLAIN, (float) pixelHeight);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        int padding = 2;
        Map<Integer, GlyphInfo> glyphs = new HashMap<>();

        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tmpG = tmp.createGraphics();
        tmpG.setFont(font);
        FontMetrics fontMetrics = tmpG.getFontMetrics();
        float ascentPx = fontMetrics.getAscent();
        float lineHeightPx = fontMetrics.getHeight();
        int spaceAdvance = Math.max(1, fontMetrics.charWidth(' '));
        tmpG.dispose();

        List<Integer> cps = new ArrayList<>(ASCII_COUNT + CYR_COUNT + 4);
        for (int cp = ASCII_FIRST; cp < ASCII_FIRST + ASCII_COUNT; cp++) {
            cps.add(cp);
        }
        for (int cp = CYR_FIRST; cp < CYR_FIRST + CYR_COUNT; cp++) {
            if (font.canDisplay(cp)) {
                cps.add(cp);
            }
        }
        if (!cps.contains((int) '?')) {
            cps.add((int) '?');
        }

        int range = Math.max(1, cps.size());
        int charsVert = (int) (Math.ceil(Math.sqrt(range)) * 1.5);

        AffineTransform affineTransform = new AffineTransform();
        FontRenderContext frc = new FontRenderContext(affineTransform, true, false);

        List<PlacedGlyph> placed = new ArrayList<>(range);

        int generated = 0;
        int charNX = 0;
        int maxX = 0;
        int maxY = 0;
        int currentX = 0;
        int currentY = 0;
        int currentRowMaxY = 0;

        for (int cp : cps) {
            char c = (char) cp;
            int gw;
            int gh;
            if (c == ' ') {
                gw = spaceAdvance;
                gh = (int) Math.ceil(lineHeightPx);
            } else {
                Rectangle2D bounds = font.getStringBounds(String.valueOf(c), frc);
                gw = (int) Math.ceil(bounds.getWidth());
                gh = (int) Math.ceil(bounds.getHeight());
                if (gw <= 0) gw = 1;
                if (gh <= 0) gh = 1;
            }

            if (charNX >= charsVert) {
                currentX = 0;
                currentY += currentRowMaxY + padding;
                charNX = 0;
                currentRowMaxY = 0;
            }

            maxX = Math.max(maxX, currentX + gw);
            maxY = Math.max(maxY, currentY + gh);

            placed.add(new PlacedGlyph(cp, currentX, currentY, gw, gh));

            currentRowMaxY = Math.max(currentRowMaxY, gh);
            currentX += gw + padding;
            charNX++;
            generated++;
        }

        int atlasW = Math.max(maxX + padding, 1);
        int atlasH = Math.max(maxY + padding, 1);

        BufferedImage image = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setFont(font);
        g2d.setColor(new java.awt.Color(255, 255, 255, 0));
        g2d.fillRect(0, 0, atlasW, atlasH);
        g2d.setColor(java.awt.Color.WHITE);

        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (PlacedGlyph pg : placed) {
            char c = (char) pg.codepoint;
            if (c != ' ') {
                g2d.drawString(String.valueOf(c), pg.u, pg.v + fontMetrics.getAscent());
            }

            if (c == ' ') {
                glyphs.put(pg.codepoint, new GlyphInfo(0, 0, 0, 0, 0f, 0f, 0f, 0f, spaceAdvance));
            } else {
                int adv = fontMetrics.charWidth(c);
                if (adv <= 0) {
                    adv = pg.w;
                }
                glyphs.put(pg.codepoint, new GlyphInfo(
                        pg.u,
                        pg.v,
                        pg.u + pg.w,
                        pg.v + pg.h,
                        0f,
                        -ascentPx,
                        pg.w,
                        pg.h - ascentPx,
                        adv
                ));
            }
        }

        g2d.dispose();

        int[] argb = new int[atlasW * atlasH];
        image.getRGB(0, 0, atlasW, atlasH, argb, 0, atlasW);

        if (!atlasCpuLogged) {
            atlasCpuLogged = true;
            int maxA = 0;
            int nonZero = 0;
            for (int p : argb) {
                int a = (p >>> 24) & 0xFF;
                if (a > maxA) maxA = a;
                if (a != 0) nonZero++;
            }
            System.out.println("Detrix font atlas CPU: size=" + atlasW + "x" + atlasH + " maxA=" + maxA + " nonZero=" + nonZero);
        }

        ByteBuffer rgba = MemoryUtil.memAlloc(atlasW * atlasH * 4);
        for (int y = atlasH - 1; y >= 0; y--) {
            int row = y * atlasW;
            for (int x = 0; x < atlasW; x++) {
                int a = (argb[row + x] >>> 24) & 0xFF;
                byte v = (byte) a;
                rgba.put(v);
                rgba.put(v);
                rgba.put(v);
                rgba.put(v);
            }
        }
        rgba.flip();

        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        int prevUnpackAlign = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int prevUnpackRowLength = GL11.glGetInteger(GL12.GL_UNPACK_ROW_LENGTH);
        int prevUnpackSkipRows = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_ROWS);
        int prevUnpackSkipPixels = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_PIXELS);
        int prevUnpackImageHeight = GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT);
        int prevUnpackSkipImages = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, atlasW, atlasH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlign);
        GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, prevUnpackRowLength);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, prevUnpackSkipRows);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, prevUnpackSkipPixels);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, prevUnpackImageHeight);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, prevUnpackSkipImages);
        int err = GL11.glGetError();
        if (err != GL11.GL_NO_ERROR) {
            System.out.println("Detrix glTexImage2D error=" + err);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        MemoryUtil.memFree(rgba);

        return new TtfFont(pixelHeight, atlasW, atlasH, textureId, new HashMap<>(glyphs), ascentPx, lineHeightPx);
    }

    private record PlacedGlyph(int codepoint, int u, int v, int w, int h) {
    }

    public int pixelHeight() { return pixelHeight; }
    public int atlasWidth() { return atlasWidth; }
    public int atlasHeight() { return atlasHeight; }
    public int textureId() { return textureId; }
    public float ascentPx() { return ascentPx; }
    public float lineHeightPx() { return lineHeightPx; }

    public GlyphInfo glyph(int codepoint) {
        GlyphInfo g = glyphs.get(codepoint);
        if (g != null) return g;
        
        g = glyphs.get((int) '?');
        if (g != null) return g;
        
        return glyphs.get(ASCII_FIRST);
    }
}

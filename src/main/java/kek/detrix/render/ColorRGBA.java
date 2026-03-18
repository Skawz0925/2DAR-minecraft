package kek.detrix.render;

public record ColorRGBA(float r, float g, float b, float a) {
    public static ColorRGBA rgba(int r, int g, int b, int a) {
        return new ColorRGBA(r / 255f, g / 255f, b / 255f, a / 255f);
    }

    public static ColorRGBA rgba(int r, int g, int b) {
        return rgba(r, g, b, 255);
    }
}


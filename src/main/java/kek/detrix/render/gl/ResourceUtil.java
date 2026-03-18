package kek.detrix.render.gl;

import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class ResourceUtil {
    private ResourceUtil() {
    }

    public static String readUtf8(String path) {
        byte[] bytes;
        try (InputStream inputStream = ResourceUtil.class.getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            bytes = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static ByteBuffer readToDirectBuffer(String path) {
        byte[] bytes;
        try (InputStream inputStream = ResourceUtil.class.getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            bytes = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        return buffer;
    }
}


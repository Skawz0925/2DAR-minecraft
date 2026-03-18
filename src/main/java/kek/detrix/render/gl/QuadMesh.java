package kek.detrix.render.gl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public final class QuadMesh {
    private final int vaoId;
    private final int vboId;

    public QuadMesh() {
        this.vaoId = GL30.glGenVertexArrays();
        this.vboId = GL15.glGenBuffers();

        float[] vertices = {
                0f, 0f, 0f, 0f,
                1f, 0f, 1f, 0f,
                1f, 1f, 1f, 1f,
                0f, 0f, 0f, 0f,
                1f, 1f, 1f, 1f,
                0f, 1f, 0f, 1f
        };

        FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
        buffer.put(vertices).flip();

        GL30.glBindVertexArray(this.vaoId);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        int stride = 4 * Float.BYTES;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * Float.BYTES);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        MemoryUtil.memFree(buffer);
    }

    public void bind() {
        GL30.glBindVertexArray(this.vaoId);
    }

    public void unbind() {
        GL30.glBindVertexArray(0);
    }

    public void draw() {
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    public void delete() {
        GL30.glDeleteVertexArrays(this.vaoId);
        GL15.glDeleteBuffers(this.vboId);
    }
}


package kek.detrix.render.gl;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.Map;

public final class ShaderProgram {
    private final int programId;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    public ShaderProgram(String vertexResourcePath, String fragmentResourcePath) {
        int vertexShaderId = compileShader(GL20.GL_VERTEX_SHADER, ResourceUtil.readUtf8(vertexResourcePath));
        int fragmentShaderId = compileShader(GL20.GL_FRAGMENT_SHADER, ResourceUtil.readUtf8(fragmentResourcePath));

        this.programId = GL20.glCreateProgram();
        GL20.glAttachShader(this.programId, vertexShaderId);
        GL20.glAttachShader(this.programId, fragmentShaderId);
        GL20.glBindAttribLocation(this.programId, 0, "aUnitPos");
        GL20.glBindAttribLocation(this.programId, 1, "aUnitUv");
        GL30.glBindFragDataLocation(this.programId, 0, "fragColor");
        GL20.glLinkProgram(this.programId);

        if (GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(this.programId);
            GL20.glDeleteProgram(this.programId);
            GL20.glDeleteShader(vertexShaderId);
            GL20.glDeleteShader(fragmentShaderId);
            throw new IllegalStateException(log);
        }

        GL20.glDetachShader(this.programId, vertexShaderId);
        GL20.glDetachShader(this.programId, fragmentShaderId);
        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);
    }

    private static int compileShader(int type, String source) {
        int shaderId = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);
        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shaderId);
            GL20.glDeleteShader(shaderId);
            throw new IllegalStateException(log);
        }
        return shaderId;
    }

    public void use() {
        GL20.glUseProgram(this.programId);
    }

    public static void stop() {
        GL20.glUseProgram(0);
    }

    public void delete() {
        GL20.glDeleteProgram(this.programId);
    }

    public void uniform1f(String name, float value) {
        GL20.glUniform1f(uniformLocation(name), value);
    }

    public void uniform2f(String name, float x, float y) {
        GL20.glUniform2f(uniformLocation(name), x, y);
    }

    public void uniform4f(String name, float x, float y, float z, float w) {
        GL20.glUniform4f(uniformLocation(name), x, y, z, w);
    }

    public void uniform1i(String name, int value) {
        GL20.glUniform1i(uniformLocation(name), value);
    }

    private int uniformLocation(String name) {
        Integer cached = this.uniformLocations.get(name);
        if (cached != null) {
            return cached;
        }
        int location = GL20.glGetUniformLocation(this.programId, name);
        this.uniformLocations.put(name, location);
        return location;
    }
}

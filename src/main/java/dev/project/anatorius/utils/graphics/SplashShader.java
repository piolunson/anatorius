package dev.project.anatorius.utils.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public class SplashShader implements IMinecraft {
    private static SplashShader INSTANCE;

    private int vertexShaderId = 0;
    private int fragShaderId   = 0;
    private int programId      = 0;
    private int vaoId          = 0;
    private int vboId          = 0;

    private boolean initialized  = false;
    private boolean failedToInit = false;
    private float   time         = 0f;

    private static final String VERT = """
            #version 150
            in vec2 Position;
            void main() {
                gl_Position = vec4(Position, 0.0, 1.0);
            }
            """;

    private static final String FRAG = """
            #version 150

            uniform float u_Time;
            uniform vec2  u_Resolution;

            out vec4 fragColor;

            float WEIGHT;

            float line(vec2 p, vec2 p0, vec2 p1, float w) {
                vec2 d = p1 - p0;
                float denom = max(dot(d, d), 0.0001);
                float t = clamp(dot(d, p - p0) / denom, 0.0, 1.0);
                vec2 proj = p0 + d * t;
                float dist = max(length(p - proj), 0.001);
                dist = 1.0 / dist * WEIGHT * w;
                return min(dist * dist, 1.0);
            }

            vec3 hsv(float h, float s, float v) {
                vec4 t = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
                vec3 p = abs(fract(vec3(h) + t.xyz) * 6.0 - vec3(t.w));
                return v * mix(vec3(t.x), clamp(p - vec3(t.x), 0.0, 1.0), s);
            }

            void main(void) {
                WEIGHT = 20.0 / max(u_Resolution.x, 1.0);

                vec2 uv = gl_FragCoord.xy / u_Resolution.xy;
                uv = uv * 2.0 - 1.0;
                uv.x *= u_Resolution.x / u_Resolution.y;

                float len = max(length(uv), 0.001);
                float t2 = u_Time * 0.31415 + sin(len + u_Time * 0.2) / len * 0.1;
                vec3 c = vec3(0.0);

                for (float i = 8.0; i < 24.0; i += 2.0) {
                    float f = line(uv,
                        vec2(cos(t2*i)/2.0, sin(t2*i)/2.0),
                        vec2(sin(t2*i)/2.0, -cos(t2*i)/2.0),
                        0.5);
                    c += hsv(i / 24.0, 1.0, 1.0) * f;
                }

                fragColor = vec4(c, 1.0);
            }
            """;

    private SplashShader() {}

    public static SplashShader getInstance() {
        if (INSTANCE == null) INSTANCE = new SplashShader();
        return INSTANCE;
    }

    public void render(DrawContext context, int x, int y, int width, int height, float delta) {
        if (width <= 0 || height <= 0 || failedToInit) return;

        if (!initialized) {
            init();
            if (!initialized) return;
        }

        try {
            time += delta * 0.05f;

            int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int prevVao     = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean prevCull  = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL20.glUseProgram(programId);

            int uTime = GL20.glGetUniformLocation(programId, "u_Time");
            int uRes  = GL20.glGetUniformLocation(programId, "u_Resolution");

            if (uTime >= 0) GL20.glUniform1f(uTime, time);
            if (uRes  >= 0) GL20.glUniform2f(uRes,
                (float) mc.getWindow().getFramebufferWidth(),
                (float) mc.getWindow().getFramebufferHeight());

            uploadQuad(x, y, width, height);

            GL30.glBindVertexArray(vaoId);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            GL30.glBindVertexArray(0);

            GL20.glUseProgram(prevProgram);
            GL30.glBindVertexArray(prevVao);

            if (prevDepth) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (prevCull)  GL11.glEnable(GL11.GL_CULL_FACE);
            if (!prevBlend) GL11.glDisable(GL11.GL_BLEND);
        } catch (Exception e) {
            System.err.println("[SplashShader] Render error:");
            e.printStackTrace();
        }
    }

    private void init() {
        if (initialized || failedToInit) return;
        try {
            vertexShaderId = compileShader(GL20.GL_VERTEX_SHADER,   VERT, "splash.vsh");
            fragShaderId   = compileShader(GL20.GL_FRAGMENT_SHADER, FRAG, "splash.fsh");
            programId      = linkProgram(vertexShaderId, fragShaderId);

            vaoId = GL30.glGenVertexArrays();
            vboId = GL15.glGenBuffers();

            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            initialized = true;
            System.out.println("[SplashShader] Init OK");
        } catch (Exception e) {
            System.err.println("[SplashShader] Init failed: " + e.getMessage());
            e.printStackTrace();
            cleanup();
            failedToInit = true;
        }
    }

    private int compileShader(int type, String source, String name) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(id, 4096);
            GL20.glDeleteShader(id);
            throw new IllegalStateException("[" + name + "] compile error:\n" + log);
        }
        return id;
    }

    private int linkProgram(int vert, int frag) {
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vert);
        GL20.glAttachShader(prog, frag);
        GL20.glBindAttribLocation(prog, 0, "Position");
        GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(prog, 4096);
            GL20.glDeleteProgram(prog);
            throw new IllegalStateException("Program link error:\n" + log);
        }
        return prog;
    }

    private void uploadQuad(int x, int y, int width, int height) {
        float[] quad = {
            -1f,  1f,
             1f,  1f,
             1f, -1f,
            -1f,  1f,
             1f, -1f,
            -1f, -1f
        };

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        FloatBuffer fb = MemoryUtil.memAllocFloat(12);
        try {
            fb.put(quad).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        } finally {
            MemoryUtil.memFree(fb);
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void cleanup() {
        if (vboId != 0)         { GL15.glDeleteBuffers(vboId);         vboId = 0; }
        if (vaoId != 0)         { GL30.glDeleteVertexArrays(vaoId);    vaoId = 0; }
        if (programId != 0)     { GL20.glDeleteProgram(programId);     programId = 0; }
        if (vertexShaderId != 0){ GL20.glDeleteShader(vertexShaderId); vertexShaderId = 0; }
        if (fragShaderId != 0)  { GL20.glDeleteShader(fragShaderId);   fragShaderId = 0; }
        initialized = false;
    }
}

package dev.project.anatorius.utils.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public class MainMenuShader implements IMinecraft {
    private static MainMenuShader INSTANCE;

    private int programId = 0;
    private int vertexShaderId = 0;
    private int fragmentShaderId = 0;
    private int vaoId = 0;
    private int vboId = 0;
    private boolean initialized = false;
    private boolean failedToInit = false;

    private int timeLoc = -1;
    private int resolutionLoc = -1;

    private float time = 0f;

    private static final float[] QUAD = {
        -1f, -1f, 0f,
         1f, -1f, 0f,
         1f,  1f, 0f,
        -1f, -1f, 0f,
         1f,  1f, 0f,
        -1f,  1f, 0f,
    };

    private static final String VERT = """
            #version 150
            in vec3 Position;
            void main() {
                gl_Position = vec4(Position, 1.0);
            }
            """;

    private static final String FRAG = """
            #version 150

            #define NUM_OCTAVES 6

            uniform float time;
            uniform vec2 resolution;

            out vec4 fragColor;

            float random(vec2 pos) {
                return fract(sin(dot(pos.xy, vec2(12.9898, 78.233))) * 43758.5453123);
            }

            float noise(vec2 pos) {
                vec2 i = floor(pos);
                vec2 f = fract(pos);
                float a = random(i + vec2(0.0, 0.0));
                float b = random(i + vec2(1.0, 0.0));
                float c = random(i + vec2(0.0, 1.0));
                float d = random(i + vec2(1.0, 1.0));
                vec2 u = f * f * (3.0 - 2.0 * f);
                return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
            }

            float fbm(vec2 pos) {
                float v = 0.0;
                float a = 0.5;
                vec2 shift = vec2(100.0);
                mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
                for (int i = 0; i < NUM_OCTAVES; i++) {
                    float dir = mod(float(i), 2.0) > 0.5 ? 1.0 : -1.0;
                    v += a * noise(pos - 0.05 * dir * time);
                    pos = rot * pos * 2.0 + shift;
                    a *= 0.5;
                }
                return v;
            }

            void main(void) {
                vec2 p = (gl_FragCoord.xy * 2.0 - resolution.xy) / min(resolution.x, resolution.y);
                p -= vec2(12.0, 0.0);

                float time2 = 1.0;

                vec2 q = vec2(0.0);
                q.x = fbm(p + 0.00 * time2);
                q.y = fbm(p + vec2(1.0));
                vec2 r = vec2(0.0);
                r.x = fbm(p + 1.0 * q + vec2(1.7, 9.2) + 0.15 * time2);
                r.y = fbm(p + 1.0 * q + vec2(8.3, 2.8) + 0.126 * time2);
                float f = fbm(p + r);

                vec3 color = mix(
                    vec3(0.3, 0.3, 0.6),
                    vec3(0.7, 0.7, 0.7),
                    clamp((f * f) * 4.0, 0.0, 1.0)
                );

                color = mix(
                    color,
                    vec3(0.7, 0.7, 0.7),
                    clamp(length(q), 0.0, 1.0)
                );

                color = mix(
                    color,
                    vec3(0.4, 0.4, 0.4),
                    clamp(length(r.x), 0.0, 1.0)
                );

                color = (f * f * f + 0.9 * f * f + 0.8 * f) * color;

                fragColor = vec4(color * 0.7, 1.0);
            }
            """;

    private MainMenuShader() {}

    public static MainMenuShader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MainMenuShader();
        }
        return INSTANCE;
    }

    private static void clearGLErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
    }

    private static boolean checkGLError(String stage) {
        int err = GL11.glGetError();
        if (err != GL11.GL_NO_ERROR) {
            System.err.println("[Anatorius-Shader] GL error at " + stage + ": 0x" + Integer.toHexString(err));
            return true;
        }
        return false;
    }

    private void init() {
        if (initialized || failedToInit) return;

        System.out.println("[Anatorius-Shader] Initializing main menu shader...");
        clearGLErrors();

        try {
            vertexShaderId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vertexShaderId, VERT);
            GL20.glCompileShader(vertexShaderId);
            if (GL20.glGetShaderi(vertexShaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("[Anatorius-Shader] VERTEX compile error:\n" + GL20.glGetShaderInfoLog(vertexShaderId, 2048));
                cleanup(); failedToInit = true; return;
            }

            fragmentShaderId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fragmentShaderId, FRAG);
            GL20.glCompileShader(fragmentShaderId);
            if (GL20.glGetShaderi(fragmentShaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("[Anatorius-Shader] FRAGMENT compile error:\n" + GL20.glGetShaderInfoLog(fragmentShaderId, 2048));
                cleanup(); failedToInit = true; return;
            }

            programId = GL20.glCreateProgram();
            GL20.glAttachShader(programId, vertexShaderId);
            GL20.glAttachShader(programId, fragmentShaderId);
            GL20.glBindAttribLocation(programId, 0, "Position");
            GL20.glLinkProgram(programId);
            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                System.err.println("[Anatorius-Shader] LINK error:\n" + GL20.glGetProgramInfoLog(programId, 2048));
                cleanup(); failedToInit = true; return;
            }

            timeLoc = GL20.glGetUniformLocation(programId, "time");
            resolutionLoc = GL20.glGetUniformLocation(programId, "resolution");

            vaoId = GL30.glGenVertexArrays();
            vboId = GL15.glGenBuffers();

            int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);

            FloatBuffer fb = MemoryUtil.memAllocFloat(QUAD.length);
            try {
                fb.put(QUAD).flip();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
            } finally {
                MemoryUtil.memFree(fb);
            }

            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * 4, 0);

            GL30.glBindVertexArray(prevVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            checkGLError("init");
            initialized = true;
            System.out.println("[Anatorius-Shader] ✓ Initialization complete");
        } catch (Exception e) {
            System.err.println("[Anatorius-Shader] Init exception: " + e.getMessage());
            e.printStackTrace();
            cleanup();
            failedToInit = true;
        }
    }

    public void render(MatrixStack matrices, int screenWidth, int screenHeight, float delta) {
        if (failedToInit) return;
        if (!initialized) {
            init();
            if (!initialized) return;
        }

        try {
            time += delta * 0.02f;

            float scaleFactor = (float) mc.getWindow().getScaleFactor();
            float fbW = screenWidth * scaleFactor;
            float fbH = screenHeight * scaleFactor;

            int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL20.glUseProgram(programId);
            GL20.glUniform1f(timeLoc, time);
            GL20.glUniform2f(resolutionLoc, fbW, fbH);

            GL30.glBindVertexArray(vaoId);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            GL30.glBindVertexArray(prevVao);

            GL20.glUseProgram(prevProgram);

            if (prevDepth) GL11.glEnable(GL11.GL_DEPTH_TEST);
            else GL11.glDisable(GL11.GL_DEPTH_TEST);

            if (prevCull) GL11.glEnable(GL11.GL_CULL_FACE);
            else GL11.glDisable(GL11.GL_CULL_FACE);

            if (prevBlend) GL11.glEnable(GL11.GL_BLEND);
            else GL11.glDisable(GL11.GL_BLEND);
        } catch (Exception e) {
            System.err.println("[Anatorius-Shader] Render error: " + e.getMessage());
        }
    }

    private void cleanup() {
        if (vaoId != 0) { GL30.glDeleteVertexArrays(vaoId); vaoId = 0; }
        if (vboId != 0) { GL15.glDeleteBuffers(vboId); vboId = 0; }
        if (vertexShaderId != 0) { GL20.glDeleteShader(vertexShaderId); vertexShaderId = 0; }
        if (fragmentShaderId != 0) { GL20.glDeleteShader(fragmentShaderId); fragmentShaderId = 0; }
        if (programId != 0) { GL20.glDeleteProgram(programId); programId = 0; }
        initialized = false;
    }

    public void dispose() {
        cleanup();
        failedToInit = false;
    }
}

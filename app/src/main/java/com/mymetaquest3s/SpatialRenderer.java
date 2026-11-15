
package com.mymetaquest3s;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import com.google.ar.core.Anchor;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class SpatialRenderer implements GLSurfaceView.Renderer {

    private Session session;
    private int textureId;
    private int cameraProgram, panelProgram;
    private int width, height;
    private int displayRotation = 0;

    private final float[] view = new float[16];
    private final float[] projection = new float[16];
    private final float[] model = new float[16];
    private final float[] mvp = new float[16];

    private Anchor panelAnchor;
    private final AtomicReference<float[]> touch = new AtomicReference<>();

    private FloatBuffer cameraVertices;
    private FloatBuffer cameraUv;
    private FloatBuffer panelVertices;

    private final String cameraVS =
        "attribute vec4 p; attribute vec2 t;" +
        "varying vec2 v; void main(){" +
        "gl_Position=p; v=t;}";

    private final String cameraFS =
        "#extension GL_OES_EGL_image_external : require\n" +
        "precision mediump float;" +
        "uniform samplerExternalOES image;" +
        "varying vec2 v;" +
        "void main(){gl_FragColor=texture2D(image,v);}";

    private final String panelVS =
        "attribute vec4 p; uniform mat4 m;" +
        "void main(){gl_Position=m*p;}";

    private final String panelFS =
        "precision mediump float;" +
        "void main(){gl_FragColor=vec4(0.015,0.025,0.045,0.94);}";

    public SpatialRenderer(android.content.Context context) {
        cameraVertices = buffer(new float[]{
            -1,-1, 1,-1, -1,1, 1,1
        });

        cameraUv = buffer(new float[]{
            0,1, 1,1, 0,0, 1,0
        });

        panelVertices = buffer(new float[]{
            -1,0,-1, 1,0,-1,
            -1,0, 1, 1,0, 1
        });
    }

    private FloatBuffer buffer(float[] data) {
        FloatBuffer b = ByteBuffer
            .allocateDirect(data.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
        b.put(data);
        b.position(0);
        return b;
    }

    public void setSession(Session s) {
        session = s;
    }

    public void requestAnchor(float x, float y) {
        touch.set(new float[]{x, y});
    }

    public void clearAnchor() {
        if (panelAnchor != null) {
            panelAnchor.detach();
            panelAnchor = null;
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        cameraProgram = createProgram(cameraVS, cameraFS);
        panelProgram = createProgram(panelVS, panelFS);

        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        textureId = ids[0];

        GLES20.glBindTexture(0x8D65, textureId);
        GLES20.glTexParameteri(0x8D65,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(0x8D65,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        width = w;
        height = h;
        GLES20.glViewport(0, 0, w, h);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(
            GLES20.GL_COLOR_BUFFER_BIT |
            GLES20.GL_DEPTH_BUFFER_BIT
        );

        if (session == null) return;

        try {
            session.setCameraTextureName(textureId);
            Frame frame = session.update();

            if (width > 0 && height > 0) {
                session.setDisplayGeometry(
                    displayRotation, width, height
                );
            }

            drawCamera(frame);

            if (frame.getCamera().getTrackingState()
                != TrackingState.TRACKING) return;

            frame.getCamera().getViewMatrix(view, 0);
            frame.getCamera().getProjectionMatrix(
                projection, 0, 0.1f, 100f
            );

            processTouch(frame);

            if (panelAnchor != null &&
                panelAnchor.getTrackingState() == TrackingState.TRACKING) {

                panelAnchor.getPose().toMatrix(model, 0);

                Matrix.scaleM(model, 0, 1.30f, 1f, 0.525f);

                Matrix.multiplyMM(mvp, 0, view, 0, model, 0);
                Matrix.multiplyMM(mvp, 0, projection, 0, mvp, 0);

                drawPanel();
            }

        } catch (Exception ignored) {
        }
    }

    private void drawCamera(Frame frame) {
        FloatBuffer input = buffer(new float[]{
            -1,-1, 1,-1, -1,1, 1,1
        });
        FloatBuffer output = buffer(new float[8]);

        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            input,
            Coordinates2d.TEXTURE_NORMALIZED,
            output
        );

        output.position(0);

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(cameraProgram);

        int p = GLES20.glGetAttribLocation(cameraProgram, "p");
        int t = GLES20.glGetAttribLocation(cameraProgram, "t");

        cameraVertices.position(0);
        GLES20.glEnableVertexAttribArray(p);
        GLES20.glVertexAttribPointer(
            p, 2, GLES20.GL_FLOAT, false, 0, cameraVertices
        );

        GLES20.glEnableVertexAttribArray(t);
        GLES20.glVertexAttribPointer(
            t, 2, GLES20.GL_FLOAT, false, 0, output
        );

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(0x8D65, textureId);

        GLES20.glUniform1i(
            GLES20.glGetUniformLocation(cameraProgram, "image"), 0
        );

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(p);
        GLES20.glDisableVertexAttribArray(t);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private void processTouch(Frame frame) {
        float[] point = touch.getAndSet(null);
        if (point == null) return;

        try {
            for (HitResult hit : frame.hitTest(point[0], point[1])) {
                if (hit.getTrackable() instanceof Plane) {
                    Plane plane = (Plane) hit.getTrackable();

                    if (plane.getType() == Plane.Type.VERTICAL &&
                        plane.isPoseInPolygon(hit.getHitPose())) {

                        if (panelAnchor != null) panelAnchor.detach();
                        panelAnchor = hit.createAnchor();
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void drawPanel() {
        GLES20.glUseProgram(panelProgram);

        int p = GLES20.glGetAttribLocation(panelProgram, "p");
        int m = GLES20.glGetUniformLocation(panelProgram, "m");

        GLES20.glUniformMatrix4fv(m, 1, false, mvp, 0);

        panelVertices.position(0);
        GLES20.glEnableVertexAttribArray(p);
        GLES20.glVertexAttribPointer(
            p, 3, GLES20.GL_FLOAT, false, 0, panelVertices
        );

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(p);
    }

    private int createProgram(String vs, String fs) {
        int vertex = compile(GLES20.GL_VERTEX_SHADER, vs);
        int fragment = compile(GLES20.GL_FRAGMENT_SHADER, fs);

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);

        int[] result = new int[1];
        GLES20.glGetProgramiv(
            program, GLES20.GL_LINK_STATUS, result, 0
        );

        if (result[0] == 0) {
            throw new RuntimeException(
                GLES20.glGetProgramInfoLog(program)
            );
        }
        return program;
    }

    private int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] result = new int[1];
        GLES20.glGetShaderiv(
            shader, GLES20.GL_COMPILE_STATUS, result, 0
        );

        if (result[0] == 0) {
            throw new RuntimeException(
                GLES20.glGetShaderInfoLog(shader)
            );
        }
        return shader;
    }
}

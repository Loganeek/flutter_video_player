package io.flutter.plugins.videoplayer.spherical;

import static com.google.common.base.Preconditions.checkNotNull;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.AnyThread;
import androidx.annotation.BinderThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.media3.common.C;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

@UnstableApi
public final class VRView extends GLSurfaceView {

    private final Handler mainHandler;
    static final int FIELD_OF_VIEW_DEGREES = 90;
    static final float Z_NEAR = 0.1f;
    static final float Z_FAR = 100;

    // TODO Calculate this depending on surface size and field of view.
    private static final float PX_PER_DEGREES = 25;


    private final OrientationListener orientationListener;

    private boolean isOrientationListenerRegistered;

    @Nullable
    private SurfaceTexture surfaceTexture;

    @Nullable
    private Surface surface;


    private boolean isStarted;

    /* package */ static final float UPRIGHT_ROLL = (float) Math.PI;

    private SceneRenderer scene;

    private final SensorManager sensorManager;

    @Nullable
    private final Sensor orientationSensor;

    private boolean useSensorRotation;

    private final TouchTracker touchTracker;

    public interface SurfaceReadyCallback {
        void onSurfaceReady(Surface surface);
    }

    private SurfaceReadyCallback callback;

    @Override
    public void onResume() {
        super.onResume();
        isStarted = true;
        updateOrientationListenerRegistration();
    }

    @Override
    public void onPause() {
        isStarted = false;
        updateOrientationListenerRegistration();
        super.onPause();
    }

    private final Renderer renderer;

    @SuppressWarnings("deprecation")
    public VRView(Context context) {
        super(context);

        // Configure sensors and touch.
        sensorManager = (SensorManager) checkNotNull(context.getSystemService(Context.SENSOR_SERVICE));
        // TYPE_GAME_ROTATION_VECTOR is the easiest sensor since it handles all the complex math for
        // fusion. It's used instead of TYPE_ROTATION_VECTOR since the latter uses the magnetometer on
        // devices. When used indoors, the magnetometer can take some time to settle depending on the
        // device and amount of metal in the environment.
        @Nullable
        Sensor orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (orientationSensor == null) {
            orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        this.orientationSensor = orientationSensor;

        mainHandler = new Handler(Looper.getMainLooper());
        scene = new SceneRenderer();
        renderer = new Renderer(scene);
        touchTracker = new TouchTracker(context, renderer, PX_PER_DEGREES);
        Display display = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30) 及以上建议从 Context 获取
            display = context.getDisplay();
        }

        // 确保 display 不为空
        if (display == null) {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            // 降级方案：使用 WindowManager 获取
            display = windowManager.getDefaultDisplay();
        }
        orientationListener = new OrientationListener(display, touchTracker, renderer);
        useSensorRotation = true;

        setEGLContextClientVersion(2);
        setRenderer(renderer);
        setOnTouchListener(touchTracker);
    }

    /**
     * Sets the default stereo mode. If the played video doesn't contain a stereo mode the default one
     * is used.
     *
     * @param stereoMode A {@link C.StereoMode} value.
     */
    public void setDefaultStereoMode(@C.StereoMode int stereoMode) {
        scene.setDefaultStereoMode(stereoMode);
    }

    /**
     * Sets whether to use the orientation sensor for rotation (if available).
     */
    public void setUseSensorRotation(boolean useSensorRotation) {
        this.useSensorRotation = useSensorRotation;
        updateOrientationListenerRegistration();
    }

    public void setSurfaceReadyCallback(SurfaceReadyCallback cb) {
        this.callback = cb;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        onResume();
    }

    @Override
    protected void onDetachedFromWindow() {
        // This call stops GL thread.
        super.onDetachedFromWindow();

        // Post to make sure we occur in order with any onSurfaceTextureAvailable calls.
        mainHandler.post(
                () -> {
                    surfaceTexture = null;
                    surface = null;
                });
    }

    /**
     * Returns the {@link VideoFrameMetadataListener} that should be registered during playback.
     */
    public VideoFrameMetadataListener getVideoFrameMetadataListener() {
        return scene;
    }

    private void updateOrientationListenerRegistration() {
        boolean enabled = useSensorRotation && isStarted;
        if (orientationSensor == null || enabled == isOrientationListenerRegistered) {
            return;
        }
        if (enabled) {
            sensorManager.registerListener(
                    orientationListener, orientationSensor, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            sensorManager.unregisterListener(orientationListener);
        }
        isOrientationListenerRegistered = enabled;
    }

    /**
     * Standard GL Renderer implementation. The notable code is the matrix multiplication in
     * onDrawFrame and updatePitchMatrix.
     */
    final class Renderer
            implements GLSurfaceView.Renderer, TouchTracker.Listener, OrientationListener.Listener {
        private final SceneRenderer scene;
        private final float[] projectionMatrix = new float[16];

        // There is no model matrix for this scene so viewProjectionMatrix is used for the mvpMatrix.
        private final float[] viewProjectionMatrix = new float[16];

        // Device orientation is derived from sensor data. This is accessed in the sensor's thread and
        // the GL thread.
        private final float[] deviceOrientationMatrix = new float[16];

        // Optional pitch and yaw rotations are applied to the sensor orientation. These are accessed on
        // the UI, sensor and GL Threads.
        private final float[] touchPitchMatrix = new float[16];
        private final float[] touchYawMatrix = new float[16];

        // viewMatrix = touchPitch * deviceOrientation * touchYaw.
        private final float[] viewMatrix = new float[16];
        private final float[] tempMatrix = new float[16];
        private float touchPitch;
        private float deviceRoll;

        public Renderer(SceneRenderer scene) {
            this.scene = scene;
            GlUtil.setToIdentity(deviceOrientationMatrix);
            GlUtil.setToIdentity(touchPitchMatrix);
            GlUtil.setToIdentity(touchYawMatrix);
            deviceRoll = UPRIGHT_ROLL;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            surfaceTexture = scene.init();

            surface = new Surface(surfaceTexture);

            mainHandler.post(() -> {
                if (callback != null && surface != null) {
                    callback.onSurfaceReady(surface);
                }
            });
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            float aspect = (float) width / height;
            float fovY = calculateFieldOfViewInYDirection(aspect);
            Matrix.perspectiveM(projectionMatrix, 0, fovY, aspect, Z_NEAR, Z_FAR);
        }


        @Override
        public void onDrawFrame(GL10 gl) {
            // Combine touch & sensor data.
            // Orientation = pitch * sensor * yaw since that is closest to what most users expect the
            // behavior to be.
            synchronized (this) {
                Matrix.multiplyMM(tempMatrix, 0, deviceOrientationMatrix, 0, touchYawMatrix, 0);
                Matrix.multiplyMM(viewMatrix, 0, touchPitchMatrix, 0, tempMatrix, 0);
            }

            Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            scene.drawFrame(viewProjectionMatrix, /* rightEye= */ false);
        }

        private float calculateFieldOfViewInYDirection(float aspect) {
            boolean landscapeMode = aspect > 1;
            if (landscapeMode) {
                double halfFovX = FIELD_OF_VIEW_DEGREES / 2f;
                double tanY = Math.tan(Math.toRadians(halfFovX)) / aspect;
                double halfFovY = Math.toDegrees(Math.atan(tanY));
                return (float) (halfFovY * 2);
            } else {
                return FIELD_OF_VIEW_DEGREES;
            }
        }

        /**
         * Adjusts the GL camera's rotation based on device rotation. Runs on the sensor thread.
         */
        @Override
        @BinderThread
        public synchronized void onOrientationChange(float[] matrix, float deviceRoll) {
            System.arraycopy(matrix, 0, deviceOrientationMatrix, 0, deviceOrientationMatrix.length);
            this.deviceRoll = -deviceRoll;
            updatePitchMatrix();
        }

        /**
         * Updates the pitch matrix after a physical rotation or touch input. The pitch matrix rotation
         * is applied on an axis that is dependent on device rotation so this must be called after
         * either touch or sensor update.
         */
        @AnyThread
        private void updatePitchMatrix() {
            // The camera's pitch needs to be rotated along an axis that is parallel to the real world's
            // horizon. This is the <1, 0, 0> axis after compensating for the device's roll.
            Matrix.setRotateM(
                    touchPitchMatrix,
                    0,
                    -touchPitch,
                    (float) Math.cos(deviceRoll),
                    (float) Math.sin(deviceRoll),
                    0);
        }


        @Override
        @UiThread
        public synchronized void onScrollChange(PointF scrollOffsetDegrees) {
            touchPitch = scrollOffsetDegrees.y;
            updatePitchMatrix();
            Matrix.setRotateM(touchYawMatrix, 0, -scrollOffsetDegrees.x, 0, 1, 0);
        }


        @Override
        @UiThread
        public boolean onSingleTapUp(MotionEvent event) {
            return performClick();
        }
    }

    class OldRenderer implements GLSurfaceView.Renderer {

        SurfaceTexture surfaceTexture;
        Surface surface;
        int textureId;

        int program;
        int aPos;
        int aTex;
        int uTex;

        FloatBuffer vBuf;
        FloatBuffer tBuf;

        float[] VERT = {
                -1f, -1f,
                1f, -1f,
                -1f, 1f,
                1f, 1f
        };

        float[] TEX = {
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
        };

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {

            textureId = createOESTexture();

            surfaceTexture = new SurfaceTexture(textureId);
            surface = new Surface(surfaceTexture);

            post(() -> {
                if (callback != null && surface != null) {
                    callback.onSurfaceReady(surface);
                }
            });

            program = createProgram(VS, FS);

            aPos = GLES20.glGetAttribLocation(program, "aPosition");
            aTex = GLES20.glGetAttribLocation(program, "aTexCoord");
            uTex = GLES20.glGetUniformLocation(program, "uTexture");

            GLES20.glUseProgram(program);
            GLES20.glUniform1i(uTex, 0); // ⭐关键

            vBuf = ByteBuffer.allocateDirect(VERT.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vBuf.put(VERT).position(0);

            tBuf = ByteBuffer.allocateDirect(TEX.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            tBuf.put(TEX).position(0);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int w, int h) {
            GLES20.glViewport(0, 0, w, h);
        }

        @Override
        public void onDrawFrame(GL10 gl) {

            if (surfaceTexture == null) return;

            surfaceTexture.updateTexImage();

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);

            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vBuf);

            GLES20.glEnableVertexAttribArray(aTex);
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, tBuf);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        private int createOESTexture() {
            int[] t = new int[1];
            GLES20.glGenTextures(1, t, 0);

            int id = t[0];

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

            return id;
        }

        private int createProgram(String vs, String fs) {
            int v = load(GLES20.GL_VERTEX_SHADER, vs);
            int f = load(GLES20.GL_FRAGMENT_SHADER, fs);

            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, v);
            GLES20.glAttachShader(p, f);
            GLES20.glLinkProgram(p);

            return p;
        }

        private int load(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            return s;
        }

        static final String VS =
                "attribute vec4 aPosition;\n" +
                        "attribute vec2 aTexCoord;\n" +
                        "varying vec2 vTex;\n" +
                        "void main() {\n" +
                        "  gl_Position = aPosition;\n" +
                        "  vTex = aTexCoord;\n" +
                        "}";

        static final String FS =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTex;\n" +
                        "uniform samplerExternalOES uTexture;\n" +

                        "void main() {\n" +

                        "  // 把 0~1 转成 -1~1\n" +
                        "  vec2 p = vTex * 2.0 - 1.0;\n" +

                        "  float r = length(p);\n" +

                        "  // 180°球面映射（简单版）\n" +
                        "  float theta = atan(p.y, p.x);\n" +
                        "  float phi = r * 1.57079632679; // PI/2\n" +

                        "  vec2 uv;\n" +
                        "  uv.x = (theta + 3.1415926) / (2.0 * 3.1415926);\n" +
                        "  uv.y = phi / 1.57079632679;\n" +

                        "  gl_FragColor = texture2D(uTexture, uv);\n" +
                        "}";
    }
}
package io.flutter.plugins.videoplayer.spherical;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.opengl.Matrix;
import android.view.Surface;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Extra yaw applied <em>before</em> {@link FrameRotationQueue#computeRecenterMatrix} is absorbed
 * into the yaw baseline and cancelled. Portrait letterbox compensation must run <em>after</em>
 * recenter or it is a no-op — which matches the inline-portrait 90° yaw bug.
 */
@RunWith(RobolectricTestRunner.class)
public final class RecenterYawOrderTest {
  private static final float EPS = 1e-4f;

  @Test
  public void yawBeforeRecenter_isCancelled() {
    float[] matrix = identity();
    Matrix.rotateM(matrix, 0, -90, 0, 1, 0);

    float[] recenter = new float[16];
    FrameRotationQueue.computeRecenterMatrix(recenter, matrix);

    float[] result = new float[16];
    Matrix.multiplyMM(result, 0, matrix, 0, recenter, 0);

    assertEquals(0f, yawDegrees(result), 1f);
  }

  @Test
  public void yawAfterRecenter_survives() {
    float[] matrix = identity();
    float[] recenter = new float[16];
    FrameRotationQueue.computeRecenterMatrix(recenter, matrix);

    float[] result = new float[16];
    Matrix.multiplyMM(result, 0, matrix, 0, recenter, 0);
    Matrix.rotateM(result, 0, -90, 0, 1, 0);

    assertEquals(-90f, yawDegrees(result), 1f);
  }

  @Test
  public void letterboxPortraitNeedsYaw_onlyWhenDisplayPortraitAndSurfaceWide() {
    assertTrue(
        OrientationListener.needsLetterboxedPortraitYaw(Surface.ROTATION_0, 1920, 1080));
    assertTrue(
        OrientationListener.needsLetterboxedPortraitYaw(Surface.ROTATION_180, 1600, 900));
    assertFalse(
        OrientationListener.needsLetterboxedPortraitYaw(Surface.ROTATION_0, 1080, 1920));
    assertFalse(
        OrientationListener.needsLetterboxedPortraitYaw(Surface.ROTATION_90, 1920, 1080));
    assertFalse(
        OrientationListener.needsLetterboxedPortraitYaw(Surface.ROTATION_0, 0, 1080));
  }

  private static float[] identity() {
    float[] m = new float[16];
    Matrix.setIdentityM(m, 0);
    return m;
  }

  /** Yaw around Y from the OpenGL -Z look vector (column-major m[8], m[10]). */
  private static float yawDegrees(float[] m) {
    return (float) Math.toDegrees(Math.atan2(m[8], m[10]));
  }
}

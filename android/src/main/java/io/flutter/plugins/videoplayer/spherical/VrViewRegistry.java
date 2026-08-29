package io.flutter.plugins.videoplayer.spherical;

import androidx.annotation.Nullable;

/** Maps Flutter playerId → attached {@link VRView} stack for runtime VR controls. */
public final class VrViewRegistry {
  private static final PlayerIdViewStack<VRView> STACK = new PlayerIdViewStack<>();

  private VrViewRegistry() {}

  public static void register(long playerId, VRView view) {
    STACK.push(playerId, view);
  }

  /** Removes one platform view; remaining top (if any) is the next output target. */
  public static void unregister(long playerId, VRView view) {
    STACK.remove(playerId, view);
  }

  public static void unregister(long playerId) {
    STACK.clear(playerId);
  }

  @Nullable
  public static VRView get(long playerId) {
    return STACK.peek(playerId);
  }
}

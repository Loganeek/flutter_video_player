package io.flutter.plugins.videoplayer.spherical;

import androidx.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

/** Maps Flutter playerId → attached {@link VRView} for runtime VR controls. */
public final class VrViewRegistry {
  private static final ConcurrentHashMap<Long, WeakReference<VRView>> VIEWS =
      new ConcurrentHashMap<>();

  private VrViewRegistry() {}

  public static void register(long playerId, VRView view) {
    VIEWS.put(playerId, new WeakReference<>(view));
  }

  public static void unregister(long playerId) {
    VIEWS.remove(playerId);
  }

  @Nullable
  public static VRView get(long playerId) {
    WeakReference<VRView> ref = VIEWS.get(playerId);
    if (ref == null) {
      return null;
    }
    VRView view = ref.get();
    if (view == null) {
      VIEWS.remove(playerId);
    }
    return view;
  }
}

package io.flutter.plugins.videoplayer.spherical;

import androidx.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-player stack of attached views. Chewie fullscreen mounts a second VR Platform View on the
 * same player; {@link #peek} is the top (visible) view. Removing the top restores the previous.
 */
/* package */ final class PlayerIdViewStack<T> {
  private final ConcurrentHashMap<Long, CopyOnWriteArrayList<WeakReference<T>>> stacks =
      new ConcurrentHashMap<>();

  void push(long playerId, T view) {
    stacks
        .computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>())
        .add(new WeakReference<>(view));
  }

  void remove(long playerId, T view) {
    CopyOnWriteArrayList<WeakReference<T>> stack = stacks.get(playerId);
    if (stack == null) {
      return;
    }
    for (Iterator<WeakReference<T>> it = stack.iterator(); it.hasNext(); ) {
      WeakReference<T> ref = it.next();
      T item = ref.get();
      if (item == null || item == view) {
        stack.remove(ref);
      }
    }
    if (stack.isEmpty()) {
      stacks.remove(playerId);
    }
  }

  void clear(long playerId) {
    stacks.remove(playerId);
  }

  @Nullable
  T peek(long playerId) {
    CopyOnWriteArrayList<WeakReference<T>> stack = stacks.get(playerId);
    if (stack == null) {
      return null;
    }
    for (int i = stack.size() - 1; i >= 0; i--) {
      T item = stack.get(i).get();
      if (item != null) {
        return item;
      }
    }
    return null;
  }
}

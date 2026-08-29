package io.flutter.plugins.videoplayer.spherical;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Chewie fullscreen pushes a second VR view on the same playerId. Popping the top must restore the
 * inline view as {@link PlayerIdViewStack#peek}.
 */
@RunWith(RobolectricTestRunner.class)
public final class PlayerIdViewStackTest {
  @Test
  public void peekIsTop_removeTopRestoresPrevious() {
    PlayerIdViewStack<Object> stack = new PlayerIdViewStack<>();
    Object inline = new Object();
    Object fullscreen = new Object();

    stack.push(1L, inline);
    stack.push(1L, fullscreen);

    assertSame(fullscreen, stack.peek(1L));

    stack.remove(1L, fullscreen);

    assertSame(inline, stack.peek(1L));
  }

  @Test
  public void removeOnlyView_peekNull() {
    PlayerIdViewStack<Object> stack = new PlayerIdViewStack<>();
    Object only = new Object();
    stack.push(7L, only);
    stack.remove(7L, only);
    assertNull(stack.peek(7L));
  }

  @Test
  public void clearDropsWholePlayer() {
    PlayerIdViewStack<Object> stack = new PlayerIdViewStack<>();
    stack.push(3L, new Object());
    stack.push(3L, new Object());
    stack.clear(3L);
    assertNull(stack.peek(3L));
  }
}

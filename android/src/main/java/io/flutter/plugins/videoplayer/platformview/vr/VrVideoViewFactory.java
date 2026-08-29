// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer.platformview.vr;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.exoplayer.ExoPlayer;
import io.flutter.plugin.platform.PlatformView;
import io.flutter.plugins.videoplayer.PlatformVideoViewCreationParams;
import io.flutter.plugins.videoplayer.VideoPlayer;
import io.flutter.plugins.videoplayer.platformview.PlatformVideoViewFactory;
import io.flutter.plugins.videoplayer.spherical.VRView;
import io.flutter.plugins.videoplayer.spherical.VrViewRegistry;
import java.util.Objects;

/**
 * A factory class responsible for creating platform video views that can be embedded in a Flutter
 * app.
 */
public class VrVideoViewFactory extends PlatformVideoViewFactory {
  private final PlatformVideoViewFactory.VideoPlayerProvider videoPlayerProvider;

  public VrVideoViewFactory(
      @NonNull PlatformVideoViewFactory.VideoPlayerProvider videoPlayerProvider) {
    super(videoPlayerProvider);
    this.videoPlayerProvider = videoPlayerProvider;
  }

  @NonNull
  @Override
  public PlatformView create(@NonNull Context context, int id, @Nullable Object args) {
    final PlatformVideoViewCreationParams params =
        Objects.requireNonNull((PlatformVideoViewCreationParams) args);
    final Long playerId = params.getPlayerId();
    final VideoPlayer player = videoPlayerProvider.getVideoPlayer(playerId);
    final ExoPlayer exoPlayer = player.getExoPlayer();
    final VrVideoView view = VrVideoView.createVrView(context, exoPlayer);
    VrViewRegistry.register(playerId, view.getVrSurface());
    return view;
  }

  @Override
  public PlatformView buildPlatformView(@NonNull Context context, @NonNull ExoPlayer exoPlayer) {
    return VrVideoView.createVrView(context, exoPlayer);
  }
}

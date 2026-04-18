// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer.platformview.vr;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.media3.exoplayer.ExoPlayer;

import io.flutter.plugin.platform.PlatformView;
import io.flutter.plugin.platform.PlatformViewFactory;
import io.flutter.plugins.videoplayer.AndroidVideoPlayerApi;
import io.flutter.plugins.videoplayer.PlatformVideoViewCreationParams;
import io.flutter.plugins.videoplayer.platformview.PlatformVideoViewFactory;

/**
 * A factory class responsible for creating platform video views that can be embedded in a Flutter
 * app.
 */
public class VrVideoViewFactory extends PlatformVideoViewFactory {
    public VrVideoViewFactory(@NonNull VideoPlayerProvider videoPlayerProvider) {
        super(videoPlayerProvider);
    }

    @Override
    public PlatformView buildPlatformView(@NonNull Context context, @NonNull ExoPlayer exoPlayer) {
        return VrVideoView.createVrView(context, exoPlayer);
    }
}

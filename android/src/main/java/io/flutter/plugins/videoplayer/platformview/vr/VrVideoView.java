// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer.platformview.vr;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import io.flutter.plugins.videoplayer.platformview.PlatformVideoView;
import io.flutter.plugins.videoplayer.spherical.VRView;

/**
 * A class used to create a native video view that can be embedded in a Flutter app. It wraps an
 * {@link ExoPlayer} instance and displays its video content.
 */
@UnstableApi
public final class VrVideoView extends PlatformVideoView<VRView> {
    private VrVideoView(@NonNull VRView vrView) {
        super(vrView);
    }
    public static VrVideoView createVrView(@NonNull Context context, @NonNull ExoPlayer exoPlayer) {
        var view = new VRView(context);
        view.setDefaultStereoMode(C.STEREO_MODE_LEFT_RIGHT);
        setupSurfaceWithCallback(view, exoPlayer);
        return new VrVideoView(view);
    }


    private static void setupSurfaceWithCallback(VRView view, @NonNull ExoPlayer exoPlayer) {
        exoPlayer.setVideoFrameMetadataListener(view.getVideoFrameMetadataListener());
        view.setSurfaceReadyCallback(exoPlayer::setVideoSurface);
    }

    /**
     * Disposes of the resources used by this PlatformView.
     */
    @Override
    public void dispose() {
        getSurfaceView().onPause();
        super.dispose();
    }
}

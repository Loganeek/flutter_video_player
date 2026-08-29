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
import io.flutter.plugins.videoplayer.spherical.VrViewRegistry;

/**
 * A class used to create a native video view that can be embedded in a Flutter app. It wraps an
 * {@link ExoPlayer} instance and displays its video content.
 */
@UnstableApi
public final class VrVideoView extends PlatformVideoView<VRView> {
    private final long playerId;
    @NonNull private final ExoPlayer exoPlayer;

    private VrVideoView(@NonNull VRView vrView, long playerId, @NonNull ExoPlayer exoPlayer) {
        super(vrView);
        this.playerId = playerId;
        this.exoPlayer = exoPlayer;
    }

    /** Exposed for registry registration from the view factory. */
    @NonNull
    public VRView getVrSurface() {
        return getSurfaceView();
    }

    public static VrVideoView createVrView(
            @NonNull Context context, @NonNull ExoPlayer exoPlayer, long playerId) {
        var view = new VRView(context);
        view.setDefaultStereoMode(C.STEREO_MODE_LEFT_RIGHT);
        setupSurfaceWithCallback(view, exoPlayer);
        return new VrVideoView(view, playerId, exoPlayer);
    }

    public static VrVideoView createVrView(@NonNull Context context, @NonNull ExoPlayer exoPlayer) {
        return createVrView(context, exoPlayer, /* playerId= */ -1L);
    }

    private static void setupSurfaceWithCallback(VRView view, @NonNull ExoPlayer exoPlayer) {
        exoPlayer.setVideoFrameMetadataListener(view.getVideoFrameMetadataListener());
        view.setSurfaceReadyCallback(exoPlayer::setVideoSurface);
    }

    /**
     * Disposes of the resources used by this PlatformView.
     *
     * <p>Chewie fullscreen mounts a second view on the same player. Rebind ExoPlayer to the
     * remaining view so inline VR keeps receiving frames.
     */
    @Override
    public void dispose() {
        VRView self = getVrSurface();
        self.setSurfaceReadyCallback(null);
        if (playerId >= 0) {
            VrViewRegistry.unregister(playerId, self);
        }
        VRView remaining = playerId >= 0 ? VrViewRegistry.get(playerId) : null;
        if (remaining != null) {
            exoPlayer.setVideoFrameMetadataListener(remaining.getVideoFrameMetadataListener());
            remaining.offerExistingSurface();
        } else {
            exoPlayer.setVideoSurface(null);
            exoPlayer.setVideoFrameMetadataListener(null);
        }
        getSurfaceView().onPause();
        super.dispose();
    }
}

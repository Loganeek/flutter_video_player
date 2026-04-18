// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer.platformview;

import android.content.Context;
import android.os.Build;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import io.flutter.plugin.platform.PlatformView;

/**
 * A class used to create a native video view that can be embedded in a Flutter app. It wraps an
 * {@link ExoPlayer} instance and displays its video content.
 */
@UnstableApi
public class PlatformVideoView<T extends SurfaceView> implements PlatformView {
    @NonNull
    private final T surfaceView;

    @NonNull
    protected T getSurfaceView(){
        return surfaceView;
    }


    protected PlatformVideoView(@NonNull T surfaceView) {
        this.surfaceView = surfaceView;
    }

    /**
     * Constructs a new PlatformVideoView.
     *
     * @param context   The context in which the view is running.
     * @param exoPlayer The ExoPlayer instance used to play the video.
     */
    public static PlatformVideoView<SurfaceView> create(@NonNull Context context, @NonNull ExoPlayer exoPlayer) {
        SurfaceView view = new SurfaceView(context);
        configureStandardPlayer(view, context, exoPlayer);
        return new PlatformVideoView<>(view);
    }

    protected static void configureStandardPlayer(@NonNull SurfaceView view, @NonNull Context context, @NonNull ExoPlayer exoPlayer) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            // Workaround for rendering issues on Android 9 (API 28).
            // On Android 9, using setVideoSurfaceView seems to lead to issues where the first frame is
            // not displayed if the video is paused initially.
            // To ensure the first frame is visible, the surface is directly set using holder.getSurface()
            // when the surface is created, and ExoPlayer seeks to a position to force rendering of the
            // first frame.
            setupSurfaceWithCallback(view, exoPlayer);
        } else {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                // Avoid blank space instead of a video on Android versions below 8 by adjusting video's
                // z-layer within the Android view hierarchy:
                view.setZOrderMediaOverlay(true);
            }
            exoPlayer.setVideoSurfaceView(view);
        }

    }

    private static void setupSurfaceWithCallback(@NonNull SurfaceView view, @NonNull ExoPlayer exoPlayer) {
        view
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(@NonNull SurfaceHolder holder) {

                                System.out.println("surfaceCreated .... ");
                                exoPlayer.setVideoSurface(holder.getSurface());
                                // Force first frame rendering:
                                exoPlayer.seekTo(1);
                            }

                            @Override
                            public void surfaceChanged(
                                    @NonNull SurfaceHolder holder, int format, int width, int height) {
                                // No implementation needed.

                                System.out.println("surface CHANGED......");
                            }

                            @Override
                            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                                exoPlayer.setVideoSurface(null);
                            }
                        });
    }

    /**
     * Returns the view associated with this PlatformView.
     *
     * @return The SurfaceView used to display the video.
     */
    @NonNull
    @Override
    public View getView() {
        return surfaceView;
    }

    /** Disposes of the resources used by this PlatformView. */
    @Override
    public void dispose() {
        surfaceView.getHolder().getSurface().release();
    }
}

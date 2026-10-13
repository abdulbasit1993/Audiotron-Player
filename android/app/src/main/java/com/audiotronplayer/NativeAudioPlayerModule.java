package com.audiotronplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import android.support.v4.media.session.PlaybackStateCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

@ReactModule(name = NativeAudioPlayerModule.NAME)
public class NativeAudioPlayerModule extends NativeAudioPlayerSpec {

    public static final String NAME = "NativeAudioPlayer";

    private static final String EVENT_PLAYBACK_STATE_CHANGE =
            "playbackStateChange";

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private BackgroundAudioService audioService;
    private boolean serviceBound = false;
    private boolean released = true;

    private Integer loopA = null;
    private Integer loopB = null;

    private String lastEmittedState = null;
    private int lastEmittedPosition = -1;

    private Promise pendingLoadPromise;

    public NativeAudioPlayerModule(
            ReactApplicationContext context) {
        super(context);

        Intent intent = new Intent(
                context,
                BackgroundAudioService.class
        );

        /*
         * Start the service, then bind to it.
         * BackgroundAudioService returns its LocalBinder from onBind().
         */
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }

            context.bindService(
                    intent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
            );
        } catch (Exception e) {
            android.util.Log.e(
                    NAME,
                    "Unable to start or bind audio service",
                    e
            );
        }
    }

    private final ServiceConnection serviceConnection =
            new ServiceConnection() {

        @Override
        public void onServiceConnected(
                ComponentName name,
                IBinder binder) {

            BackgroundAudioService.LocalBinder localBinder =
                    (BackgroundAudioService.LocalBinder) binder;

            audioService = localBinder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            audioService = null;
            serviceBound = false;
        }
    };

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void addListener(String eventName) {
        // Required by the generated React Native event-emitter spec.
    }

    @Override
    public void removeListeners(double count) {
        // Required by the generated React Native event-emitter spec.
    }

    /*
     * Checks whether the service is connected before
     * accessing the player.
     */
    private boolean requireService(Promise promise) {
        if (!serviceBound || audioService == null) {
            promise.reject(
                    "SERVICE_NOT_READY",
                    "The audio service is not connected yet. Try again."
            );
            return false;
        }

        return true;
    }

    /*
     * Emits playback information to React Native.
     */
    private void emitPlaybackStateEvent(String explicitState) {
        emitPlaybackStateEvent(explicitState, null);
    }

    private void emitPlaybackStateEvent(
            String explicitState,
            String errorMessage) {

        if (!serviceBound || audioService == null) {
            return;
        }

        try {
            int position = audioService.getCurrentPosition();
            int duration = audioService.getCurrentDuration();

            String state = explicitState;

            if (state == null) {
                if (audioService.isPlaying()) {
                    state = "playing";
                } else {
                    int serviceState = audioService.getPlaybackState();
                    if (serviceState == PlaybackStateCompat.STATE_PLAYING) {
                        state = "playing";
                    } else if (serviceState == PlaybackStateCompat.STATE_BUFFERING) {
                        state = "buffering";
                    } else if (serviceState == PlaybackStateCompat.STATE_STOPPED) {
                        state = "stopped";
                    } else {
                        state = "paused";
                    }
                }
            }

            boolean stateChanged =
                    !state.equals(lastEmittedState);

            boolean positionChanged =
                    Math.abs(position - lastEmittedPosition) >= 200;

            if (!stateChanged && !positionChanged && errorMessage == null) {
                return;
            }

            WritableMap event = Arguments.createMap();

            event.putInt("position", position);
            event.putInt("duration", duration);
            event.putString("state", state);

            if (errorMessage != null) {
                event.putString("error", errorMessage);
            }

            getReactApplicationContext()
                    .getJSModule(
                            DeviceEventManagerModule
                                    .RCTDeviceEventEmitter.class
                    )
                    .emit(EVENT_PLAYBACK_STATE_CHANGE, event);

            lastEmittedState = state;
            lastEmittedPosition = position;

        } catch (Exception e) {
            android.util.Log.w(
                    NAME,
                    "Unable to emit playback state",
                    e
            );
        }
    }

    /*
     * Periodically updates the seek bar position while playing.
     */
    private final Runnable playbackChecker = new Runnable() {
        @Override
        public void run() {
            if (!serviceBound || audioService == null || released) {
                return;
            }

            try {
                emitPlaybackStateEvent(null);
            } catch (Exception e) {
                android.util.Log.w(
                        NAME,
                        "Playback polling failed",
                        e
                );
            }

            handler.postDelayed(this, 200);
        }
    };

    @Override
    public void load(String uri, Promise promise) {
        if (!requireService(promise)) {
            return;
        }

        try {
            handler.removeCallbacks(playbackChecker);

            loopA = null;
            loopB = null;
            released = false;

            lastEmittedState = null;
            lastEmittedPosition = -1;

            audioService.load(uri, new BackgroundAudioService.LoadCallback() {
                @Override
                public void onSuccess(int duration) {
                    handler.post(playbackChecker);
                    emitPlaybackStateEvent("playing");
                    promise.resolve(null);
                }

                @Override
                public void onError(String message) {
                    released = true;
                    emitPlaybackStateEvent("error", message);
                    promise.reject("AUDIO_LOAD_ERROR", message);
                }
            });

        } catch (Exception e) {
            released = true;

            emitPlaybackStateEvent(
                    "error",
                    "Unable to load the selected audio file."
            );

            promise.reject(
                    "AUDIO_LOAD_ERROR",
                    "Unable to load the selected audio file.",
                    e
            );
        }
    }

    @Override
    public void play(Promise promise) {
        if (!requireService(promise)) {
            return;
        }

        try {
            audioService.play();
            released = false;

            handler.removeCallbacks(playbackChecker);
            handler.post(playbackChecker);

            emitPlaybackStateEvent("playing");
            promise.resolve(null);

        } catch (Exception e) {
            promise.reject("PLAY_ERROR", e);
        }
    }

    @Override
    public void pause(Promise promise) {
        if (!requireService(promise)) {
            return;
        }

        try {
            audioService.pause();

            emitPlaybackStateEvent("paused");
            promise.resolve(null);

        } catch (Exception e) {
            promise.reject("PAUSE_ERROR", e);
        }
    }

    @Override
    public void stop(Promise promise) {
        if (!requireService(promise)) {
            return;
        }

        try {
            audioService.stop();

            loopA = null;
            loopB = null;

            lastEmittedPosition = -1;
            emitPlaybackStateEvent("stopped");

            promise.resolve(null);

        } catch (Exception e) {
            promise.reject("STOP_ERROR", e);
        }
    }

    @Override
    public void getDuration(Promise promise) {
        if (!requireService(promise)) {
            return;
        }

        try {
            promise.resolve(audioService.getCurrentDuration());

        } catch (Exception e) {
            promise.reject("GET_DURATION_ERROR", e);
        }
    }

    @Override
    public void getCurrentPosition(Promise promise) {
        if (!requireService(promise)) {
            return;
        }

        try {
            promise.resolve(audioService.getCurrentPosition());

        } catch (Exception e) {
            promise.reject("GET_POSITION_ERROR", e);
        }
    }

    @Override
    public void seekTo(double positionMs, Promise promise) {
        if (!requireService(promise)) {
            return;
        }

        try {
            int duration = audioService.getCurrentDuration();

            int targetPosition = (int) Math.max(
                    0,
                    Math.min(Math.round(positionMs), duration)
            );

            audioService.seekTo(targetPosition, mp -> {
                lastEmittedPosition = targetPosition;
                emitPlaybackStateEvent(null);
                promise.resolve(null);
            });

        } catch (Exception e) {
            promise.reject("SEEK_ERROR", e);
        }
    }

    @Override
    public void setLoopA(Promise promise) {
        if (!requireService(promise)) {
            return;
        }

        try {
            int position = audioService.setLoopA();
            if (position < 0) {
                promise.reject("LOOP_A_ERROR", "Unable to set loop marker A");
                return;
            }

            loopA = position;
            loopB = null;

            promise.resolve(position);

        } catch (Exception e) {
            promise.reject("LOOP_A_ERROR", e);
        }
    }

    @Override
    public void setLoopB(Promise promise) {
        if (!requireService(promise)) {
            return;
        }

        try {
            if (audioService.getLoopA() < 0) {
                promise.reject(
                        "LOOP_A_NOT_SET",
                        "Set marker A before setting marker B."
                );
                return;
            }

            int position = audioService.setLoopB();
            if (position < 0) {
                promise.reject(
                        "INVALID_LOOP_RANGE",
                        "Marker B must be after marker A."
                );
                return;
            }

            loopB = position;
            promise.resolve(position);

        } catch (Exception e) {
            promise.reject("LOOP_B_ERROR", e);
        }
    }

    @Override
    public void clearLoop(Promise promise) {
        if (serviceBound && audioService != null) {
            audioService.clearLoop();
        }
        loopA = null;
        loopB = null;

        promise.resolve(null);
    }

    @Override
    public void release(Promise promise) {
        handler.removeCallbacks(playbackChecker);

        loopA = null;
        loopB = null;
        released = true;

        try {
            if (serviceBound && audioService != null) {
                audioService.release();
            }

            lastEmittedState = null;
            lastEmittedPosition = -1;

            promise.resolve(null);

        } catch (Exception e) {
            promise.reject("RELEASE_ERROR", e);
        }
    }

    @Override
    public void invalidate() {
        handler.removeCallbacks(playbackChecker);

        if (pendingLoadPromise != null) {
            pendingLoadPromise.reject(
                    "MODULE_INVALIDATED",
                    "Audio module was invalidated."
            );
            pendingLoadPromise = null;
        }

        if (serviceBound) {
            try {
                getReactApplicationContext().unbindService(
                        serviceConnection
                );
            } catch (IllegalArgumentException ignored) {
                // Already unbound.
            }
        }

        serviceBound = false;
        audioService = null;

        super.invalidate();
    }
}

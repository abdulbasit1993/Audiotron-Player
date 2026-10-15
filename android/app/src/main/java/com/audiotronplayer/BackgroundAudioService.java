package com.audiotronplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.io.IOException;
import java.net.URLDecoder;

public class BackgroundAudioService extends Service {

    private static final String TAG = "BackgroundAudioService";

    private static final String CHANNEL_ID = "audiotron_player_channel";
    private static final String CHANNEL_NAME = "Audiotron Player";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_PLAY =
            "com.audiotronplayer.PLAY";
    public static final String ACTION_PAUSE =
            "com.audiotronplayer.PAUSE";
    public static final String ACTION_STOP =
            "com.audiotronplayer.STOP";
    public static final String ACTION_SEEK_TO =
            "com.audiotronplayer.SEEK_TO";

    public interface LoadCallback {
        void onSuccess(int duration);
        void onError(String message);
    }

    private static class TrackInfo {
        final String title;
        final String artist;

        TrackInfo(String title, String artist) {
            this.title = title;
            this.artist = artist;
        }
    }

    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private AudioManager audioManager;

    private int loopA = -1;
    private int loopB = -1;

    private volatile boolean isLoopSeeking = false;
    private long loopSeekStartTime = 0;

    private boolean released = false;
    private boolean prepared = false;
    private boolean foregroundStarted = false;

    private String currentUri = null;
    private String currentTitle = "Audiotron Player";
    private String currentArtist = "Audiotron Player";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public BackgroundAudioService getService() {
            return BackgroundAudioService.this;
        }
    }

    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            pause();
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            pause();
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            play();
        }
    };

    /*
     * Checks the A-B loop while audio is playing.
     * Keeping this in the service allows looping to continue
     * when the React Native screen is not visible or minimized.
     */
    private final Runnable loopChecker = new Runnable() {
        @Override
        public void run() {
            if (released || mediaPlayer == null || !prepared) {
                return;
            }

            try {
                if (mediaPlayer.isPlaying()
                        && loopA >= 0
                        && loopB > loopA) {

                    if (isLoopSeeking && System.currentTimeMillis() - loopSeekStartTime > 1000) {
                        isLoopSeeking = false;
                    }

                    if (mediaPlayer.getCurrentPosition() >= loopB) {
                        if (!isLoopSeeking) {
                            isLoopSeeking = true;
                            loopSeekStartTime = System.currentTimeMillis();
                            seekTo(loopA, mp -> isLoopSeeking = false);
                        }
                    }

                    handler.postDelayed(this, 100);
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "Unable to check A-B loop position.", e);
            }
        }
    };

    private final MediaSessionCompat.Callback sessionCallback =
            new MediaSessionCompat.Callback() {

        @Override
        public void onPlay() {
            BackgroundAudioService.this.play();
        }

        @Override
        public void onPause() {
            BackgroundAudioService.this.pause();
        }

        @Override
        public void onStop() {
            BackgroundAudioService.this.stop();
        }

        @Override
        public void onSeekTo(long position) {
            BackgroundAudioService.this.seekTo((int) position);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createNotificationChannel();
        createMediaSession();
    }

    private void createMediaSession() {
        mediaSession = new MediaSessionCompat(this, "AudiotronPlayer");

        mediaSession.setCallback(sessionCallback);

        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                        | MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
        );

        mediaSession.setPlaybackState(
                new PlaybackStateCompat.Builder()
                        .setActions(getAvailableActions())
                        .setState(
                                PlaybackStateCompat.STATE_STOPPED,
                                0,
                                0f
                        )
                        .build()
        );

        mediaSession.setActive(true);
    }

    private long getAvailableActions() {
        return PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_SEEK_TO;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
        );

        channel.setDescription("Audio playback controls and status");
        channel.setShowBadge(false);

        NotificationManager manager =
                getSystemService(NotificationManager.class);

        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, String status) {
        Intent launchIntent = getPackageManager()
                .getLaunchIntentForPackage(getPackageName());

        PendingIntent contentIntent = null;

        if (launchIntent != null) {
            contentIntent = PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? PendingIntent.FLAG_IMMUTABLE : 0)
            );
        }

        boolean isPlaying = isPlaying();

        Intent playPauseIntent = new Intent(this, BackgroundAudioService.class);
        playPauseIntent.setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent playPausePendingIntent = PendingIntent.getService(
                this,
                101,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent stopIntent = new Intent(this, BackgroundAudioService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                102,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        String secondaryText = (currentArtist != null && !currentArtist.equals("Audiotron Player"))
                ? currentArtist
                : status;

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher_round)
                        .setContentTitle(title)
                        .setContentText(secondaryText)
                        .setSubText(status)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                        .setOnlyAlertOnce(true)
                        .setOngoing(isPlaying)
                        .setPriority(NotificationCompat.PRIORITY_LOW);

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent);
        }

        if (isPlaying) {
            builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    playPausePendingIntent
            );
        } else {
            builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Play",
                    playPausePendingIntent
            );
        }

        builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
        );

        if (mediaSession != null) {
            MediaStyle mediaStyle = new MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1);
            builder.setStyle(mediaStyle);
        }

        return builder.build();
    }

    public void startForegroundServiceNotification() {
        Notification notification =
                buildNotification(currentTitle, isPlaying() ? "Playing" : "Ready to play");

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            foregroundStarted = true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to start foreground service.", e);
        }
    }

    private void updateNotification(String status) {
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.notify(
                    NOTIFICATION_ID,
                    buildNotification(currentTitle, status)
            );
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        switch (action) {
            case ACTION_PLAY:
                play();
                break;

            case ACTION_PAUSE:
                pause();
                break;

            case ACTION_STOP:
                stop();
                break;

            case ACTION_SEEK_TO:
                seekTo(intent.getIntExtra("position", 0));
                break;

            default:
                Log.d(TAG, "Received action: " + action);
                break;
        }

        return START_NOT_STICKY;
    }

    public void load(String uri) {
        load(uri, null);
    }

    /*
     * Loads a local or content URI asynchronously.
     * Playback starts automatically after preparation, notifying the callback.
     */
    public void load(String uri, LoadCallback callback) {
        if (uri == null || uri.trim().isEmpty()) {
            Log.e(TAG, "Cannot load audio: URI is empty.");
            updatePlaybackState(
                    PlaybackStateCompat.STATE_ERROR, 0, 0
            );
            if (callback != null) {
                callback.onError("Audio URI is empty.");
            }
            return;
        }

        // If the same file is already prepared and loaded, seamlessly resume / report ready
        if (uri.equals(currentUri) && isPrepared()) {
            if (!mediaPlayer.isPlaying()) {
                play();
            }
            if (callback != null) {
                callback.onSuccess(mediaPlayer.getDuration());
            }
            return;
        }

        releasePlayer();

        released = false;
        prepared = false;
        loopA = -1;
        loopB = -1;

        TrackInfo trackInfo = resolveTrackInfo(uri);
        currentTitle = trackInfo.title;
        currentArtist = trackInfo.artist;

        try {
            MediaPlayer player = new MediaPlayer();
            mediaPlayer = player;

            player.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
            );

            // Keeps CPU awake while playing when phone screen turns off
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

            player.setOnPreparedListener(mp -> {
                if (mediaPlayer != mp || released) {
                    mp.release();
                    return;
                }

                prepared = true;
                currentUri = uri;

                try {
                    MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mp.getDuration())
                            .build();
                    mediaSession.setMetadata(metadata);

                    if (audioManager != null) {
                        audioManager.requestAudioFocus(
                                audioFocusChangeListener,
                                AudioManager.STREAM_MUSIC,
                                AudioManager.AUDIOFOCUS_GAIN
                        );
                    }

                    mp.start();
                    startForegroundServiceNotification();

                    updatePlaybackState(
                            PlaybackStateCompat.STATE_PLAYING,
                            mp.getCurrentPosition(),
                            mp.getDuration()
                    );

                    handler.removeCallbacks(loopChecker);
                    if (loopA >= 0 && loopB > loopA) {
                        handler.post(loopChecker);
                    }

                    if (callback != null) {
                        callback.onSuccess(mp.getDuration());
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Unable to start prepared audio.", e);
                    updatePlaybackState(
                            PlaybackStateCompat.STATE_ERROR, 0, 0
                    );
                    if (callback != null) {
                        callback.onError("Unable to start prepared audio: " + e.getMessage());
                    }
                }
            });

            player.setOnCompletionListener(mp -> {
                if (mediaPlayer != mp) {
                    return;
                }

                handler.removeCallbacks(loopChecker);
                clearLoop();

                updatePlaybackState(
                        PlaybackStateCompat.STATE_STOPPED,
                        getCurrentDuration(),
                        getCurrentDuration()
                );

                updateNotification("Playback finished");
            });

            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(
                        TAG,
                        "MediaPlayer error. what=" + what + ", extra=" + extra
                );

                handler.removeCallbacks(loopChecker);
                prepared = false;

                updatePlaybackState(
                        PlaybackStateCompat.STATE_ERROR, 0, 0
                );

                updateNotification("Playback error");
                releasePlayer();

                if (callback != null) {
                    callback.onError("MediaPlayer error (what=" + what + ", extra=" + extra + ")");
                }

                return true;
            });

            player.setDataSource(
                    getApplicationContext(),
                    Uri.parse(uri)
            );

            player.prepareAsync();

            updatePlaybackState(
                    PlaybackStateCompat.STATE_BUFFERING, 0, 0
            );

            updateNotification("Loading audio");

        } catch (IOException | IllegalArgumentException | SecurityException e) {
            Log.e(TAG, "Error loading audio: " + uri, e);

            releasePlayer();

            updatePlaybackState(
                    PlaybackStateCompat.STATE_ERROR, 0, 0
            );

            updateNotification("Unable to load audio");
            if (callback != null) {
                callback.onError("Error loading audio: " + e.getMessage());
            }
        }
    }

    /**
     * Resolves the user-facing title and artist for the track.
     * Prioritizes embedded metadata (track title/name), then falls back to the clean file name.
     */
    private TrackInfo resolveTrackInfo(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return new TrackInfo("Audiotron Player", "Audiotron Player");
        }

        Uri parsedUri = Uri.parse(uri);
        String title = null;
        String artist = null;

        // 1. Try to extract metadata from the audio file itself (ID3 / Vorbis / MP4 tags)
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(getApplicationContext(), parsedUri);

            String metaTitle = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_TITLE
            );
            if (metaTitle != null && !metaTitle.trim().isEmpty()) {
                title = metaTitle.trim();
            }

            String metaArtist = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ARTIST
            );
            if (metaArtist != null && !metaArtist.trim().isEmpty()) {
                artist = metaArtist.trim();
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {}
        }

        if (artist == null || artist.trim().isEmpty()) {
            artist = "Audiotron Player";
        }

        // If track title/name is found, return it
        if (title != null && !title.trim().isEmpty()) {
            return new TrackInfo(title, artist);
        }

        // 2. Fallback to file name via ContentResolver OpenableColumns.DISPLAY_NAME
        String fileName = null;
        if ("content".equalsIgnoreCase(parsedUri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(
                    parsedUri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        String displayName = cursor.getString(nameIndex);
                        if (displayName != null && !displayName.trim().isEmpty()) {
                            fileName = displayName.trim();
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Fallback: Parse file name from the documentId or URI path
        if (fileName == null || fileName.trim().isEmpty()) {
            try {
                String path = parsedUri.getPath();
                String lastSegment = parsedUri.getLastPathSegment();
                String rawName = (lastSegment != null && !lastSegment.isEmpty())
                        ? lastSegment : path;

                if (rawName != null) {
                    try {
                        rawName = URLDecoder.decode(rawName, "UTF-8");
                    } catch (Throwable ignored) {}

                    int lastSlash = rawName.lastIndexOf('/');
                    if (lastSlash >= 0 && lastSlash < rawName.length() - 1) {
                        rawName = rawName.substring(lastSlash + 1);
                    }

                    int lastColon = rawName.lastIndexOf(':');
                    if (lastColon >= 0 && lastColon < rawName.length() - 1) {
                        rawName = rawName.substring(lastColon + 1);
                    }

                    if (!rawName.trim().isEmpty()) {
                        fileName = rawName.trim();
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "Audiotron Player";
        }

        return new TrackInfo(fileName, artist);
    }

    public void play() {
        if (mediaPlayer == null || released || !prepared) {
            Log.w(TAG, "play() ignored: audio is not prepared.");
            return;
        }

        try {
            if (audioManager != null) {
                audioManager.requestAudioFocus(
                        audioFocusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN
                );
            }

            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }

            startForegroundServiceNotification();

            updatePlaybackState(
                    PlaybackStateCompat.STATE_PLAYING,
                    mediaPlayer.getCurrentPosition(),
                    mediaPlayer.getDuration()
            );

            handler.removeCallbacks(loopChecker);
            if (loopA >= 0 && loopB > loopA) {
                handler.post(loopChecker);
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Unable to play audio.", e);
        }
    }

    public void pause() {
        if (mediaPlayer == null || released || !prepared) {
            return;
        }

        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }

            updatePlaybackState(
                    PlaybackStateCompat.STATE_PAUSED,
                    mediaPlayer.getCurrentPosition(),
                    mediaPlayer.getDuration()
            );

            handler.removeCallbacks(loopChecker);
            isLoopSeeking = false;

            if (foregroundStarted) {
                ServiceCompat.stopForeground(
                        this,
                        ServiceCompat.STOP_FOREGROUND_DETACH
                );
                foregroundStarted = false;
            }
            updateNotification("Paused");
        } catch (IllegalStateException e) {
            Log.e(TAG, "Unable to pause audio.", e);
        }
    }

    public void stop() {
        clearLoop();

        if (mediaPlayer == null || released || !prepared) {
            return;
        }

        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }

            mediaPlayer.seekTo(0);
            currentUri = null;

            updatePlaybackState(
                    PlaybackStateCompat.STATE_STOPPED,
                    0,
                    mediaPlayer.getDuration()
            );

            handler.removeCallbacks(loopChecker);
            isLoopSeeking = false;

            if (foregroundStarted) {
                ServiceCompat.stopForeground(
                        this,
                        ServiceCompat.STOP_FOREGROUND_REMOVE
                );
                foregroundStarted = false;
            }

            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID);
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Unable to stop audio.", e);
        }
    }

    public boolean isPlaying() {
        if (mediaPlayer == null || released || !prepared) {
            return false;
        }

        try {
            return mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public int getPlaybackState() {
        if (mediaSession == null
                || mediaSession.getController() == null
                || mediaSession.getController().getPlaybackState() == null) {
            return PlaybackStateCompat.STATE_NONE;
        }

        return mediaSession.getController()
                .getPlaybackState()
                .getState();
    }

    public int getCurrentPosition() {
        if (mediaPlayer == null || released || !prepared) {
            return 0;
        }

        try {
            return mediaPlayer.getCurrentPosition();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public int getCurrentDuration() {
        if (mediaPlayer == null || released || !prepared) {
            return 0;
        }

        try {
            return mediaPlayer.getDuration();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public void seekTo(int position, MediaPlayer.OnSeekCompleteListener listener) {
        if (mediaPlayer == null || released || !prepared) {
            isLoopSeeking = false;
            if (listener != null) {
                listener.onSeekComplete(mediaPlayer);
            }
            return;
        }

        try {
            int duration = mediaPlayer.getDuration();
            int safePosition = Math.max(
                    0,
                    Math.min(position, duration)
            );

            mediaPlayer.setOnSeekCompleteListener(mp -> {
                isLoopSeeking = false;
                updatePlaybackState(
                        getPlaybackState(),
                        safePosition,
                        duration
                );
                if (listener != null) {
                    listener.onSeekComplete(mp);
                }
            });

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mediaPlayer.seekTo((long) safePosition, MediaPlayer.SEEK_CLOSEST);
            } else {
                mediaPlayer.seekTo(safePosition);
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Unable to seek audio.", e);
            isLoopSeeking = false;
            if (listener != null) {
                listener.onSeekComplete(mediaPlayer);
            }
        }
    }

    public void seekTo(int position) {
        seekTo(position, null);
    }

    public int setLoopA() {
        if (mediaPlayer == null || released || !prepared) {
            return -1;
        }

        loopA = getCurrentPosition();
        loopB = -1;
        isLoopSeeking = false;
        handler.removeCallbacks(loopChecker);

        return loopA;
    }

    public int setLoopB() {
        if (mediaPlayer == null || released || !prepared) {
            return -1;
        }

        int position = getCurrentPosition();

        if (loopA < 0 || position <= loopA) {
            Log.w(TAG, "Set loop A before setting loop B.");
            return -1;
        }

        loopB = position;
        isLoopSeeking = false;

        if (mediaPlayer.isPlaying()) {
            handler.removeCallbacks(loopChecker);
            handler.post(loopChecker);
        }

        return loopB;
    }

    public void clearLoop() {
        loopA = -1;
        loopB = -1;
        isLoopSeeking = false;
        handler.removeCallbacks(loopChecker);
    }

    public int getLoopA() {
        return loopA;
    }

    public int getLoopB() {
        return loopB;
    }

    public boolean isPrepared() {
        return prepared && mediaPlayer != null && !released;
    }

    private void releasePlayer() {
        handler.removeCallbacks(loopChecker);
        isLoopSeeking = false;
        prepared = false;

        MediaPlayer oldPlayer = mediaPlayer;
        mediaPlayer = null;

        if (oldPlayer != null) {
            oldPlayer.setOnPreparedListener(null);
            oldPlayer.setOnCompletionListener(null);
            oldPlayer.setOnErrorListener(null);
            oldPlayer.setOnSeekCompleteListener(null);

            try {
                oldPlayer.reset();
            } catch (IllegalStateException ignored) {
            }

            try {
                oldPlayer.release();
            } catch (Exception e) {
                Log.w(TAG, "Error releasing MediaPlayer.", e);
            }
        }
    }

    public void release() {
        released = true;
        currentUri = null;
        loopA = -1;
        loopB = -1;
        isLoopSeeking = false;

        if (audioManager != null) {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }

        releasePlayer();

        updatePlaybackState(
                PlaybackStateCompat.STATE_STOPPED, 0, 0
        );

        if (mediaSession != null) {
            mediaSession.setActive(false);
        }

        if (foregroundStarted) {
            ServiceCompat.stopForeground(
                    this,
                    ServiceCompat.STOP_FOREGROUND_REMOVE
            );
            foregroundStarted = false;
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    private void updatePlaybackState(int state, int position, int duration) {
        if (mediaSession == null) {
            return;
        }

        long actions = getAvailableActions();

        PlaybackStateCompat playbackState =
                new PlaybackStateCompat.Builder()
                        .setActions(actions)
                        .setState(state, Math.max(0, position), 1.0f)
                        .build();

        mediaSession.setPlaybackState(playbackState);

        String status;

        switch (state) {
            case PlaybackStateCompat.STATE_PLAYING:
                status = "Playing";
                break;

            case PlaybackStateCompat.STATE_PAUSED:
                status = "Paused";
                break;

            case PlaybackStateCompat.STATE_BUFFERING:
                status = "Loading audio";
                break;

            case PlaybackStateCompat.STATE_ERROR:
                status = "Playback error";
                break;

            case PlaybackStateCompat.STATE_STOPPED:
                status = "Stopped";
                break;

            default:
                status = "Ready to play";
                break;
        }

        updateNotification(status);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        /*
         * Do not stop the service just because the app task is removed:
         * ongoing audio is allowed to continue in background.
         */
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        released = true;
        currentUri = null;
        if (audioManager != null) {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
        releasePlayer();

        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }

        handler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }
}

package com.garbagecam.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaCodecInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.sources.audio.NoAudioSource;
import com.pedro.encoder.input.sources.video.Camera2Source;
import com.pedro.rtspserver.RtspServerStream;

public class GarbageCamService extends Service implements ConnectChecker {
  public static final String ACTION_START = "com.garbagecam.app.START";
  public static final String ACTION_STOP = "com.garbagecam.app.STOP";

  private static final String TAG = "GarbageCam";
  private static final String CHANNEL_ID = "garbagecam_stream";
  private static final int NOTIFICATION_ID = 71;

  public static volatile boolean running = false;
  public static volatile String status = "STOPPED";
  public static volatile String endpoint = "";
  public static volatile long currentBitrate = 0;
  public static volatile int clients = 0;

  private RtspServerStream stream;
  private Camera2Source cameraSource;
  private PowerManager.WakeLock wakeLock;
  private WifiManager.WifiLock wifiLock;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Runnable idrTask;
  private Runnable statsTask;

  @Override
  public void onCreate() {
    super.onCreate();
    createNotificationChannel();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    String action = intent != null ? intent.getAction() : null;
    if (ACTION_STOP.equals(action)) {
      stopStreaming();
      stopSelf();
      return START_NOT_STICKY;
    }

    startForeground(NOTIFICATION_ID, buildNotification("Starting..."));
    if (!running) startStreaming();
    return START_STICKY;
  }

  private void startStreaming() {
    GarbageCamSettings s = GarbageCamSettings.load(this);
    status = "STARTING";
    updateNotification(status);

    try {
      acquireLocks();

      cameraSource = new Camera2Source(this);
      cameraSource.setDynamicFps(false);

      stream = new RtspServerStream(this, s.port, this, cameraSource, new NoAudioSource());
      stream.getStreamClient().setOnlyVideo(true);
      stream.getStreamClient().setLogs(false);
      if (s.username != null && !s.username.isEmpty()) {
        stream.getStreamClient().setAuthorization(s.username, s.password == null ? "" : s.password);
      }

      int profile = s.profile;
      if (profile != MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
          && profile != MediaCodecInfo.CodecProfileLevel.AVCProfileMain
          && profile != MediaCodecInfo.CodecProfileLevel.AVCProfileHigh) {
        profile = MediaCodecInfo.CodecProfileLevel.AVCProfileMain;
      }

      boolean videoPrepared = stream.prepareVideo(
          s.width,
          s.height,
          s.bitrateKbps * 1000,
          s.fps,
          Math.max(1, s.iFrameSeconds),
          s.rotation,
          profile,
          -1
      );
      // RootEncoder expects the audio encoder to be prepared before startStream even when
      // the source is NoAudioSource. No microphone is opened and the RTSP server is set
      // to video-only, so this is just a harmless internal encoder initialization.
      boolean audioPrepared = stream.prepareAudio(32000, false, 64000);

      if (!videoPrepared || !audioPrepared) {
        throw new IllegalStateException("Phone encoder rejected the selected video settings");
      }

      stream.startStream();
      endpoint = stream.getStreamClient().getEndPointConnection();
      running = true;
      status = "STREAMING";
      updateNotification(status + "  " + endpoint);
      scheduleForcedIdr(s.forceIdrSeconds);
      scheduleStats();

    } catch (Throwable t) {
      Log.e(TAG, "Unable to start stream", t);
      status = "ERROR: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
      running = false;
      endpoint = "";
      updateNotification(status);
      cleanupStream();
      releaseLocks();
    }
  }

  private void scheduleForcedIdr(int seconds) {
    if (idrTask != null) handler.removeCallbacks(idrTask);
    if (seconds <= 0 || stream == null) return;
    idrTask = new Runnable() {
      @Override public void run() {
        if (stream != null && running) {
          try { stream.requestKeyframe(); } catch (Throwable ignored) {}
          handler.postDelayed(this, seconds * 1000L);
        }
      }
    };
    handler.postDelayed(idrTask, seconds * 1000L);
  }

  private void scheduleStats() {
    if (statsTask != null) handler.removeCallbacks(statsTask);
    statsTask = new Runnable() {
      @Override public void run() {
        if (stream != null && running) {
          try { clients = stream.getStreamClient().getNumClients(); } catch (Throwable ignored) {}
          handler.postDelayed(this, 1000L);
        }
      }
    };
    handler.post(statsTask);
  }

  private void stopStreaming() {
    status = "STOPPING";
    running = false;
    if (idrTask != null) handler.removeCallbacks(idrTask);
    if (statsTask != null) handler.removeCallbacks(statsTask);
    cleanupStream();
    releaseLocks();
    endpoint = "";
    clients = 0;
    currentBitrate = 0;
    status = "STOPPED";
    stopForeground(STOP_FOREGROUND_REMOVE);
  }

  private void cleanupStream() {
    if (stream != null) {
      try { if (stream.isStreaming()) stream.stopStream(); } catch (Throwable ignored) {}
      try { stream.release(); } catch (Throwable ignored) {}
      stream = null;
    }
    cameraSource = null;
  }

  private void acquireLocks() {
    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    if (pm != null) {
      wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GarbageCam:CameraWake");
      wakeLock.setReferenceCounted(false);
      wakeLock.acquire();
    }

    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    if (wm != null) {
      wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "GarbageCam:WifiWake");
      wifiLock.setReferenceCounted(false);
      wifiLock.acquire();
    }
  }

  private void releaseLocks() {
    if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    wakeLock = null;
    if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
    wifiLock = null;
  }

  private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
          CHANNEL_ID, "GarbageCam streaming", NotificationManager.IMPORTANCE_LOW);
      channel.setDescription("Keeps the camera and RTSP server alive while the screen is off");
      NotificationManager nm = getSystemService(NotificationManager.class);
      if (nm != null) nm.createNotificationChannel(channel);
    }
  }

  private Notification buildNotification(String text) {
    Intent open = new Intent(this, MainActivity.class);
    PendingIntent pi = PendingIntent.getActivity(
        this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    return new NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.presence_video_online)
        .setContentTitle("GarbageCam")
        .setContentText(text)
        .setContentIntent(pi)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build();
  }

  private void updateNotification(String text) {
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
  }

  @Override public void onConnectionStarted(String url) { }

  @Override public void onConnectionSuccess() {
    status = "STREAMING";
  }

  @Override public void onConnectionFailed(String reason) {
    status = "CLIENT ERROR: " + reason;
  }

  @Override public void onDisconnect() { }

  @Override public void onAuthError() {
    status = "AUTH ERROR";
  }

  @Override public void onAuthSuccess() { }

  @Override public void onNewBitrate(long bitrate) {
    currentBitrate = bitrate;
  }

  @Override
  public void onDestroy() {
    stopStreaming();
    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}

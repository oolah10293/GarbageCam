package com.garbagecam.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaCodecInfo;

public final class GarbageCamSettings {
  private static final String PREFS = "garbagecam";

  public int width = 1280;
  public int height = 720;
  public int fps = 15;
  public int bitrateKbps = 2000;
  public int iFrameSeconds = 2;
  public int forceIdrSeconds = 2;
  public int rotation = 0;
  public int profile = MediaCodecInfo.CodecProfileLevel.AVCProfileMain;
  public int port = 8554;
  public String username = "camera";
  public String password = "garbage";

  public static GarbageCamSettings load(Context context) {
    SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    GarbageCamSettings s = new GarbageCamSettings();
    s.width = p.getInt("width", s.width);
    s.height = p.getInt("height", s.height);
    s.fps = p.getInt("fps", s.fps);
    s.bitrateKbps = p.getInt("bitrate", s.bitrateKbps);
    s.iFrameSeconds = p.getInt("iframe", s.iFrameSeconds);
    s.forceIdrSeconds = p.getInt("force_idr", s.forceIdrSeconds);
    s.rotation = p.getInt("rotation", s.rotation);
    s.profile = p.getInt("profile", s.profile);
    s.port = p.getInt("port", s.port);
    s.username = p.getString("username", s.username);
    s.password = p.getString("password", s.password);
    return s;
  }

  public void save(Context context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putInt("width", width)
        .putInt("height", height)
        .putInt("fps", fps)
        .putInt("bitrate", bitrateKbps)
        .putInt("iframe", iFrameSeconds)
        .putInt("force_idr", forceIdrSeconds)
        .putInt("rotation", rotation)
        .putInt("profile", profile)
        .putInt("port", port)
        .putString("username", username)
        .putString("password", password)
        .apply();
  }
}

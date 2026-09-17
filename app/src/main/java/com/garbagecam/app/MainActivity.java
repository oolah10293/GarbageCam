package com.garbagecam.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
  private static final int REQ_CAMERA = 41;
  private static final String[] RESOLUTIONS = {
      "640x480", "800x600", "960x540", "1280x720", "1920x1080", "2560x1440"
  };
  private static final String[] PROFILES = { "Baseline", "Main", "High" };
  private static final String[] ROTATIONS = { "0", "90", "180", "270" };

  private Spinner resolutionSpinner, profileSpinner, rotationSpinner;
  private EditText fpsEdit, bitrateEdit, iFrameEdit, forceIdrEdit, portEdit, userEdit, passEdit;
  private TextView statusText, urlText;
  private final Handler handler = new Handler(Looper.getMainLooper());

  private final Runnable statusUpdater = new Runnable() {
    @Override public void run() {
      refreshStatus();
      handler.postDelayed(this, 750);
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    statusText = findViewById(R.id.statusText);
    urlText = findViewById(R.id.urlText);
    resolutionSpinner = findViewById(R.id.resolutionSpinner);
    profileSpinner = findViewById(R.id.profileSpinner);
    rotationSpinner = findViewById(R.id.rotationSpinner);
    fpsEdit = findViewById(R.id.fpsEdit);
    bitrateEdit = findViewById(R.id.bitrateEdit);
    iFrameEdit = findViewById(R.id.iFrameEdit);
    forceIdrEdit = findViewById(R.id.forceIdrEdit);
    portEdit = findViewById(R.id.portEdit);
    userEdit = findViewById(R.id.userEdit);
    passEdit = findViewById(R.id.passEdit);

    setupSpinner(resolutionSpinner, RESOLUTIONS);
    setupSpinner(profileSpinner, PROFILES);
    setupSpinner(rotationSpinner, ROTATIONS);

    loadUi(GarbageCamSettings.load(this));

    findViewById(R.id.presetSafe).setOnClickListener(v -> applyPresetSafe());
    findViewById(R.id.presetAnnke).setOnClickListener(v -> applyPresetAnnke());
    findViewById(R.id.presetPush).setOnClickListener(v -> applyPresetPush());

    findViewById(R.id.startButton).setOnClickListener(v -> startGarbageCam());
    findViewById(R.id.stopButton).setOnClickListener(v -> stopGarbageCam());

    requestPermissionsIfNeeded();
  }

  private void setupSpinner(Spinner spinner, String[] items) {
    ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
    a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinner.setAdapter(a);
  }

  private void requestPermissionsIfNeeded() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }
    if (Build.VERSION.SDK_INT >= 33
        && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
          new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
    }
  }

  private void startGarbageCam() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      requestPermissionsIfNeeded();
      return;
    }

    GarbageCamSettings settings;
    try {
      settings = readUi();
    } catch (Exception e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
      return;
    }
    settings.save(this);

    Intent i = new Intent(this, GarbageCamService.class).setAction(GarbageCamService.ACTION_START);
    ContextCompat.startForegroundService(this, i);
  }

  private void stopGarbageCam() {
    Intent i = new Intent(this, GarbageCamService.class).setAction(GarbageCamService.ACTION_STOP);
    startService(i);
  }

  private GarbageCamSettings readUi() {
    GarbageCamSettings s = new GarbageCamSettings();
    String[] wh = RESOLUTIONS[resolutionSpinner.getSelectedItemPosition()].split("x");
    s.width = Integer.parseInt(wh[0]);
    s.height = Integer.parseInt(wh[1]);
    s.fps = parsePositive(fpsEdit, "FPS");
    s.bitrateKbps = parsePositive(bitrateEdit, "Bitrate");
    s.iFrameSeconds = parsePositive(iFrameEdit, "I-frame interval");
    s.forceIdrSeconds = parseNonNegative(forceIdrEdit, "Force IDR interval");
    s.rotation = Integer.parseInt(ROTATIONS[rotationSpinner.getSelectedItemPosition()]);
    s.port = parsePort(portEdit);
    s.username = userEdit.getText().toString().trim();
    s.password = passEdit.getText().toString();

    switch (profileSpinner.getSelectedItemPosition()) {
      case 0: s.profile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline; break;
      case 2: s.profile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh; break;
      default: s.profile = MediaCodecInfo.CodecProfileLevel.AVCProfileMain;
    }
    return s;
  }

  private int parsePositive(EditText e, String name) {
    int v = Integer.parseInt(e.getText().toString().trim());
    if (v <= 0) throw new IllegalArgumentException(name + " must be greater than zero");
    return v;
  }

  private int parseNonNegative(EditText e, String name) {
    int v = Integer.parseInt(e.getText().toString().trim());
    if (v < 0) throw new IllegalArgumentException(name + " cannot be negative");
    return v;
  }

  private int parsePort(EditText e) {
    int v = parsePositive(e, "Port");
    if (v > 65535) throw new IllegalArgumentException("Port must be 1-65535");
    return v;
  }

  private void loadUi(GarbageCamSettings s) {
    select(resolutionSpinner, s.width + "x" + s.height, RESOLUTIONS);
    fpsEdit.setText(String.valueOf(s.fps));
    bitrateEdit.setText(String.valueOf(s.bitrateKbps));
    iFrameEdit.setText(String.valueOf(s.iFrameSeconds));
    forceIdrEdit.setText(String.valueOf(s.forceIdrSeconds));
    portEdit.setText(String.valueOf(s.port));
    userEdit.setText(s.username);
    passEdit.setText(s.password);
    select(rotationSpinner, String.valueOf(s.rotation), ROTATIONS);

    if (s.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline) profileSpinner.setSelection(0);
    else if (s.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh) profileSpinner.setSelection(2);
    else profileSpinner.setSelection(1);
  }

  private void select(Spinner spinner, String value, String[] values) {
    for (int i = 0; i < values.length; i++) {
      if (values[i].equals(value)) {
        spinner.setSelection(i);
        return;
      }
    }
  }

  private void applyPresetSafe() {
    select(resolutionSpinner, "1280x720", RESOLUTIONS);
    fpsEdit.setText("15");
    bitrateEdit.setText("2000");
    profileSpinner.setSelection(1);
    iFrameEdit.setText("2");
    forceIdrEdit.setText("2");
  }

  private void applyPresetAnnke() {
    select(resolutionSpinner, "1920x1080", RESOLUTIONS);
    fpsEdit.setText("20");
    bitrateEdit.setText("6144");
    profileSpinner.setSelection(1);
    // Native I51DS is 50 frames @ 20 fps (~2.5 sec); MediaCodec accepts whole seconds.
    iFrameEdit.setText("3");
    forceIdrEdit.setText("3");
  }

  private void applyPresetPush() {
    select(resolutionSpinner, "1920x1080", RESOLUTIONS);
    fpsEdit.setText("30");
    bitrateEdit.setText("8000");
    profileSpinner.setSelection(2);
    iFrameEdit.setText("2");
    forceIdrEdit.setText("2");
  }

  private void refreshStatus() {
    String s = GarbageCamService.status;
    statusText.setText(s + (GarbageCamService.running ? "   clients=" + GarbageCamService.clients : ""));
    statusText.setTextColor(Color.parseColor(GarbageCamService.running ? "#79FF79" : "#FF7B7B"));

    if (GarbageCamService.running && !GarbageCamService.endpoint.isEmpty()) {
      GarbageCamSettings settings = GarbageCamSettings.load(this);
      String url = GarbageCamService.endpoint;
      if (settings.username != null && !settings.username.isEmpty()) {
        url = url.replace("rtsp://", "rtsp://" + settings.username + ":" + settings.password + "@");
      }
      urlText.setText(url + "\nCurrent output: " + settings.width + "x" + settings.height
          + " @ " + settings.fps + " fps, " + settings.bitrateKbps + " kbps");
    } else {
      urlText.setText("RTSP URL will appear here");
    }
  }

  @Override protected void onResume() {
    super.onResume();
    handler.post(statusUpdater);
  }

  @Override protected void onPause() {
    handler.removeCallbacks(statusUpdater);
    super.onPause();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQ_CAMERA && grantResults.length > 0
        && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
      Toast.makeText(this, "GarbageCam needs camera permission to do camera things.",
          Toast.LENGTH_LONG).show();
    }
  }
}

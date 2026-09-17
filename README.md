# GarbageCam v0.1

A deliberately boring Android RTSP camera for retired phones.

**Target test device:** Samsung Galaxy S8  
**Target NVR:** ANNKE N48PBB / Hikvision K75 platform

## What v0.1 does

- Rear Camera2 source
- Hardware H.264 via Android MediaCodec
- Local RTSP server on the phone
- Video only; no microphone/audio yet
- Runs as a foreground camera service so the screen can be off
- Holds CPU + high-performance Wi-Fi wake locks while streaming
- Adjustable resolution, FPS, bitrate, H.264 profile and I-frame interval
- Optional periodic forced IDR/keyframe requests
- Optional RTSP username/password
- Root RTSP path (`/`) to make NVR custom-protocol setup simple

The app uses Pedro Sánchez's Apache-2.0 **RTSP-Server** and **RootEncoder** libraries. The server supports RTSP clients pulling directly from the phone, while RootEncoder uses Camera2 + MediaCodec.

## Why these defaults

A working ANNKE I51DS was inspected as a reference.

### I51DS main stream reference

- 1920x1080
- H.264 Main Profile
- 20 fps
- Variable bitrate
- Max 6144 kbps
- I-frame interval 50 frames (~2.5 sec)
- H.264+ OFF

### I51DS substream reference

- 1280x720
- H.264 Baseline Profile
- 8 fps
- I-frame interval 32 frames (~4 sec)

Android's MediaCodec `KEY_I_FRAME_INTERVAL` is specified in **seconds**, not frames, so GarbageCam exposes seconds. The `ANNKE-ish` preset uses 3 seconds as the closest whole-second match to the I51DS main stream.

**Important:** RootEncoder currently prefers hardware **CBR** when the encoder supports it. v0.1 therefore does not claim to reproduce ANNKE's VBR rate control exactly. The first goal is stable NVR decoding and predictable keyframes. We can patch the encoder for explicit VBR/CBR selection later if it matters.

## Build

1. Open the project folder in Android Studio.
2. Let Gradle sync/download dependencies.
3. Select the S8 as the target device.
4. Build/Run the `app` configuration.
5. Grant Camera permission.

Dependencies are pulled from Google/Maven Central/JitPack.

## First test

Tap **SAFE 720P** and then **START**.

Expected RTSP URL format:

```text
rtsp://camera:garbage@PHONE_IP:8554/
```

Test that URL in VLC first.

## ANNKE N48PBB custom protocol

For the GarbageCam custom protocol:

- Type: RTSP
- Transfer protocol: Auto first
- Port: 8554
- Path: blank
- Substream: disabled initially

Then edit an unused NVR D-channel (the pencil, as is tradition):

- Adding method: Manual
- Channel address: PHONE_IP
- Protocol: GarbageCam custom protocol
- Management port: 8554
- Channel port: 1
- Transfer protocol: Auto
- Username/password: same values configured in GarbageCam

## Presets

### SAFE 720P

- 1280x720
- 15 fps
- 2000 kbps
- H.264 Main
- Encoder I-frame: 2 sec
- Forced IDR: 2 sec

### ANNKE-ish 1080P

- 1920x1080
- 20 fps
- 6144 kbps
- H.264 Main
- Encoder I-frame: 3 sec
- Forced IDR: 3 sec

### PUSH IT 1080P30

- 1920x1080
- 30 fps
- 8000 kbps
- H.264 High
- Encoder I-frame: 2 sec
- Forced IDR: 2 sec

If a profile/resolution combination is rejected by the S8's hardware encoder, GarbageCam will show an error rather than silently changing it.

## v0.2 candidates

- Live preview/aiming screen
- Explicit CBR/VBR switch (requires encoder patch/fork)
- Two independent main/sub streams
- ONVIF discovery
- Optional audio
- Boot auto-start
- Exposure/focus controls
- Better runtime codec capability inspection
- Optional timestamp overlay

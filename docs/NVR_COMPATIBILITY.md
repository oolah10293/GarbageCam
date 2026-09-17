# ANNKE N48PBB compatibility notes

GarbageCam was started after testing generic RTSP sources against an ANNKE N48PBB recorder.

## Recorder

- ANNKE N48PBB
- Hikvision K75-derived platform
- ANNKE V4.30.080 build 210412 tested

## Useful NVR behavior discovered during testing

- Existing logical D-channels must be edited with the pencil icon when the recorder reports `No more IP camera allowed`.
- `Channel Port` is the camera's internal channel index, not the NVR D-channel number; single-sensor cameras normally use `1`.
- Multi-camera live view may use a lower-bandwidth/sub stream while full-screen view switches to the main stream.
- High main-stream resolutions can exceed the recorder's practical decode path even when the substream works.

Observed working examples after lowering main-stream resolution:

- Reolink E331: 2560x1440 H.264
- ANNKE I51DS: 1920x1080 H.264

## GarbageCam custom RTSP protocol

Initial N48PBB settings:

- Type: RTSP
- Port: 8554
- Path: blank/root
- Substream: disabled for v0.1
- Channel Port: 1
- Transfer Protocol: Auto first

Use the same RTSP username/password configured in GarbageCam.

## Reference ANNKE encoder settings

A working I51DS was used as the stream-shape reference.

### Main stream

- 1920x1080
- H.264 Main Profile
- 20 fps
- Variable bitrate
- Max bitrate 6144 kbps
- I-frame interval 50 frames (~2.5 s)
- H.264+ off

### Substream

- 1280x720
- H.264 Baseline Profile
- 8 fps
- I-frame interval 32 frames (~4 s)

GarbageCam v0.1 approximates the native keyframe timing using MediaCodec's seconds-based I-frame interval plus optional forced IDR requests.

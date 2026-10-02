# Recording

Setup → Record: container (**mp4 / mkv / mov / flv**), quality preset,
encoder, and output path (game-relative, default
`obsnomore/capture.mp4`).

## Controls

- **Start Rec / Stop Rec** in Controls (or hotkeys F8/F9).
- **Pause** stops the current file and resumes into a new
  `capture_part2.mp4`, `_part3.mp4`, … — nothing is lost, the stream of
  files just splits.
- Recording uses the same quality/encoder settings as streaming unless
  you set its own in Setup → Record.

## 1-POV / 2-POV

Same rule as streaming: 1-POV records your screen as-is; 2-POV records
the clean game with sources composited in by ffmpeg.

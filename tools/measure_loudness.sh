#!/bin/sh
# 校验 assets 铃声响度是否齐平（目标 -20 LUFS，峰值 ≤ -3.5dBFS）。新增/替换素材后运行。
cd "$(dirname "$0")/../assets" || exit 1
printf '%-12s %10s %10s %10s\n' file LUFS peak RMS
for f in ring1.mp3 ring2.mp3 ring3.ogg ring4.ogg ring5.mp3 ring6.mp3 ring7.mp3 ring8.mp3; do
  [ -f "$f" ] || continue
  L=$(ffmpeg -hide_banner -nostats -i "$f" -af ebur128=framelog=quiet -f null - 2>&1 | grep -E "^ *I:" | tail -1 | sed 's/.*I: *//;s/ LUFS//')
  V=$(ffmpeg -hide_banner -nostats -i "$f" -af "astats=measure_perchannel=none:measure_overall=Peak_level+RMS_level:metadata=0" -f null - 2>&1 | grep -E "Peak level dB|RMS level dB" | sed 's/.*: //' | tr '\n' ' ')
  printf '%-12s %10s %10s\n' "$f" "$L" "$V"
done

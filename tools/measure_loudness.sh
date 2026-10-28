#!/bin/sh
# 测 assets 下全部内置铃声的 RMS / Peak(dBFS)，并算出应写入 ChimeService.normalGain() 的增益。
# 新增或重编码铃声后运行：sh tools/measure_loudness.sh
TARGET_RMS=-19.5
HEADROOM=-9.4
cd "$(dirname "$0")/../assets" || exit 1
printf '%-12s %9s %9s %9s %9s %8s\n' file RMS peak want_dB cap_dB gain_dB
for f in ring1.mp3 ring2.mp3 ring3.ogg ring4.ogg ring5.mp3 ring6.mp3 ring7.mp3 ring8.mp3; do
  [ -f "$f" ] || continue
  V=$(ffmpeg -hide_banner -nostats -i "$f" -af "astats=measure_perchannel=none:measure_overall=Peak_level+RMS_level:metadata=0" -f null - 2>&1)
  P=$(echo "$V" | grep "Peak level dB" | sed 's/.*: //')
  R=$(echo "$V" | grep "RMS level dB"  | sed 's/.*: //')
  W=$(awk -v t=$TARGET_RMS -v r="$R" 'BEGIN{printf "%.2f", t-r}')
  C=$(awk -v h=$HEADROOM  -v p="$P" 'BEGIN{printf "%.2f", h-p}')
  G=$(awk -v w="$W" -v c="$C" 'BEGIN{d=(w<c)?w:c; if(d>0)d=0; printf "%.2f", d}')
  printf '%-12s %9s %9s %9s %9s %8s\n' "$f" "$R" "$P" "$W" "$C" "$G"
done

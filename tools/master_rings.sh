#!/bin/sh
# 铃声素材母带：把 assets/ring*.{mp3,ogg} 统一到 -20 LUFS，峰值 ≤ -6dBFS，并保持原编码格式/采样率/声道。
# 用法：sh tools/master_rings.sh            处理 assets 下全部内置铃声
#       sh tools/master_rings.sh new.mp3    处理单个新素材（原地覆盖）
# 原理：alimiter 先压峰值 → LUFS 归一到 -20 → 按需回补 → 编码。改完跑 sh tools/measure_loudness.sh 校验。
set -e
cd "$(dirname "$0")/.."
TARGET=-20.0
CEIL=-6
TMP=_loud_work; rm -rf $TMP; mkdir -p $TMP/lim $TMP/out

LUFS() { ffmpeg -hide_banner -nostats -i "$1" -af ebur128=framelog=quiet -f null - 2>&1 | grep -E "^ *I:" | tail -1 | sed 's/.*I: *//;s/ LUFS//'; }
PEAK() { ffmpeg -hide_banner -nostats -i "$1" -af "astats=measure_perchannel=none:measure_overall=Peak_level:metadata=0" -f null - 2>&1 | grep "Peak level dB" | sed 's/.*: //'; }

FILES=${*:-$(ls assets/ring*.mp3 assets/ring*.ogg 2>/dev/null)}
for src in $FILES; do
  [ -f "$src" ] || continue
  base=$(basename "$src"); stem=${base%.*}; ext=${base##*.}
  read SR CH CODEC <<EOT
$(ffprobe -v error -select_streams a:0 -show_entries stream=sample_rate,channels,codec_name -of default=nw=1:nk=1 "$src" | tr '\n' ' ')
EOT
  ffmpeg -hide_banner -loglevel error -y -i "$src" -af "alimiter=limit=$(awk -v c=$CEIL 'BEGIN{print 10^(c/20)}'):attack=1:release=40:level=disabled" -c:a pcm_f32le $TMP/lim/$stem.wav
  P=$(PEAK $TMP/lim/$stem.wav); G=$(awk -v p="$P" -v c=$CEIL 'BEGIN{printf "%.2f", c-p}')
  ffmpeg -hide_banner -loglevel error -y -i $TMP/lim/$stem.wav -af "volume=${G}dB" -c:a pcm_f32le $TMP/out/$stem.wav
  L=$(LUFS $TMP/out/$stem.wav); D=$(awk -v l="$L" -v t=$TARGET 'BEGIN{printf "%.2f", t-l}')
  ffmpeg -hide_banner -loglevel error -y -i $TMP/out/$stem.wav -af "volume=${D}dB" -c:a pcm_f32le $TMP/out/$stem.wav.norm.wav
  mv $TMP/out/$stem.wav.norm.wav $TMP/out/$stem.wav
  case $ext in
    mp3) ffmpeg -hide_banner -loglevel error -y -i $TMP/out/$stem.wav -c:a libmp3lame -q:a 2 -ar $SR -ac $CH "$src" ;;
    ogg) ffmpeg -hide_banner -loglevel error -y -i $TMP/out/$stem.wav -c:a libvorbis -q:a 4 -ar $SR -ac $CH "$src" ;;
  esac
  echo "$base  ${SR}Hz/${CH}ch -> $(LUFS "$src") LUFS  peak $(PEAK "$src") dBFS"
done

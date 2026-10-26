package com.len.hourlychime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import org.json.JSONObject;

/** 唯一广播入口：被精确闹钟唤醒 -> 找命中方案 -> 播放 -> 排下一次 */
public class ChimeReceiver extends BroadcastReceiver {

    private static MediaPlayer mp;
    private static int savedVol = -1;

    @Override
    public void onReceive(Context ctx, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            scheduleNext(ctx);
            return;
        }
        // 闹钟触发：goAsync 保持进程存活直到播完
        final android.content.BroadcastReceiver.PendingResult pr = goAsync();
        JSONObject p = Scheduler.enabled(ctx) ? Scheduler.firingProfile(ctx) : null;
        if (p != null) {
            if (Scheduler.vibrate(ctx)) vibrate(ctx);
            play(ctx, p, pr);
        } else {
            scheduleNext(ctx);
            pr.finish();
        }
    }

    public static void scheduleNext(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, new Intent(ctx, ChimeReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long at = Scheduler.nextTrigger(ctx);
        if (at < 0) { am.cancel(pi); return; }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    private static void play(final Context ctx, JSONObject p, final android.content.BroadcastReceiver.PendingResult pr) {
        try {
            if (mp != null) { mp.release(); mp = null; }
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            int vol = Scheduler.effectiveVol(ctx, p);   // 全局覆盖优先，其次方案自身（-1=跟随系统）
            savedVol = -1;
            float gain = 1f;
            if (vol >= 0) {
                // 双保险：闹铃流尽量拉满 + MediaPlayer 按百分比精确衰减
                // （部分系统/勿扰场景会拒绝 setStreamVolume，仅靠它音量会不准）
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                savedVol = am.getStreamVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
                gain = Math.max(0f, Math.min(1f, vol / 100f));
            }
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            // 注意：setVolume 必须在 prepare 之后调用——Idle 态调用会被系统静默忽略（增益失效=全音量）
            final float g = gain;
            String ring = p.optString("ring", "bell");
            if (ring.startsWith("sys:") || ring.startsWith("doc:")) {
                // 系统音效或自选文件：ring 值内嵌 URI
                mp.setDataSource(ctx, Uri.parse(ring.substring(4)));
            } else {
                String file;
                switch (ring) {
                    // 铃声 key（存于偏好）与 assets 文件名解耦，文件名归一化为 ring1~ring8
                    case "bell":   file = "ring2.mp3";  break;
                    case "wind":   file = "ring3.ogg";  break;
                    case "sing":   file = "ring1.mp3";  break;
                    case "d1":     file = "ring5.mp3";  break;
                    case "d2":     file = "ring6.mp3";  break;
                    case "d3":     file = "ring7.mp3";  break;
                    case "d4":     file = "ring8.mp3";  break;
                    default:       file = "ring4.ogg";  break;
                }
                AssetFileDescriptor fd = ctx.getAssets().openFd(file);
                mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                fd.close();
            }
            mp.prepare();
            mp.setVolume(g, g);
            final Context app = ctx.getApplicationContext();
            mp.setOnCompletionListener(m -> {
                m.release();
                mp = null;
                if (savedVol >= 0) {
                    AudioManager a2 = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
                    a2.setStreamVolume(AudioManager.STREAM_ALARM, savedVol, 0);
                    savedVol = -1;
                }
                scheduleNext(app);
                pr.finish();
            });
            mp.start();
            mp.setVolume(g, g);   // 个别系统 start 时会重置增益，补一次保险
        } catch (Exception e) {
            // 播放失败也要排下一次并释放
            scheduleNext(ctx);
            pr.finish();
            savedVol = -1;
        }
    }

    private static void vibrate(Context ctx) {
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm.getDefaultVibrator();
            } else {
                v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            }
            v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) { }
    }
}

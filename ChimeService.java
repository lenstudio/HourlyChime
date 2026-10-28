package com.len.hourlychime;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

/**
 * 常驻前台服务：自持时间线，不依赖系统闹钟派发。
 *
 * 背景：MIUI 上 setExactAndAllowWhileIdle 会退化为 setExact（exactAllowReason=policy_permission），
 * 叠加 delay_nonwakeup_alarms_while_screen_off + Doze，派发延迟可达数分钟到数小时；
 * 广播延迟到达后若只按“当前分钟”匹配规则，就会静默跳过 = 漏响。
 * 因此改为前台服务挂 PARTIAL_WAKE_LOCK 自持计时：灭屏也不休眠，整点准时触发。
 * 系统闹钟（ChimeReceiver）降级为兜底：进程被 MIUI 杀掉后把它拉回来。
 */
public class ChimeService extends Service {

    public static final String CH = "chime_run";
    public static final int NOTI_ID = 1001;
    private static final long TICK = 1000L;
    private static final long RING_MS = 30_000L;   // 单次报时最长响 30s

    private PowerManager.WakeLock wake;
    private MediaPlayer mp;
    private int savedVol = -1;
    private boolean ringing;
    private long ringingUntil;
    private int lastFiredDateKey = -1;   // 防止同一分钟重复触发
    private int lastFiredMinute = -1;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int lastBadgeMinute = -1;

    /**
     * 单实例保护。同一进程内 Service 只会有一个实例，但更新 APK 后旧进程可能残留
     * （force-stop 对已 startForeground 的服务不总是生效），新旧两个进程会同时报时。
     * 这里用「时间戳心跳」判定：新实例启动时若发现 30 秒内有人写过心跳，说明已有活着的实例，
     * 自己是多余的那个，直接 stopSelf()。
     */
    private static final String KEY_HB = "svc_heartbeat_ms";
    private long myStartAt;

    private boolean anotherAlive() {
        try {
            long hb = getSharedPreferences(KEY_HB, MODE_PRIVATE).getLong(KEY_HB, 0);
            return hb > 0 && Math.abs(System.currentTimeMillis() - hb) < 30_000L
                    && hb > myStartAt;
        } catch (Exception e) {
            return false;
        }
    }

    private static final String KEY_FIRED = "fired_mark";

    /** 该 (日, 分钟) 是否已响过 */
    private boolean firedOnDisk(int dateKey, int minute) {
        try {
            String v = getSharedPreferences(KEY_FIRED, MODE_PRIVATE).getString(KEY_FIRED, "");
            return v.equals(dateKey + ":" + minute);
        } catch (Exception e) {
            return false;
        }
    }

    /** 记录「这一分钟已响」，并打印实际命中类别，便于事后核对 */
    private void markFired(int dateKey, int minute, JSONObject hit) {
        try {
            getSharedPreferences(KEY_FIRED, MODE_PRIVATE).edit()
                    .putString(KEY_FIRED, dateKey + ":" + minute).apply();
            System.out.println("[Chime] fire date=" + dateKey + " min=" + minute
                    + " cat=" + hit.optString("cat")
                    + " ring=" + hit.optString("ring")
                    + " vol=" + hit.optInt("vol", -999));
        } catch (Exception ignored) { }
    }

    private void beat() {
        try {
            getSharedPreferences(KEY_HB, MODE_PRIVATE).edit().putLong(KEY_HB, System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
    }

    private void clearBeat() {
        try { getSharedPreferences(KEY_HB, MODE_PRIVATE).edit().remove(KEY_HB).apply(); } catch (Exception ignored) { }
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            try { onTick(); } catch (Throwable ignored) { }
            beat();
            h.postDelayed(this, TICK);
        }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, ChimeService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, ChimeService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        myStartAt = System.currentTimeMillis();
        if (anotherAlive()) {
            // 已有实例在跑，自己是重复实例（多半是旧 APK 残留进程），直接退出
            System.out.println("[Chime] duplicate instance detected, stopSelf");
            stopSelf();
            return;
        }
        beat();
        createChannel();
        try {
            startForeground(NOTI_ID, buildNoti(getString(R.string.noti_waiting, "—"), null));
        } catch (Throwable t) {
            // 极端情况下（禁止前台服务）也要尽量活着
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HourlyChime::timeline");
        wake.setReferenceCounted(false);
        wake.acquire();
        h.post(ticker);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 调试入口：adb am start-service 传 --es testCat hour 可立即按该类响一次
        String test = intent == null ? null : intent.getStringExtra("testCat");
        if (test != null) {
            JSONObject hit = debugHit(test);
            if (hit != null) {
                ring(hit);
                updateNoti();
            }
            return START_STICKY;
        }
        if (!Scheduler.enabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    /** 构造一个假的触发对象，走与真实报时完全相同的 ring() 路径 */
    private JSONObject debugHit(String cat) {
        try {
            JSONArray ps = Scheduler.profiles(this);
            JSONObject p = ps.optJSONObject(0);          // 工作日
            if (p == null) return null;
            JSONArray cats = p.optJSONArray("cats");
            if (cats == null) return null;
            for (int i = 0; i < cats.length(); i++) {
                JSONObject c = cats.optJSONObject(i);
                if (c == null || !cat.equals(c.optString("k"))) continue;
                JSONObject out = new JSONObject();
                out.put("cat", cat);
                out.put("ring", c.optString("ring", "bell"));
                out.put("vol", c.optInt("vol", 50));
                System.out.println("[ChimeTest] cat=" + cat
                        + " ring=" + out.optString("ring") + " vol=" + out.optInt("vol"));
                return out;
            }
        } catch (Exception ignored) { }
        return null;
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        clearBeat();
        stopRing();
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── 时间线 ──

    private void onTick() {
        if (!Scheduler.enabled(this)) { stopSelf(); return; }

        Calendar now = Calendar.getInstance();
        int dateKey = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        if (minute != lastBadgeMinute) {
            lastBadgeMinute = minute;
            updateNoti();
        }

        // 到点判定：精确到分钟，同一分钟只触发一次（内存 + 磁盘双重锁）
        if (minute != lastFiredMinute || dateKey != lastFiredDateKey) {
            if (firedOnDisk(dateKey, minute)) {
                // 进程刚重启过，这一分钟已经被上一个实例响过，绝不重复
                lastFiredMinute = minute;
                lastFiredDateKey = dateKey;
                System.out.println("[Chime] minute " + minute + " already fired (disk lock), skip");
            } else {
                JSONObject hit = Scheduler.firingCategory(this);
                if (hit != null) {
                    lastFiredMinute = minute;
                    lastFiredDateKey = dateKey;
                    markFired(dateKey, minute, hit);
                    ring(hit);
                    updateNoti();
                }
            }
        }

        // 铃声可能比 1 分钟长，到时间点强制收尾并续排
        if (ringing && System.currentTimeMillis() > ringingUntil) stopRing();
    }

    // ── 通知 ──

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CH, getString(R.string.noti_channel),
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }

    private void updateNoti() {
        long at = Scheduler.nextTrigger(this);
        String next = "—";
        if (at > 0) {
            next = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date(at));
        }
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTI_ID, buildNoti(getString(R.string.noti_waiting, next), null));
        } catch (Throwable ignored) { }
    }

    private Notification buildNoti(String text, String big) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_chime)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true);
        if (big != null) b.setStyle(new Notification.BigTextStyle().bigText(big));
        return b.build();
    }

    // ── 播放 ──

    /** 素材已统一母带为 -20 LUFS，代码侧只做「统一响度 → 用户百分比」映射，不再逐铃声补偿。 */
    private static final float MASTER_GAIN = 0.35f;   // = -9.1dB，单声道峰值 ≈ -12dBFS，不削波

    private void ring(JSONObject hit) {
        try {
            stopRing();
            if (Scheduler.vibrate(this)) vibrate();

            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int vol = hit.optInt("vol", 50);
            String r = hit.optString("ring", "bell");

            float norm = normalGain(r);

            // ── 唯一音量算法（试听端 MainActivity 与这里逐行一致）──
            // 1) 把闹铃通道顶到最大，并回读「真正生效」的值（MIUI 后台会覆盖，必须校验）
            // 2) 增益 = 母带头寸 × 用户百分比^1.6 × 通道差额补偿
            // 这样无论通道被顶到 15 还是被按回 4，最终声压都等于用户设定值。
            int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            savedVol = am.getStreamVolume(AudioManager.STREAM_ALARM);
            if (savedVol != max) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
            }
            int eff = am.getStreamVolume(AudioManager.STREAM_ALARM);

            float gain;
            if (vol < 0) {
                // 「跟随」：用户不想指定，直接按系统当前通道音量播（不做增益压缩）
                gain = norm;
            } else {
                float pct = Math.max(0f, Math.min(1f, vol / 100f));
                float deficit = (float) max / Math.max(1, eff);
                deficit = Math.min(deficit, 4f);          // 上限 +12dB，避免削波
                gain = norm * (float) Math.pow(pct, 1.6) * deficit;
            }
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            mp.setVolume(gain, gain);
            System.out.println("[ChimeTest] vol=" + vol + " eff=" + eff + "/" + max
                    + " norm=" + norm + " gain=" + gain);

            if (r.startsWith("sys:") || r.startsWith("doc:")) {
                mp.setDataSource(this, Uri.parse(r.substring(4)));
            } else {
                String file;
                switch (r) {
                    case "bell":   file = "ring2.mp3";  break;
                    case "wind":   file = "ring3.ogg";  break;
                    case "sing":   file = "ring1.mp3";  break;
                    case "d1":     file = "ring5.mp3";  break;
                    case "d2":     file = "ring6.mp3";  break;
                    case "d3":     file = "ring7.mp3";  break;
                    case "d4":     file = "ring8.mp3";  break;
                    default:       file = "ring4.ogg";  break;
                }
                AssetFileDescriptor fd = getAssets().openFd(file);
                mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                fd.close();
            }
            mp.prepare();
            // 响 30s 后停：够醒目，又不会盖住下一分钟的报时
            mp.setOnCompletionListener(m -> stopRing());
            mp.start();
            ringing = true;
            ringingUntil = System.currentTimeMillis() + RING_MS;
        } catch (Exception e) {
            stopRing();
        }
    }

    /**
     * 播放增益。素材端已在母带阶段统一到 -20 LUFS / 峰值 ≤ -3dBFS（见 tools/master_rings.sh，
     * 母版备份在 _loudness_orig/），因此这里只做两件事：
     * ① 乘统一头寸 MASTER_GAIN；② 自定义文件/系统音效响度未知，只给保守值不放大。
     */
    public static float normalGain(String ring) {
        if (ring.startsWith("sys:") || ring.startsWith("doc:")) return 0.6f;
        return MASTER_GAIN;
    }

    private void stopRing() {
        try { if (mp != null) { mp.setVolume(0f, 0f); mp.stop(); mp.release(); } } catch (Exception ignored) { }
        mp = null;
        ringing = false;
        ringingUntil = 0;
        if (savedVol >= 0) {
            try {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                am.setStreamVolume(AudioManager.STREAM_ALARM, savedVol, 0);
            } catch (Exception ignored) { }
            savedVol = -1;
        }
    }

    private void vibrate() {
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                v = vm.getDefaultVibrator();
            } else {
                v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            }
            v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) { }
    }
}

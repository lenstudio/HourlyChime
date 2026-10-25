package com.len.hourlychime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final String[] DAY_NAMES = {"一", "二", "三", "四", "五", "六", "日"};
    private static final int[] DAY_VALUES = {2, 3, 4, 5, 6, 7, 1};

    private LinearLayout list;
    private TextView bigTime, bigDate, stateLine, secTime, dayTag;
    private final android.os.Handler tick = new android.os.Handler(
            android.os.Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (secTime != null) secTime.setText(new SimpleDateFormat(
                    "HH:mm:ss", Locale.CHINA).format(new Date()));
            if (dayTag != null) dayTag.setText("今天：" + Scheduler.dayLabel(MainActivity.this,
                    java.util.Calendar.getInstance()));
            tick.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.immersive(this);
        int pad = Ui.dpi(20, this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(pad, Ui.statusBarHeight(this) + dp(12), pad, dp(24));

        // ── Header：标题居左，当前时间日期居右（TextClock 自动刷新）──
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout hl = new LinearLayout(this);
        hl.setOrientation(LinearLayout.VERTICAL);
        hl.setPadding(dp(8), 0, 0, 0);
        hl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        hl.addView(Ui.text(this, "整点报时", 26, Ui.TEXT, true));
        TextView sub = Ui.text(this, "Hourly Chime · 无常驻进程", 12, Ui.SUB, false);
        sub.setPadding(0, dp(4), 0, 0);
        hl.addView(sub);
        android.widget.ImageView logo = new android.widget.ImageView(this);
        try {
            logo.setImageBitmap(android.graphics.BitmapFactory.decodeStream(getAssets().open("icon.png")));
        } catch (Exception ignored) { }
        header.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        header.addView(hl);

        android.widget.TextClock tcDate = new android.widget.TextClock(this);
        tcDate.setFormat24Hour("MM月dd日 EEEE");
        tcDate.setTextSize(11);
        tcDate.setTextColor(Ui.SUB);
        tcDate.setGravity(Gravity.END);
        dayTag = Ui.text(this, "", 11, Ui.ACCENT, true);
        dayTag.setGravity(Gravity.END);
        secTime = Ui.text(this, "", 24, Ui.ACCENT, true);
        secTime.setGravity(Gravity.END);
        LinearLayout hr = new LinearLayout(this);
        hr.setOrientation(LinearLayout.VERTICAL);
        hr.setGravity(Gravity.END);
        hr.addView(secTime);
        hr.addView(tcDate);
        hr.addView(dayTag);
        header.addView(hr);
        root.addView(header);

        // ── 状态主卡 ──
        LinearLayout hero = Ui.card(this, Ui.CARD, 22, 22);
        hero.addView(Ui.label(this, "下 次 响 铃"));
        bigTime = Ui.text(this, "—", 40, Ui.ACCENT, true);
        bigTime.setPadding(0, dp(6), 0, 0);
        hero.addView(bigTime);
        bigDate = Ui.text(this, "", 13, Ui.SUB, false);
        bigDate.setPadding(0, dp(2), 0, 0);
        hero.addView(bigDate);
        stateLine = Ui.text(this, "", 13, Ui.SUB, false);
        stateLine.setPadding(0, dp(10), 0, 0);
        hero.addView(stateLine);
        root.addView(hero, Ui.mw(24, this));

        // ── 开关卡 ──
        LinearLayout ctrl = Ui.card(this, Ui.CARD, 18, 16);
        Switch en = new Switch(this);
        en.setText("总开关");
        en.setTextSize(16);
        en.setTextColor(Ui.TEXT);
        en.setChecked(Scheduler.enabled(this));
        en.setOnCheckedChangeListener((btn, ck) -> {
            Scheduler.setEnabled(this, ck);
            ChimeReceiver.scheduleNext(this);
            refresh();
        });
        ctrl.addView(en);
        ctrl.addView(Ui.divider(this), sep(12));
        CheckBox vb = new CheckBox(this);
        vb.setText("响铃同时振动");
        vb.setTextSize(14);
        vb.setTextColor(Ui.TEXT);
        vb.setChecked(Scheduler.vibrate(this));
        vb.setOnCheckedChangeListener((btn, ck) -> Scheduler.setVibrate(this, ck));
        ctrl.addView(vb);
        root.addView(ctrl, Ui.mw(16, this));

        // ── 方案区 ──
        LinearLayout sec = new LinearLayout(this);
        sec.setGravity(Gravity.CENTER_VERTICAL);
        View bar = new View(this);
        bar.setBackgroundColor(Ui.ACCENT);
        sec.addView(bar, new LinearLayout.LayoutParams(dp(4), dp(16)));
        sec.addView(Ui.text(this, "  报时方案", 15, Ui.TEXT, true));
        root.addView(sec, Ui.mw(26, this));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        Button add = new Button(this);
        add.setText("＋ 新建方案");
        add.setAllCaps(false);
        add.setTextColor(Ui.ACCENT);
        add.setBackground(Ui.outline(Color.TRANSPARENT, Ui.ACCENT_DIM, 14, 1.5f, this));
        add.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        root.addView(add, Ui.mw(12, this));

        setContentView(new android.widget.ScrollView(this) {{ addView(root); }});
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ChimeReceiver.scheduleNext(this);   // 自愈：打开 app 即重排下一次
        tick.post(ticker);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        tick.removeCallbacks(ticker);   // 页面不可见即停，不常驻
    }

    private void refresh() {
        boolean on = Scheduler.enabled(this);
        if (on) {
            long at = Scheduler.nextTrigger(this);
            if (at > 0) {
                Date d = new Date(at);
                bigTime.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(d));
                bigDate.setText(new SimpleDateFormat("EEEE · MM月dd日", Locale.CHINA).format(d));
            } else {
                bigTime.setText("—");
                bigDate.setText("");
            }
            stateLine.setText("运行中 · 零常驻，整点自动唤醒");
            stateLine.setTextColor(0xFF2E9E5B);
            bigTime.setTextColor(Ui.ACCENT);
        } else {
            bigTime.setText("已关闭");
            bigTime.setTextColor(Ui.SUB);
            bigDate.setText("打开总开关后恢复报时");
            stateLine.setText("");
        }

        list.removeAllViews();
        JSONArray ps = Scheduler.profiles(this);
        for (int i = 0; i < ps.length(); i++) {
            JSONObject p = ps.optJSONObject(i);
            if (p == null) continue;
            final int idx = i;
            boolean active = p.optBoolean("on", true);

            LinearLayout card = Ui.card(this, Ui.CARD, 18, 16);
            View strip = new View(this);
            strip.setBackgroundColor(active ? Ui.ACCENT : 0xFFD5DBE3);
            card.addView(strip, new LinearLayout.LayoutParams(dp(28), dp(3)));

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, 0);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            col.addView(Ui.text(this, (active ? "" : "⏸ ") + p.optString("name", "方案"), 16, Ui.TEXT, true));
            TextView det = Ui.text(this, describe(p), 12, Ui.SUB, false);
            det.setPadding(0, dp(4), 0, 0);
            col.addView(det);
            row.addView(col);
            row.addView(Ui.text(this, "›", 22, Ui.SUB, false));
            card.addView(row);

            card.setOnClickListener(v -> {
                Intent it = new Intent(this, ProfileActivity.class);
                it.putExtra("index", idx);
                startActivity(it);
            });
            list.addView(card, Ui.mw(10, this));
        }
    }

    private String describe(JSONObject p) {
        StringBuilder sb = new StringBuilder();
        int m = p.optInt("dayMode", 0);
        if (m == 1) sb.append("法定工作日  ");
        else if (m == 2) sb.append("法定休息日  ");
        JSONArray d = p.optJSONArray("days");
        if (m == 0) {
            if (d != null && d.length() > 0) {
                for (int j = 0; j < DAY_VALUES.length; j++)
                    for (int k = 0; k < d.length(); k++)
                        if (d.optInt(k) == DAY_VALUES[j]) { sb.append(DAY_NAMES[j]); break; }
            } else sb.append("每天");
        }
        JSONArray rules = p.optJSONArray("rules");
        if (rules != null) for (int k = 0; k < rules.length(); k++) sb.append("  ").append(rules.optString(k));
        return sb.toString();
    }

    private LinearLayout.LayoutParams sep(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(top);
        return lp;
    }

    private int dp(int v) { return Ui.dpi(v, this); }
}

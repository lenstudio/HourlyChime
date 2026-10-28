package com.len.hourlychime;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 单页应用：所有设置直接铺在主页，无第二层。
 *
 * 固定两组唯一方案：工作日（高阶：四节点 + 整点 + 半点） / 节假日（简单：整点 + 半点）。
 * 任何改动即时落盘并重启时间线，不需要「保存」按钮。
 */
public class MainActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    private static final String[] RING_KEYS = {"sing", "bell", "wind", "chimes", "d1", "d2", "d3", "d4", "sys"};
    private static final int[] RING_LABEL_IDS = {
            R.string.ring1_label, R.string.ring2_label, R.string.ring3_label, R.string.ring4_label,
            R.string.ring5_label, R.string.ring6_label, R.string.ring7_label, R.string.ring8_label,
            R.string.ring_sys};
    private static final String[] NODE_DEFAULT = {"9:00", "12:30", "13:30", "18:00"};
    private static final int[] NODE_LABELS = {
            R.string.node_in, R.string.node_lunch, R.string.node_pm, R.string.node_out};

    // 工作日
    private final TextView[] wdNodes = new TextView[4];
    private final List<TextView> wdHours = new ArrayList<>();
    private final List<TextView> wdHalves = new ArrayList<>();
    private Spinner wdOfficeRing, wdHourRing, wdHalfRing;
    private SeekBar wdOfficeVol, wdHourVol, wdHalfVol;
    private Switch wdHalfSw;
    private TextView wdSum;

    // 节假日
    private final List<TextView> hdHours = new ArrayList<>();
    private final List<TextView> hdHalves = new ArrayList<>();
    private Spinner hdHourRing, hdHalfRing;
    private SeekBar hdHourVol, hdHalfVol;
    private Switch hdHalfSw;
    private TextView hdSum;

    private TextView bigTime, bigDate, stateLine, secTime, dayTag, lunarView;
    private boolean loading;
    private FrostedBackdrop frostedBackdrop;

    private final android.os.Handler tick = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (secTime != null) secTime.setText(new SimpleDateFormat(
                    "HH:mm:ss", Locale.getDefault()).format(new Date()));
            if (dayTag != null) dayTag.setText(getString(R.string.today,
                    Scheduler.dayLabel(MainActivity.this, java.util.Calendar.getInstance())));
            if (lunarView != null) lunarView.setText(lunarText());
            tick.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.immersive(this);
        int pad = Ui.dpi(18, this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(20), pad, dp(32));

        // ── 头部：图标 + 大标题/实时时间 + 右侧日期农历 ──
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView logo = new android.widget.ImageView(this);
        try {
            logo.setImageBitmap(android.graphics.BitmapFactory.decodeStream(getAssets().open("icon.png")));
        } catch (Exception ignored) { }
        header.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        logo.setVisibility(View.GONE);   // 头部只要时间，不显示图标

        // 左侧：标题与实时时间同字号（时间占标题位右侧）
        LinearLayout hl = new LinearLayout(this);
        hl.setOrientation(LinearLayout.VERTICAL);
        hl.setGravity(Gravity.START);
        hl.setPadding(0, 0, 0, 0);
        hl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        hl.addView(Ui.text(this, getString(R.string.app_name), 22, Ui.TEXT, true));
        secTime = Ui.text(this, "", 38, Ui.TEXT, false);
        secTime.setFontFeatureSettings("tnum");
        secTime.setSingleLine(true);
        secTime.setAutoSizeTextTypeUniformWithConfiguration(22, 38, 1, android.util.TypedValue.COMPLEX_UNIT_SP);   // 34sp × 150%

        header.addView(hl);

        // 右侧：日期 + 农历 + 今日类型，整体字号加大
        android.widget.TextClock tcDate = new android.widget.TextClock(this);
        tcDate.setFormat24Hour(getString(R.string.date_clock));
        tcDate.setTextSize(13);
        tcDate.setTextColor(Ui.TEXT);
        tcDate.setGravity(Gravity.END);
        lunarView = Ui.text(this, lunarText(), 13, Ui.SUB, false);
        lunarView.setGravity(Gravity.END);
        dayTag = Ui.text(this, "", 13, Ui.ACCENT, true);
        dayTag.setGravity(Gravity.END);
        LinearLayout hrBox = new LinearLayout(this);
        hrBox.setOrientation(LinearLayout.VERTICAL);
        hrBox.setGravity(Gravity.END);
        hrBox.addView(tcDate);
        hrBox.addView(lunarView);
        hrBox.addView(dayTag);
        header.addView(hrBox);
        root.addView(header);

        // ── 状态卡 ──
        // 左列：下次响铃标题 / 大号时间 / 日期
        // 右列：语言胶囊 / 总开关 / 振动（与左列底部对齐）
        // Sculpted clock dial, live text remains a native accessible TextView.
        android.widget.FrameLayout dial = new android.widget.FrameLayout(this);
        dial.addView(new ClockDial(this), new android.widget.FrameLayout.LayoutParams(-1, -1));
        LinearLayout dialText = new LinearLayout(this);
        dialText.setOrientation(LinearLayout.VERTICAL);
        dialText.setGravity(Gravity.CENTER);
        secTime.setTextSize(38);
        secTime.setTextColor(Ui.ACCENT);
        secTime.setGravity(Gravity.CENTER);
        dialText.addView(secTime, new LinearLayout.LayoutParams(-1, dp(56)));
        dial.addView(dialText, new android.widget.FrameLayout.LayoutParams(-1, -1));
        root.addView(dial, new LinearLayout.LayoutParams(-1, dp(236)));
        dial.removeView(dialText);
        root.addView(dialText, new LinearLayout.LayoutParams(-1, dp(66)));
        LinearLayout hero = Ui.card(this, Ui.CARD, 24, 20);
        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);

        // 左列
        LinearLayout heroL = new LinearLayout(this);
        heroL.setOrientation(LinearLayout.VERTICAL);
        heroL.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        heroL.addView(Ui.label(this, getString(R.string.next_alarm)));
        bigTime = Ui.text(this, "—", 34, Ui.ACCENT, false);
        bigTime.setFontFeatureSettings("tnum");
        bigTime.setPadding(0, dp(4), 0, 0);
        heroL.addView(bigTime);
        bigDate = Ui.text(this, "", 12, Ui.SUB, false);
        heroL.addView(bigDate);
        stateLine = Ui.text(this, "", 12, Ui.SUB, false);
        stateLine.setPadding(0, dp(4), 0, 0);
        heroL.addView(stateLine);
        heroRow.addView(heroL);

        // 右列
        LinearLayout heroR = new LinearLayout(this);
        heroR.setOrientation(LinearLayout.VERTICAL);
        heroR.setGravity(Gravity.END);
        LinearLayout langRow = new LinearLayout(this);
        langRow.setGravity(Gravity.CENTER_VERTICAL);
        langRow.addView(new LanguageCapsule(this, code -> { Lang.set(this, code); recreate(); }),
                new LinearLayout.LayoutParams(dp(112), dp(48)));
        heroR.addView(langRow);

        LinearLayout swRow = new LinearLayout(this);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setPadding(0, dp(10), 0, 0);
        swRow.addView(Ui.text(this, getString(R.string.master_switch), 14, Ui.TEXT, false));
        Switch en = new Switch(this);
        en.setButtonTintList(Ui.tint());
        en.setThumbTintList(Ui.tint());
        en.setTrackTintList(Ui.tint());
        en.setChecked(Scheduler.enabled(this));
        en.setPadding(dp(6), 0, 0, 0);
        en.setOnCheckedChangeListener((btn, ck) -> {
            Scheduler.setEnabled(this, ck);
            if (ck) {
                ensureNotiPermission();
                ChimeService.start(this);
            } else {
                ChimeService.stop(this);
            }
            ChimeReceiver.scheduleNext(this);
            refreshHero();
        });
        swRow.addView(en);
        heroR.addView(swRow);

        android.widget.CheckBox vb = new android.widget.CheckBox(this);
        vb.setText(getString(R.string.vibrate_together));
        vb.setTextSize(12);
        vb.setButtonTintList(Ui.tint());
        vb.setTextColor(Ui.TEXT);
        vb.setChecked(Scheduler.vibrate(this));
        vb.setPadding(0, 0, 0, 0);
        vb.setOnCheckedChangeListener((btn, ck) -> Scheduler.setVibrate(this, ck));
        heroR.addView(vb);

        heroRow.addView(heroR);
        hero.addView(heroRow);

        root.addView(hero, Ui.mw(0, this));

        // ══ 工作日 ══
        root.addView(groupBar(getString(R.string.group_workday)), Ui.mw(22, this));
        LinearLayout wd = Ui.card(this, Ui.CARD, 18, 16);
        wd.addView(Ui.label(this, getString(R.string.sec_office)));
        // 两行两项：上班|午休 / 午班|下班
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, 0);
            for (int c = 0; c < 2; c++) {
                int i = r * 2 + c;
                LinearLayout cell = new LinearLayout(this);
                cell.setGravity(Gravity.CENTER_VERTICAL);
                TextView lbl = Ui.text(this, getString(NODE_LABELS[i]), 13, Ui.SUB, false);
                lbl.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                cell.addView(lbl);
                wdNodes[i] = timeChip(NODE_DEFAULT[i]);
                cell.addView(wdNodes[i]);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                clp.rightMargin = c == 0 ? dp(8) : 0;
                row.addView(cell, clp);
            }
            wd.addView(row);
        }
        wd.addView(dividerWith(12));
        LinearLayout wdTitleRow = new LinearLayout(this);
        wdTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        wdTitleRow.addView(Ui.label(this, getString(R.string.sec_clock_plain)),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        wdHalfSw = halfSwitch(wdTitleRow);
        wdHalfSw.setTextSize(13);
        wdTitleRow.addView(wdHalfSw);
        wd.addView(wdTitleRow, subM(10));
        wd.addView(dualStrip(wdHours, wdHalves), subM(6));

        // ── 铃声与音量（三类集中一处）──
        wd.addView(dividerWith(14));
        wd.addView(Ui.label(this, getString(R.string.sec_ring)), subM(10));
        wd.addView(ringRow(getString(R.string.ring_office_hint), 1));
        wd.addView(ringRow(getString(R.string.ring_hour_hint), 2));
        wd.addView(ringRow(getString(R.string.ring_half_hint), 3));
        wdSum = Ui.text(this, "", 11, Ui.SUB, false);
        wdSum.setPadding(0, dp(8), 0, 0);
        wd.addView(wdSum);
        root.addView(wd, Ui.mw(10, this));

        // ══ 节假日 ══
        root.addView(groupBar(getString(R.string.group_holiday)), Ui.mw(22, this));
        LinearLayout hd = Ui.card(this, Ui.CARD, 18, 16);
        LinearLayout hdTitleRow = new LinearLayout(this);
        hdTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        hdTitleRow.addView(Ui.label(this, getString(R.string.sec_clock_plain)),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        hdHalfSw = halfSwitch(hdTitleRow);
        hdHalfSw.setTextSize(13);
        hdTitleRow.addView(hdHalfSw);
        hd.addView(hdTitleRow);
        hd.addView(dualStrip(hdHours, hdHalves), subM(6));

        hd.addView(dividerWith(14));
        hd.addView(Ui.label(this, getString(R.string.sec_ring)), subM(10));
        hd.addView(ringRow(getString(R.string.ring_hour_hint), 4));
        hd.addView(ringRow(getString(R.string.ring_half_hint), 5));
        hdSum = Ui.text(this, "", 11, Ui.SUB, false);
        hdSum.setPadding(0, dp(8), 0, 0);
        hd.addView(hdSum);
        root.addView(hd, Ui.mw(10, this));

        // 底图固定层
        android.widget.FrameLayout fl = new android.widget.FrameLayout(this);
        fl.setBackgroundColor(Ui.BG);
        frostedBackdrop = new FrostedBackdrop(this);
        fl.addView(frostedBackdrop, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setClipToPadding(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(root);
        fl.addView(sv, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(fl);

        loadAll();
        bindChips();
        refreshHero();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (frostedBackdrop != null) frostedBackdrop.setActive(true);
        if (Scheduler.enabled(this)) ChimeService.start(this);
        ChimeReceiver.scheduleNext(this);
        tick.post(ticker);
        refreshHero();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (frostedBackdrop != null) frostedBackdrop.setActive(false);
        tick.removeCallbacks(ticker);
        stopPreview();
    }

    private void ensureNotiPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
    }

    // ── 控件构建 ──

    private LinearLayout groupBar(String title) {
        LinearLayout sec = new LinearLayout(this);
        sec.setGravity(Gravity.CENTER_VERTICAL);
        View bar = new View(this);
        bar.setBackground(Ui.fill(Ui.ACCENT, 3, this));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(4), dp(16));
        barLp.rightMargin = dp(8);
        sec.addView(bar, barLp);
        sec.addView(Ui.text(this, title, 17, Ui.TEXT, true));
        return sec;
    }

    private LinearLayout chipStrip(final List<TextView> target) {
        return chipStrip(target, target == wdHalves || target == hdHalves);
    }

    private LinearLayout chipStrip(final List<TextView> target, boolean isHalf) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        target.clear();
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            for (int col = 0; col < 12; col++) {
                final int hh = row * 12 + col;
                TextView chip = new TextView(this);
                chip.setText(isHalf ? (hh + ":30") : String.valueOf(hh));
                chip.setTextSize(isHalf ? 9f : 11f);
                chip.setGravity(Gravity.CENTER);
                chip.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                chip.setPadding(0, dp(5), 0, dp(5));
                chip.setSelected(false);
                paintChip(chip, false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.rightMargin = col == 11 ? 0 : dp(3);
                line.addView(chip, lp);
                target.add(chip);
            }
            LinearLayout.LayoutParams wlp = row == 0 ? null : subM(4);
            if (wlp == null) wrap.addView(line);
            else wrap.addView(line, wlp);
        }
        return wrap;
    }

    /**
     * 报时时刻一体化列表：按时间顺序平铺 48 个格子
     * （0:00 0:30 1:00 1:30 ... 23:00 23:30），每行 6 个 × 8 行。
     * 6 列是为拇指点选优化：单格宽度≈屏宽/6≈60dp，高度 40dp，远超 48dp 最小触控目标。
     * 整点与半点样式区分（整点深底粗体、半点浅底细体），但都在同一时间轴上。
     */
    private LinearLayout dualStrip(final List<TextView> hours, final List<TextView> halves) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        hours.clear();
        halves.clear();
        for (int row = 0; row < 8; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            for (int col = 0; col < 6; col++) {
                final int slot = row * 6 + col;          // 0..47，每格 30 分钟
                final int hh = slot / 2;
                final boolean isHalf = (slot % 2) == 1;
                TextView chip = new TextView(this);
                chip.setText(String.format(Locale.US, "%d:%02d", hh, isHalf ? 30 : 0));
                chip.setTextSize(12f);
                chip.setMinHeight(dp(48));
                chip.setFontFeatureSettings("tnum");
                Ui.press(chip);
                chip.setGravity(Gravity.CENTER);
                chip.setTypeface(isHalf ? android.graphics.Typeface.DEFAULT
                        : android.graphics.Typeface.DEFAULT_BOLD);
                chip.setPadding(0, dp(9), 0, dp(9));
                chip.setSelected(false);
                paintSlot(chip, false, isHalf);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.rightMargin = col == 5 ? 0 : dp(5);
                line.addView(chip, lp);
                if (isHalf) halves.add(chip);
                else hours.add(chip);
            }
            wrap.addView(line, subM(row == 0 ? 0 : 5));
        }
        return wrap;
    }

    /** 整点与半点使用相同的选中样式。 */
    private void paintSlot(TextView t, boolean on, boolean isHalf) {
        if (on) {
            t.setTextColor(Ui.ON_ACCENT);
            t.setBackground(Ui.gloss(false, 14, this));
        } else {
            t.setTextColor(isHalf ? Ui.SUB : Ui.TEXT);
            t.setBackground(Ui.outline(isHalf ? 0xFFF3F6FC : 0x65FFFFFF, 0xCCFFFFFF, 14, 1, this));
        }
    }

    private void paintChip(TextView t, boolean on) {
        t.setTextColor(on ? 0xFFFFFFFF : Ui.TEXT);
        t.setBackground(Ui.fill(on ? Ui.ACCENT : Ui.CARD2, 9, this));
    }

    /** 被上下班节点占用的时刻：红底白字，视觉上明确「这是上下班提醒，不是报时」 */
    private void paintLocked(TextView t) {
        t.setSelected(false);
        t.setTextColor(0xFFFFFFFF);
        t.setAlpha(1f);
        t.setBackground(Ui.gloss(true, 12, this));
    }

    private TextView timeChip(String init) {
        TextView t = new TextView(this);
        t.setText(init);
        t.setTextSize(14);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(Ui.ON_ACCENT);
        t.setBackground(Ui.gloss(true, 10, this));
        t.setPadding(dp(10), dp(10), dp(10), dp(10));
        t.setMinHeight(dp(48));
        t.setGravity(Gravity.CENTER);
        Ui.press(t);
        t.setOnClickListener(v -> pickTime(t));
        return t;
    }

    private void pickTime(final TextView target) {
        android.widget.TimePicker tp = new android.widget.TimePicker(this);
        tp.setIs24HourView(true);
        try {
            String[] hm = target.getText().toString().split(":");
            tp.setHour(Integer.parseInt(hm[0]));
            tp.setMinute(Integer.parseInt(hm[1]));
        } catch (Exception ignored) { }
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(18), dp(12), dp(18), 0);
        box.addView(tp);
        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.pick_time))
                .setView(box)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.ok_btn), (d, w) -> {
                    target.setText(String.format(Locale.US, "%d:%02d", tp.getHour(), tp.getMinute()));
                    refreshLocked();
                    saveGroup(0);
                })
                .show();
    }

    private Switch halfSwitch(LinearLayout parent) {
        final Switch sw = new Switch(this);
        sw.setText(getString(R.string.half_follow));
        sw.setTextSize(14);
        sw.setThumbTintList(Ui.tint());
        sw.setTrackTintList(Ui.tint());
        sw.setTextColor(Ui.TEXT);
        sw.setOnCheckedChangeListener((btn, ck) -> {
            if (loading) return;
            boolean wd = (sw == wdHalfSw);
            List<TextView> halves = wd ? wdHalves : hdHalves;
            // 点亮 = 一次性全选（0:30~23:30 中未被节点占用者）；关闭 = 全清
            List<TextView> hrs = wd ? wdHours : hdHours;
            for (int i = 0; i < halves.size(); i++) {
                boolean locked = wd && lockHalf(i);
                if (locked) {                       // 上下班占用的时刻：保持红底，开关无权干预
                    halves.get(i).setSelected(false);
                    paintLocked(halves.get(i));
                    continue;
                }
                // 半点依附整点：该小时整点未选中时，半点不能被点亮
                boolean hourOn = i < hrs.size() && hrs.get(i).isSelected();
                boolean on = ck && hourOn;
                halves.get(i).setSelected(on);
                paintSlot(halves.get(i), on, true);
            }
            saveGroup(wd ? 0 : 1);
        });
        return sw;
    }

    /**
     * 铃声 + 音量一体化行。
     * 下拉包含全部内置铃声（ring1~8 + 系统音效入口），切换即生成对应音量条。
     * which: 1=工作日上下班 2=工作日整点 3=工作日半点 4=节假日整点 5=节假日半点
     */
    private LinearLayout ringRow(String label, final int which) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(Ui.outline(0xFFE3E5EC, 0xA0FFFFFF, 18, 1, this));
        box.setLayoutParams(subM(8));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbl = Ui.text(this, label, 12, Ui.SUB, false);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(lbl);
        Spinner sp = new Spinner(this);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < RING_KEYS.length; i++) labels.add(getString(RING_LABEL_IDS[i]));
        labels.add(getString(R.string.ring_custom));
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        sp.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));
        row.addView(sp);
        box.addView(row);

        // 音量条
        LinearLayout volRow = new LinearLayout(this);
        volRow.setGravity(Gravity.CENTER_VERTICAL);
        volRow.setPadding(0, dp(4), 0, 0);
        final TextView volLabel = Ui.text(this, "", 11, Ui.SUB, false);
        volLabel.setMinWidth(dp(40));
        final SeekBar volBar = new SeekBar(this);
        volBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        volBar.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        volBar.setMax(100);
        volBar.setProgress(50);
        volBar.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        volRow.addView(volLabel);
        volRow.addView(volBar);

        final Button playBtn = new Button(this);
        playBtn.setText(getString(R.string.preview));
        playBtn.setAllCaps(false);
        playBtn.setTextSize(12);
        playBtn.setTextColor(Ui.ACCENT);
        playBtn.setPadding(dp(10), 0, dp(10), 0);
        playBtn.setMinWidth(0);
        playBtn.setMinimumWidth(0);
        playBtn.setBackground(Ui.outline(0x00000000, Ui.ACCENT_DIM, 9, 1.1f, this));
        playBtn.setOnClickListener(v -> togglePreview(which));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(48));
        plp.leftMargin = dp(8);
        volRow.addView(playBtn, plp);
        box.addView(volRow);

        final Runnable updLabel = () -> volLabel.setText(
                volBar.getProgress() == 0 ? getString(R.string.volume_follow)
                        : getString(R.string.volume_pct, volBar.getProgress()));
        updLabel.run();

        volBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int pr, boolean user) { updLabel.run(); }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { saveGroup(which <= 3 ? 0 : 1); }
        });
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> a, View v, int pos, long id) {
                if (loading) return;
                saveGroup(which <= 3 ? 0 : 1);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> a) { }
        });

        switch (which) {
            case 1: wdOfficeRing = sp; wdOfficeVol = volBar; break;
            case 2: wdHourRing = sp;   wdHourVol = volBar; break;
            case 3: wdHalfRing = sp;   wdHalfVol = volBar; break;
            case 4: hdHourRing = sp;   hdHourVol = volBar; break;
            default: hdHalfRing = sp;  hdHalfVol = volBar; break;
        }
        return box;
    }

    // ── 试听 ──

    private android.media.MediaPlayer pv;
    private int pvSaveVol = -1;

    /** 按类别试听：取该类的铃声 key 与音量，播放上限 8 秒 */
    private void togglePreview(int which) {
        if (pv != null) { stopPreview(); return; }
        try {
            String ring;
            int vol;
            switch (which) {
                case 1:
                    ring = ringAt(wdOfficeRing); vol = wdOfficeVol.getProgress(); break;
                case 2:
                    ring = ringAt(wdHourRing); vol = wdHourVol.getProgress(); break;
                case 3:
                    ring = ringAt(wdHalfRing); vol = wdHalfVol.getProgress(); break;
                case 4:
                    ring = ringAt(hdHourRing); vol = hdHourVol.getProgress(); break;
                default:
                    ring = ringAt(hdHalfRing); vol = hdHalfVol.getProgress(); break;
            }
            // ── 与 ChimeService.ring() 逐行一致的音量算法 ──
            android.media.AudioManager am =
                    (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM);
            pvSaveVol = am.getStreamVolume(android.media.AudioManager.STREAM_ALARM);
            am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, max, 0);
            int eff = am.getStreamVolume(android.media.AudioManager.STREAM_ALARM);

            float gain;
            if (vol < 0) {
                gain = ChimeService.normalGain(ring);
            } else {
                float pct = Math.max(0f, Math.min(1f, vol / 100f));
                float deficit = (float) max / Math.max(1, eff);
                deficit = Math.min(deficit, 4f);
                gain = ChimeService.normalGain(ring) * (float) Math.pow(pct, 1.6) * deficit;
            }

            pv = new android.media.MediaPlayer();
            pv.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            pv.setVolume(gain, gain);
            if (ring.startsWith("sys:") || ring.startsWith("doc:")) {
                pv.setDataSource(this, android.net.Uri.parse(ring.substring(4)));
            } else {
                android.content.res.AssetFileDescriptor fd =
                        getAssets().openFd(ringFile(ring));
                pv.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                fd.close();
            }
            pv.prepare();
            pv.start();
            // 最长试听 8 秒，避免长铃声没完没了
            tick.postDelayed(this::stopPreview, 8000);
        } catch (Exception e) {
            stopPreview();
        }
    }

    private void stopPreview() {
        try { if (pv != null) { pv.release(); pv = null; } } catch (Exception ignored) { }
        if (pvSaveVol >= 0) {
            try {
                android.media.AudioManager am =
                        (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, pvSaveVol, 0);
            } catch (Exception ignored) { }
            pvSaveVol = -1;
        }
    }

    /** 铃声 key -> assets 文件名 */
    private static String ringFile(String key) {
        switch (key) {
            case "bell": return "ring2.mp3";
            case "wind": return "ring3.ogg";
            case "sing": return "ring1.mp3";
            case "d1":   return "ring5.mp3";
            case "d2":   return "ring6.mp3";
            case "d3":   return "ring7.mp3";
            case "d4":   return "ring8.mp3";
            default:     return "ring4.ogg";
        }
    }

    // ── 交互 ──

    private void bindChips() {
        for (int i = 0; i < wdHours.size(); i++) {
            final int hh = i;
            wdHours.get(i).setOnClickListener(v -> {
                if (lockHour(hh)) return;
                boolean on = !v.isSelected();
                v.setSelected(on);
                paintChip((TextView) v, on);
                if (hh < wdHalves.size() && !lockHalf(hh)) {
                    boolean ho = on && wdHalfSw.isChecked();
                    wdHalves.get(hh).setSelected(ho);
                    paintSlot(wdHalves.get(hh), ho, true);
                }
                saveGroup(0);
            });
        }
        for (int i = 0; i < wdHalves.size(); i++) {
            final int hh = i;
            wdHalves.get(i).setOnClickListener(v -> {
                if (lockHalf(hh)) return;
                if (!wdHours.get(hh).isSelected()) return;   // 整点未选 -> 半点不可选
                boolean on = !v.isSelected();
                v.setSelected(on);
                paintSlot((TextView) v, on, true);
                saveGroup(0);
            });
        }
        for (int i = 0; i < hdHours.size(); i++) {
            final int hh = i;
            hdHours.get(i).setOnClickListener(v -> {
                boolean on = !v.isSelected();
                v.setSelected(on);
                paintChip((TextView) v, on);
                if (hh < hdHalves.size()) {
                    boolean ho = on && hdHalfSw.isChecked();
                    hdHalves.get(hh).setSelected(ho);
                    paintSlot(hdHalves.get(hh), ho, true);
                }
                saveGroup(1);
            });
        }
        for (int i = 0; i < hdHalves.size(); i++) {
            final int hh = i;
            hdHalves.get(i).setOnClickListener(v -> {
                if (!hdHours.get(hh).isSelected()) return;   // 整点未选 -> 半点不可选
                boolean on = !v.isSelected();
                v.setSelected(on);
                paintSlot((TextView) v, on, true);
                saveGroup(1);
            });
        }
    }

    private boolean lockHour(int h) {
        for (TextView f : wdNodes) {
            try { if (parseMin(f.getText().toString()) == h * 60) return true; }
            catch (Exception ignored) { }
        }
        return false;
    }

    private boolean lockHalf(int h) {
        for (TextView f : wdNodes) {
            try { if (parseMin(f.getText().toString()) == h * 60 + 30) return true; }
            catch (Exception ignored) { }
        }
        return false;
    }

    private void refreshLocked() {
        for (int i = 0; i < wdHours.size(); i++) {
            TextView t = wdHours.get(i);
            if (lockHour(i)) {
                paintLocked(t);
            } else {
                t.setAlpha(1f);
                paintChip(t, t.isSelected());
            }
            if (i >= wdHalves.size()) continue;
            TextView hf = wdHalves.get(i);
            if (lockHalf(i)) {
                paintLocked(hf);
            } else if (!t.isSelected()) {
                hf.setSelected(false);
                hf.setTextColor(Ui.SUB);
                hf.setAlpha(0.38f);
                hf.setBackground(Ui.fill(0xFFF4F6F9, 10, this));
            } else {
                hf.setAlpha(1f);
                paintSlot(hf, hf.isSelected(), true);
            }
        }
    }

    // ── 数据 ──

    private void loadAll() {
        loading = true;
        JSONArray ps = Scheduler.profiles(this);
        for (int i = 0; i < 4; i++) wdNodes[i].setText(NODE_DEFAULT[i]);
        fillFrom(ps.optJSONObject(0), 0);
        fillFrom(ps.optJSONObject(1), 1);
        loading = false;
        refreshLocked();
        updSummaries();
    }

    private void fillFrom(JSONObject p, int g) {
        List<TextView> hours = g == 0 ? wdHours : hdHours;
        List<TextView> halves = g == 0 ? wdHalves : hdHalves;
        if (p == null) return;
        JSONArray cats = p.optJSONArray("cats");
        if (cats == null) return;
        for (int i = 0; i < cats.length(); i++) {
            JSONObject c = cats.optJSONObject(i);
            if (c == null) continue;
            String k = c.optString("k");
            JSONArray arr = c.optJSONArray("m");
            if (arr == null) continue;
            if ("office".equals(k)) {
                for (int j = 0; j < Math.min(4, arr.length()); j++)
                    wdNodes[j].setText(Scheduler.fmtMin(arr.optInt(j, 0)));
                wdOfficeRing.setSelection(indexOfRing(c.optString("ring", "sing")));
                wdOfficeVol.setProgress(normVol(c.optInt("vol", 50)));
            } else if ("hour".equals(k)) {
                for (int j = 0; j < arr.length(); j++) {
                    int h = arr.optInt(j) / 60;
                    if (h >= 0 && h < hours.size()) {
                        if (g == 0 && lockHour(h)) continue;
                        hours.get(h).setSelected(true);
                        paintChip(hours.get(h), true);
                    }
                }
                Spinner sp = g == 0 ? wdHourRing : hdHourRing;
                sp.setSelection(indexOfRing(c.optString("ring", "bell")));
                (g == 0 ? wdHourVol : hdHourVol).setProgress(normVol(c.optInt("vol", 50)));
            } else if ("half".equals(k)) {
                boolean any = false;
                for (int j = 0; j < arr.length(); j++) {
                    int h = arr.optInt(j) / 60;
                    if (h >= 0 && h < halves.size()) {
                        if (g == 0 && lockHalf(h)) continue;   // 锁定项不计入
                        halves.get(h).setSelected(true);
                        paintSlot(halves.get(h), true, true);
                        any = true;
                    }
                }
                if (g == 0) wdHalfSw.setChecked(any); else hdHalfSw.setChecked(any);
                Spinner sp = g == 0 ? wdHalfRing : hdHalfRing;
                sp.setSelection(indexOfRing(c.optString("ring", "wind")));
                (g == 0 ? wdHalfVol : hdHalfVol).setProgress(normVol(c.optInt("vol", 50)));
            }
        }
    }

    /** 存量 -1（跟随系统）在界面上显示为 0 */
    private static int normVol(int v) {
        return v < 0 ? 0 : Math.min(100, v);
    }

    private static int indexOfRing(String key) {
        for (int i = 0; i < RING_KEYS.length; i++) if (RING_KEYS[i].equals(key)) return i;
        return 0;
    }

    /** 把界面上某组的状态写回存储并重启时间线 */
    private void saveGroup(int g) {
        if (loading) return;
        try {
            JSONArray ps = Scheduler.profiles(this);
            JSONObject p = ps.optJSONObject(g);
            if (p == null) p = new JSONObject();

            TreeSet<Integer> office = new TreeSet<>();
            TreeSet<Integer> hours = new TreeSet<>();
            TreeSet<Integer> halves = new TreeSet<>();
            String rOffice = RING_KEYS[0], rHour = RING_KEYS[1], rHalf = RING_KEYS[2];
            int vOffice = 80, vHour = 80, vHalf = 80;

            if (g == 0) {
                for (TextView f : wdNodes) office.add(parseMin(f.getText().toString()));
                for (int i = 0; i < wdHours.size(); i++)
                    if (wdHours.get(i).isSelected() && !lockHour(i)) hours.add(i * 60);
                for (int i = 0; i < wdHalves.size(); i++)
                    if (wdHalves.get(i).isSelected() && !lockHalf(i)
                            && i < wdHours.size() && wdHours.get(i).isSelected())
                        halves.add(i * 60 + 30);
                rOffice = ringAt(wdOfficeRing); rHour = ringAt(wdHourRing); rHalf = ringAt(wdHalfRing);
                vOffice = wdOfficeVol.getProgress(); vHour = wdHourVol.getProgress(); vHalf = wdHalfVol.getProgress();
            } else {
                for (int i = 0; i < hdHours.size(); i++)
                    if (hdHours.get(i).isSelected()) hours.add(i * 60);
                for (int i = 0; i < hdHalves.size(); i++)
                    if (hdHalves.get(i).isSelected()
                            && i < hdHours.size() && hdHours.get(i).isSelected())
                        halves.add(i * 60 + 30);
                rHour = ringAt(hdHourRing); rHalf = ringAt(hdHalfRing);
                vHour = hdHourVol.getProgress(); vHalf = hdHalfVol.getProgress();
            }

            JSONArray rules = new JSONArray();
            for (String r : Scheduler.compact(office)) rules.put(r);
            for (String r : Scheduler.compact(hours)) rules.put(r);
            for (String r : Scheduler.compact(halves)) rules.put(r);

            JSONArray cats = new JSONArray();
            if (!office.isEmpty()) cats.put(cat("office", office, rOffice, vOffice));
            cats.put(cat("hour", hours, rHour, vHour));
            cats.put(cat("half", halves, rHalf, vHalf));

            p.put("name", getString(g == 0 ? R.string.group_workday : R.string.group_holiday));
            p.put("group", g);
            p.put("mode", g == 0 ? 1 : 0);
            p.put("dayMode", g == 0 ? 1 : 2);
            p.put("days", new JSONArray(g == 0 ? "[2,3,4,5,6]" : "[1,7]"));
            p.put("rules", rules);
            p.put("cats", cats);
            p.put("ring", rHour);
            p.put("vol", 50);
            p.put("on", true);

            ps.put(g, p);
            Scheduler.saveProfiles(this, ps);
            if (Scheduler.enabled(this)) ChimeService.start(this);
            ChimeReceiver.scheduleNext(this);
            updSummaries();
            refreshHero();
        } catch (Exception ignored) { }
    }

    private static JSONObject cat(String k, TreeSet<Integer> set, String ring, int vol) throws Exception {
        JSONArray arr = new JSONArray();
        for (Integer m : set) arr.put(m);
        return new JSONObject().put("k", k).put("m", arr).put("ring", ring)
                .put("vol", vol <= 0 ? -1 : vol);
    }

    /** 下拉位置 -> 铃声 key；末位是「自定义」占位，回退到默认 */
    private static String ringAt(Spinner sp) {
        int pos = sp == null ? -1 : sp.getSelectedItemPosition();
        if (pos < 0 || pos >= RING_KEYS.length) return RING_KEYS[1];
        return RING_KEYS[pos];
    }

    /** 音量 0 表示跟随系统：界面显示 0，存储写 -1 */
    private static int volAt(SeekBar bar) {
        return bar == null ? 80 : bar.getProgress();
    }

    private void updSummaries() {
        if (wdSum != null) {
            int ho = 0, hf = 0;
            for (int i = 0; i < wdHours.size(); i++) if (wdHours.get(i).isSelected() && !lockHour(i)) ho++;
            for (int i = 0; i < wdHalves.size(); i++)
                if (wdHalves.get(i).isSelected() && !lockHalf(i)
                        && i < wdHours.size() && wdHours.get(i).isSelected()) hf++;
            wdSum.setText(getString(R.string.group_summary, ho, hf));
        }
        if (hdSum != null) {
            int ho = 0, hf = 0;
            for (TextView t : hdHours) if (t.isSelected()) ho++;
            for (TextView t : hdHalves) if (t.isSelected()) hf++;
            hdSum.setText(getString(R.string.group_summary, ho, hf));
        }
    }

    private void refreshHero() {
        boolean on = Scheduler.enabled(this);
        if (on) {
            long at = Scheduler.nextTrigger(this);
            if (at > 0) {
                Date d = new Date(at);
                bigTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d));
                bigDate.setText(new SimpleDateFormat(getString(R.string.date_hero),
                        Locale.getDefault()).format(d));
            } else {
                bigTime.setText("—");
                bigDate.setText("");
            }
            stateLine.setText(getString(R.string.state_running_plain));
            stateLine.setTextColor(0xFF2E9E5B);
            bigTime.setTextColor(Ui.ACCENT);
        } else {
            bigTime.setText(getString(R.string.state_off));
            bigTime.setTextColor(Ui.SUB);
            bigDate.setText("");
            stateLine.setText(getString(R.string.state_off_hint));
        }
    }

    private View dividerWith(int top) {
        View v = Ui.divider(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(top);
        v.setLayoutParams(lp);
        return v;
    }

    private LinearLayout.LayoutParams subM(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        return lp;
    }

    /**
     * 农历日期。系统 Calendar 只有公历，这里用 ICU 的 ChineseCalendar（android.icu.util）
     * 直接取农历月/日；失败时退回显示公历，不显示空串。
     */
    private String lunarText() {
        try {
            android.icu.util.ChineseCalendar cc = new android.icu.util.ChineseCalendar();
            cc.setTimeInMillis(System.currentTimeMillis());
            boolean leap = cc.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH) == 1;
            int m = cc.get(android.icu.util.ChineseCalendar.MONTH) + 1;
            int d = cc.get(android.icu.util.ChineseCalendar.DAY_OF_MONTH);
            String[] nums = {"正","二","三","四","五","六","七","八","九","十","十一","十二"};
            String ms = (m >= 1 && m <= 12) ? nums[m - 1] : String.valueOf(m);
            String ds;
            if (d <= 10) ds = "初" + "一二三四五六七八九十".charAt(d - 1);
            else if (d < 20) ds = "十" + "一二三四五六七八九".charAt(d - 11);
            else if (d == 20) ds = "二十";
            else if (d < 30) ds = "廿" + "一二三四五六七八九".charAt(d - 21);
            else ds = "三十";
            return getString(R.string.lunar_prefix) + (leap ? getString(R.string.lunar_leap) : "")
                    + ms + "月" + ds;
        } catch (Throwable t) {
            return "";
        }
    }

    private static int parseMin(String s) {
        String[] hm = s.trim().split(":");
        if (hm.length != 2) throw new IllegalArgumentException(s);
        int hh = Integer.parseInt(hm[0].trim());
        int mm = Integer.parseInt(hm[1].trim());
        if (hh < 0 || hh > 23 || mm < 0 || mm > 59) throw new IllegalArgumentException(s);
        return hh * 60 + mm;
    }

    private int dp(int v) { return Ui.dpi(v, this); }
}

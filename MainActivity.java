package com.len.hourlychime;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.DragEvent;
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

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    private static final int[] DAY_VALUES = {2, 3, 4, 5, 6, 7, 1};
    private String[] DAY_NAMES;

    private String[] dayNames() {
        if (DAY_NAMES == null) DAY_NAMES = getResources().getStringArray(R.array.day_short);
        return DAY_NAMES;
    }

    private LinearLayout list;
    private TextView bigTime, bigDate, stateLine, secTime, dayTag;
    private final android.os.Handler tick = new android.os.Handler(
            android.os.Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (secTime != null) secTime.setText(new SimpleDateFormat(
                    "HH:mm:ss", Locale.getDefault()).format(new Date()));
            if (dayTag != null) dayTag.setText(getString(R.string.today,
                    Scheduler.dayLabel(MainActivity.this,
                    java.util.Calendar.getInstance())));
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
        root.setPadding(pad, Ui.statusBarHeight(this) + dp(12), pad, dp(24));

        // ── Header：标题居左，当前时间日期居右（TextClock 自动刷新）──
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout hl = new LinearLayout(this);
        hl.setOrientation(LinearLayout.VERTICAL);
        hl.setPadding(dp(8), 0, 0, 0);
        hl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        hl.addView(Ui.text(this, getString(R.string.app_name), 26, Ui.TEXT, true));
        android.widget.ImageView logo = new android.widget.ImageView(this);
        try {
            logo.setImageBitmap(android.graphics.BitmapFactory.decodeStream(getAssets().open("icon.png")));
        } catch (Exception ignored) { }
        header.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        header.addView(hl);

        android.widget.TextClock tcDate = new android.widget.TextClock(this);
        tcDate.setFormat24Hour(getString(R.string.date_clock));
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
        LinearLayout heroHead = new LinearLayout(this);
        heroHead.setGravity(Gravity.CENTER_VERTICAL);
        heroHead.addView(Ui.label(this, getString(R.string.next_alarm)),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        heroHead.addView(langChip("EN", "en"));
        heroHead.addView(langChip("中文", "zh"));
        hero.addView(heroHead);
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
        en.setText(getString(R.string.master_switch));
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
        vb.setText(getString(R.string.vibrate_together));
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
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(4), dp(16));
        barLp.rightMargin = dp(8);
        sec.addView(bar, barLp);
        sec.addView(Ui.text(this, getString(R.string.section_profiles), 15, Ui.TEXT, true));
        View hint = Ui.text(this, getString(R.string.hint_drag), 10, Ui.SUB, false);
        hint.setPadding(dp(8), dp(2), 0, 0);
        LinearLayout sh = new LinearLayout(this);
        sh.setOrientation(LinearLayout.VERTICAL);
        sh.addView(sec);
        sh.addView(hint);
        root.addView(sh, Ui.mw(26, this));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        list.setOnDragListener(dragReorder());

        Button add = new Button(this);
        add.setText(getString(R.string.new_profile));
        add.setAllCaps(false);
        add.setTextColor(Ui.ACCENT);
        add.setBackground(Ui.outline(Color.TRANSPARENT, Ui.ACCENT_DIM, 14, 1.5f, this));
        add.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        root.addView(add, Ui.mw(12, this));

        // 底图固定层：bg 不随内容滚动
        android.widget.FrameLayout fl = new android.widget.FrameLayout(this);
        fl.setBackground(Ui.screenBg(this));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(root);
        fl.addView(sv, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(fl);
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

    /** 语言切换小胶囊（柔和配色）：当前语言高亮，点击切换并重建页面 */
    private TextView langChip(String label, final String code) {
        final boolean active = Lang.pref(this).equals(code);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11);
        t.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setTextColor(active ? Ui.ACCENT : Ui.SUB);
        t.setBackground(Ui.fill(active ? 0xFFE1EAFC : 0xFFF0F3F8, 12, this));
        t.setPadding(dp(10), dp(3), dp(10), dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> {
            if (!Lang.pref(this).equals(code)) {
                Lang.set(this, code);
                recreate();
            }
        });
        return t;
    }

    private void refresh() {
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
            stateLine.setText(getString(R.string.state_running));
            stateLine.setTextColor(0xFF2E9E5B);
            bigTime.setTextColor(Ui.ACCENT);
        } else {
            bigTime.setText(getString(R.string.state_off));
            bigTime.setTextColor(Ui.SUB);
            bigDate.setText(getString(R.string.state_off_hint));
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

            col.addView(Ui.text(this, (active ? "" : "⏸ ") +
                    (p.optString("name", "").isEmpty() ? getString(R.string.profile_default)
                            : p.optString("name")), 16, Ui.TEXT, true));
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
            card.setTag(idx);
            card.setOnLongClickListener(v -> {
                v.startDragAndDrop(ClipData.newPlainText("idx", String.valueOf(idx)),
                        new android.view.View.DragShadowBuilder(v), idx, 0);
                return true;
            });
            list.addView(card, Ui.mw(10, this));
        }
    }

    private String describe(JSONObject p) {
        StringBuilder sb = new StringBuilder();
        int m = p.optInt("dayMode", 0);
        if (m == 1) sb.append(getString(R.string.legal_workday)).append("  ");
        else if (m == 2) sb.append(getString(R.string.legal_restday)).append("  ");
        JSONArray d = p.optJSONArray("days");
        if (m == 0) {
            if (d != null && d.length() > 0) {
                String[] names = dayNames();
                for (int j = 0; j < DAY_VALUES.length; j++)
                    for (int k = 0; k < d.length(); k++)
                        if (d.optInt(k) == DAY_VALUES[j]) { sb.append(names[j]); break; }
            } else sb.append(getString(R.string.every_day));
        }
        JSONArray rules = p.optJSONArray("rules");
        if (rules != null) for (int k = 0; k < rules.length(); k++) sb.append("  ").append(rules.optString(k));
        return sb.toString();
    }

    /** 拖动排序：长按卡片拖到目标位置，松手即持久化（数组顺序 = 触发优先级） */
    private android.view.View.OnDragListener dragReorder() {
        return (v, ev) -> {
            switch (ev.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return ev.getLocalState() instanceof Integer;
                case DragEvent.ACTION_DRAG_LOCATION: {
                    int dragIdx = (Integer) ev.getLocalState();
                    View dragged = childWithTag(dragIdx);
                    if (dragged == null) return true;
                    float y = ev.getY();
                    int counter = 0, target = counter, cur = list.indexOfChild(dragged);
                    boolean placed = false;
                    for (int i = 0; i < list.getChildCount(); i++) {
                        View ch = list.getChildAt(i);
                        if ((Integer) ch.getTag() == dragIdx) continue;
                        if (!placed && y < ch.getY() + ch.getHeight() / 2f) {
                            target = counter;
                            placed = true;
                        }
                        counter++;
                    }
                    if (!placed) target = counter;
                    if (target != cur) {
                        list.removeView(dragged);
                        list.addView(dragged, Math.min(target, list.getChildCount()));
                        list.requestLayout();
                    }
                    return true;
                }
                case DragEvent.ACTION_DROP:
                case DragEvent.ACTION_DRAG_ENDED:
                    persistOrder();
                    return true;
                default:
                    return true;
            }
        };
    }

    private View childWithTag(int tag) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View ch = list.getChildAt(i);
            if (ch.getTag() instanceof Integer && (Integer) ch.getTag() == tag) return ch;
        }
        return null;
    }

    /** 按当前卡片顺序重排 profiles 数组并保存 */
    private void persistOrder() {
        JSONArray ps = Scheduler.profiles(this);
        JSONArray out = new JSONArray();
        for (int i = 0; i < list.getChildCount(); i++) {
            Integer tag = (Integer) list.getChildAt(i).getTag();
            if (tag != null && tag >= 0 && tag < ps.length()) out.put(ps.optJSONObject(tag));
        }
        if (out.length() != ps.length()) return;
        Scheduler.saveProfiles(this, out);
        ChimeReceiver.scheduleNext(this);
        refresh();
    }

    private LinearLayout.LayoutParams sep(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(top);
        return lp;
    }

    private int dp(int v) { return Ui.dpi(v, this); }
}

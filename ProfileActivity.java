package com.len.hourlychime;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class ProfileActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    private static final int[] DAY_VALUES = {2, 3, 4, 5, 6, 7, 1};
    private static final String[] RING_KEYS = {"sing", "bell", "wind", "chimes", "d1", "d2", "d3", "d4", "sys"};
    private static final int[] RING_LABEL_IDS = {
            R.string.ring1_label, R.string.ring2_label, R.string.ring3_label, R.string.ring4_label,
            R.string.ring5_label, R.string.ring6_label, R.string.ring7_label, R.string.ring8_label,
            R.string.ring_sys};

    private String[] dayNames() {
        return getResources().getStringArray(R.array.day_full);
    }

    private EditText name;
    private final List<CheckBox> dayBoxes = new ArrayList<>();
    private final CheckBox[] slots = new CheckBox[48];
    private TextView rulePreview;
    private Spinner modeSpinner, srcSpinner, listSpinner;
    private final List<String> listLabels = new ArrayList<>();
    private final List<String> listValues = new ArrayList<>();
    private String customUri;
    private Button fileBtn, delBtn;
    private SeekBar vol;
    private TextView volLabel;
    private int index = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.immersive(this);
        index = getIntent().getIntExtra("index", -1);
        JSONArray ps = Scheduler.profiles(this);
        JSONObject p = (index >= 0 && index < ps.length()) ? ps.optJSONObject(index) : null;

        int pad = Ui.dpi(20, this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, Ui.statusBarHeight(this) + dp(12), pad, dp(24));

        // 返回 + 标题：左上角小箭头
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "‹", 30, Ui.TEXT, false);
        back.setPadding(dp(2), 0, dp(18), 0);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        head.addView(Ui.text(this, index < 0 ? getString(R.string.create_profile)
                : getString(R.string.edit_profile), 22, Ui.TEXT, true));
        root.addView(head);

        // 名称
        root.addView(Ui.label(this, getString(R.string.name_label)), Ui.mw(22, this));
        name = input();
        name.setHint(getString(R.string.name_hint));
        if (p != null) name.setText(p.optString("name", ""));
        root.addView(name, Ui.mw(8, this));

        // 生效日模式 + 星期
        root.addView(Ui.label(this, getString(R.string.effective_days)), Ui.mw(22, this));
        String[] MODES = {getString(R.string.mode_by_week), getString(R.string.mode_legal_work),
                getString(R.string.mode_legal_rest)};
        LinearLayout modeCard = Ui.card(this, Ui.CARD, 14, 8);
        modeSpinner = new Spinner(this);
        modeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, MODES));
        int curMode = p != null ? p.optInt("dayMode", 0) : 0;
        modeSpinner.setSelection(curMode);
        modeCard.addView(modeSpinner);
        root.addView(modeCard, Ui.mw(8, this));

        HorizontalScrollView dh = new HorizontalScrollView(this);
        dh.setHorizontalScrollBarEnabled(false);
        LinearLayout days = new LinearLayout(this);
        JSONArray pd = p != null ? p.optJSONArray("days") : null;
        String[] dayFull = dayNames();
        for (int i = 0; i < 7; i++) {
            CheckBox c = new CheckBox(this);
            c.setText(dayFull[i]);
            c.setTextSize(13);
            c.setTextColor(Ui.TEXT);
            boolean checked = pd == null || pd.length() == 0;
            if (pd != null) for (int k = 0; k < pd.length(); k++)
                if (pd.optInt(k) == DAY_VALUES[i]) { checked = true; break; }
            c.setChecked(checked);
            c.setEnabled(curMode == 0);
            c.setAlpha(curMode == 0 ? 1f : 0.4f);
            days.addView(c);
            dayBoxes.add(c);
        }
        dh.addView(days);
        root.addView(dh, Ui.mw(4, this));
        modeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> a, View v2, int pos, long id) {
                boolean byWeek = pos == 0;
                for (CheckBox cb : dayBoxes) { cb.setEnabled(byWeek); cb.setAlpha(byWeek ? 1f : 0.4f); }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> a) { }
        });

        // 时段规则：48 格半小时打勾网格
        root.addView(Ui.label(this, getString(R.string.time_rules)), Ui.mw(22, this));
        LinearLayout grid = new LinearLayout(this);
        for (int col = 0; col < 3; col++) {
            LinearLayout colBox = new LinearLayout(this);
            colBox.setOrientation(LinearLayout.VERTICAL);
            for (int r2 = 0; r2 < 16; r2++) {
                int idx = col * 16 + r2;
                int mm = idx * 30;
                CheckBox cb = new CheckBox(this);
                cb.setTextSize(12);
                cb.setTextColor(Ui.TEXT);
                cb.setText(fmtMin(mm));
                cb.setOnCheckedChangeListener((bb, ck) -> updPreview());
                colBox.addView(cb);
                slots[idx] = cb;
            }
            grid.addView(colBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(grid, Ui.mw(8, this));
        rulePreview = Ui.text(this, "", 12, Ui.ACCENT, false);
        root.addView(rulePreview, Ui.mw(8, this));

        // 铃声：单一下拉（内置 + 系统音效 + 自定义）
        root.addView(Ui.label(this, getString(R.string.ringtone)), Ui.mw(22, this));
        LinearLayout ringCard = Ui.card(this, Ui.CARD, 14, 10);
        listSpinner = new Spinner(this);
        ringCard.addView(listSpinner);
        fileBtn = ghostButton(getString(R.string.pick_audio));
        fileBtn.setOnClickListener(v -> pickFile());
        fileBtn.setVisibility(View.GONE);
        ringCard.addView(fileBtn, Ui.mw(6, this));
        Button prev = ghostButton(getString(R.string.preview));
        prev.setOnClickListener(vx -> togglePreview(prev));
        ringCard.addView(prev, Ui.mw(6, this));
        root.addView(ringCard, Ui.mw(8, this));

        String cur = p != null ? p.optString("ring", "sing") : "sing";
        if (cur.startsWith("doc:")) customUri = cur.substring(4);
        listSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> a, View v2, int pos, long id) {
                fileBtn.setVisibility(pos >= 0 && pos < listValues.size() && "custom".equals(listValues.get(pos))
                        ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> a) { }
        });
        rebuildList();
        for (int i = 0; i < listValues.size(); i++) {
            String v = listValues.get(i);
            if (v.equals(cur) || ("doc:" + (customUri == null ? "" : customUri)).equals(cur) && v.startsWith("doc:"))
                listSpinner.setSelection(i);
        }

        // 音量
        volLabel = Ui.text(this, "", 13, Ui.TEXT, false);
        root.addView(volLabel, Ui.mw(18, this));
        vol = new SeekBar(this);
        vol.setMax(100);
        int v = p != null ? p.optInt("vol", 80) : 80;
        vol.setProgress(v < 0 ? 0 : v);
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean u) { updVol(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        updVol();
        root.addView(vol, Ui.mw(4, this));

        // 删除（两步确认）+ 保存
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        delBtn = new Button(this);
        delBtn.setText(getString(R.string.delete));
        delBtn.setTextSize(15);
        delBtn.setTextColor(0xFFFFFFFF);
        delBtn.setBackground(Ui.fill(0xFFE5484D, 14, this));
        delBtn.setVisibility(index >= 0 ? View.VISIBLE : View.GONE);
        delBtn.setOnClickListener(vv -> delConfirm());
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        dlp.rightMargin = dp(12);
        actions.addView(delBtn, dlp);
        Button save = new Button(this);
        save.setText(getString(R.string.save));
        save.setTextSize(16);
        save.setTextColor(Ui.ON_ACCENT);
        save.setBackground(Ui.fill(Ui.ACCENT, 14, this));
        save.setOnClickListener(vx -> save());
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(46), 2f));
        actions.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams ap = Ui.mw(28, this);
        ap.leftMargin = 0;
        root.addView(actions, ap);

        // 底图固定层：bg 不随内容滚动
        android.widget.FrameLayout fl = new android.widget.FrameLayout(this);
        fl.setBackground(Ui.screenBg(this));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(root);
        fl.addView(sv, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(fl);

        // 载入已有规则 -> 点亮对应格子
        JSONArray pr = p != null ? p.optJSONArray("rules") : null;
        java.util.HashSet<Integer> marks = new java.util.HashSet<>();
        if (pr != null) for (int i = 0; i < pr.length(); i++) {
            try { for (int m2 : Scheduler.expandRule(pr.optString(i, ""))) marks.add(m2 / 30); }
            catch (Exception ignored) { }
        }
        for (int i = 0; i < 48; i++) slots[i].setChecked(marks.contains(i));
        updPreview();
    }

    // ── 控件工厂 ──

    private EditText input() {
        EditText e = new EditText(this);
        e.setTextSize(15);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.SUB);
        e.setBackground(Ui.fill(Ui.CARD2, 12, this));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setSingleLine(true);
        return e;
    }

    private Button ghostButton(String s) {
        Button btn = new Button(this);
        btn.setText(s);
        btn.setAllCaps(false);
        btn.setTextSize(14);
        btn.setTextColor(Ui.ACCENT);
        btn.setBackground(Ui.outline(ColorTransparent(), Ui.ACCENT_DIM, 12, 1.2f, this));
        return btn;
    }

    private static int ColorTransparent() { return 0x00000000; }

    private void updVol() {
        if (Scheduler.globalVolOn(this)) {   // 全局覆盖开启：方案自身音量暂不生效
            vol.setEnabled(false);
            vol.setAlpha(0.4f);
            volLabel.setText(getString(R.string.vol_overridden, Scheduler.globalVol(this)));
            return;
        }
        vol.setEnabled(true);
        vol.setAlpha(1f);
        int pr = vol.getProgress();
        volLabel.setText(pr == 0 ? getString(R.string.volume_follow)
                : getString(R.string.volume_pct, pr));
    }

    private void updPreview() {
        List<String> rs = genRules();
        rulePreview.setText(rs.isEmpty() ? getString(R.string.none_selected)
                : getString(R.string.rule_preview, String.join(",", rs)));
    }

    /** 勾选状态 -> 规则串：连续半点合并 /30，整点对齐合并默认步长，:30 对齐合并 /60 */
    private List<String> genRules() {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < 48) {
            if (!slots[i].isChecked()) { i++; continue; }
            int s = i;
            if (i < 47 && slots[i + 1].isChecked()) {          // 连续半点串
                while (i < 47 && slots[i + 1].isChecked()) i++;
                out.add(fmtRange(s, i, 1));
                i++;
            } else if (i + 2 < 48 && slots[i + 2].isChecked()) { // 隔半点串（每小时对齐）
                i += 2;
                while (i + 2 < 48 && slots[i + 2].isChecked()) i += 2;
                out.add(fmtRange(s, i, 2));
                i++;
            } else {                                            // 孤立单点
                out.add(fmtMin(s * 30));
                i++;
            }
        }
        return out;
    }

    private static String fmtRange(int s, int e, int stride) {
        String t = fmtMin(s * 30) + "-" + fmtMin(e * 30);
        if (stride == 1) return t + "/30";
        if ((s * 30) % 60 != 0) return t + "/60";  // 从 :30 起每小时，需显式步长
        return t;
    }

    private static String fmtMin(int m) {
        return String.format(java.util.Locale.US, "%d:%02d", m / 60, m % 60);
    }

    // ── 铃声来源 ──

    /** 组合铃声列表：App 内置 + 系统音效 + 自定义文件入口 */
    private void rebuildList() {
        listLabels.clear();
        listValues.clear();
        for (int i = 0; i < RING_KEYS.length; i++) {
            listLabels.add(getString(RING_LABEL_IDS[i]));
            listValues.add(RING_KEYS[i]);
        }
        try {
            android.media.RingtoneManager rm = new android.media.RingtoneManager(this);
            rm.setType(android.media.RingtoneManager.TYPE_NOTIFICATION
                    | android.media.RingtoneManager.TYPE_ALARM);
            android.database.Cursor c = rm.getCursor();
            int n = c.getCount();
            for (int i = 0; i < n; i++) {
                android.net.Uri u = rm.getRingtoneUri(i);
                if (u != null) {
                    listLabels.add(c.getString(android.media.RingtoneManager.TITLE_COLUMN_INDEX));
                    listValues.add("sys:" + u);
                }
                c.moveToNext();
            }
        } catch (Exception ignored) { }
        if (customUri != null) {
            listLabels.add(getString(R.string.custom_selected));
            listValues.add("doc:" + customUri);
        } else {
            listLabels.add(getString(R.string.custom_unselected));
            listValues.add("custom");
        }
        listSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, listLabels));
    }

    private void pickFile() {
        android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        it.setType("audio/*");
        it.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(it, 42);
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 42 && res == RESULT_OK && data != null && data.getData() != null) {
            customUri = data.getData().toString();
            try {
                getContentResolver().takePersistableUriPermission(data.getData(),
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            rebuildList();
            for (int i = 0; i < listValues.size(); i++)
                if (listValues.get(i).equals("doc:" + customUri)) listSpinner.setSelection(i);
        }
    }

    /** 当前铃声选择的 ring 值：内置 key / sys:URI / doc:URI */
    private String currentRingValue() throws Exception {
        int pos = listSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= listValues.size()) throw new Exception(getString(R.string.err_select_ring));
        String v = listValues.get(pos);
        if (v.isEmpty() || "custom".equals(v)) throw new Exception(getString(R.string.err_custom_not_picked));
        return v;
    }

    // ── 试听 ──

    private MediaPlayer preview;
    private int pvSave = -1;

    private void togglePreview(Button btn) {
        if (preview != null) { stopPreview(btn); return; }
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int pct = vol.getProgress() == 0
                    ? Math.round(am.getStreamVolume(AudioManager.STREAM_ALARM) * 100f / max)
                    : vol.getProgress();
            pvSave = am.getStreamVolume(AudioManager.STREAM_ALARM);
            float gain = 1f;
            int eff = Scheduler.globalVolOn(this) ? Scheduler.globalVol(this)
                    : (vol.getProgress() != 0 ? pct : -1);
            if (eff >= 0) {
                // 双保险：流拉满 + MediaPlayer 百分比衰减（见 ChimeReceiver）；全局覆盖优先
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
                gain = Math.max(0f, Math.min(1f, eff / 100f));
            }
            preview = new MediaPlayer();
            preview.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            final float pvGain = gain;
            String rv = currentRingValue();
            if (rv.startsWith("sys:") || rv.startsWith("doc:")) {
                preview.setDataSource(this, Uri.parse(rv.substring(4)));
            } else {
                String file;
                switch (rv) {
                    // 铃声 key（存于偏好）与 assets 文件名解耦，文件名归一化为 ring1~ring8
                    case "bell":  file = "ring2.mp3";  break;
                    case "wind":  file = "ring3.ogg";  break;
                    case "sing":  file = "ring1.mp3";  break;
                    case "d1":    file = "ring5.mp3";  break;
                    case "d2":    file = "ring6.mp3";  break;
                    case "d3":    file = "ring7.mp3";  break;
                    case "d4":    file = "ring8.mp3";  break;
                    default:      file = "ring4.ogg";  break;
                }
                android.content.res.AssetFileDescriptor fd = getAssets().openFd(file);
                preview.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                fd.close();
            }
            preview.prepare();
            preview.setVolume(pvGain, pvGain);   // 必须在 prepare 后调用（Idle 态会被忽略）
            preview.setOnCompletionListener(m -> stopPreview(btn));
            preview.start();
            preview.setVolume(pvGain, pvGain);   // 防 start 时系统重置增益
            btn.setText(getString(R.string.stop));
        } catch (Exception e) {
            stopPreview(btn);
        }
    }

    private void stopPreview(Button btn) {
        if (preview != null) { preview.release(); preview = null; }
        if (pvSave >= 0) {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_ALARM, pvSave, 0);
            pvSave = -1;
        }
        btn.setText(getString(R.string.preview));
    }

    @Override
    protected void onDestroy() {
        if (preview != null) { preview.release(); preview = null; }
        if (pvSave >= 0) {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_ALARM, pvSave, 0);
            pvSave = -1;
        }
        super.onDestroy();
    }

    // ── 删除（两步确认，3 秒内再点一次生效）──


    private void delConfirm() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_title))
                .setMessage(getString(R.string.delete_msg, name.getText().toString().trim()))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete), (d, w) -> {
                    JSONArray arr = Scheduler.profiles(this);
                    JSONArray out = new JSONArray();
                    for (int k = 0; k < arr.length(); k++) if (k != index) out.put(arr.optJSONObject(k));
                    Scheduler.saveProfiles(this, out);
                    ChimeReceiver.scheduleNext(this);
                    Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }

    // ── 保存 ──

    private void save() {
        try {
            JSONObject p = buildProfile();
            List<String> conflicts = Scheduler.conflicts(this, p, index);
            if (conflicts.isEmpty()) { doSave(p); return; }
            StringBuilder msg = new StringBuilder(getString(R.string.conflict_intro));
            for (String s : conflicts) msg.append("\n· ").append(s);
            msg.append('\n').append(getString(R.string.conflict_ask));
            new android.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.conflict_title))
                    .setMessage(msg)
                    .setNegativeButton(getString(R.string.cancel), null)
                    .setPositiveButton(getString(R.string.save_anyway), (d, w) -> doSave(p))
                    .show();
        } catch (Exception ex) {
            Toast.makeText(this, getString(R.string.save_failed, ex.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    /** 校验并组装方案 JSON（不含写入） */
    private JSONObject buildProfile() throws Exception {
        JSONObject p = new JSONObject();
        p.put("name", name.getText().toString().trim());
        if (p.optString("name").isEmpty()) p.put("name", getString(R.string.profile_default));
        JSONArray days = new JSONArray();
        for (int i = 0; i < 7; i++) if (dayBoxes.get(i).isChecked()) days.put(DAY_VALUES[i]);
        if (days.length() == 0) for (int v2 : DAY_VALUES) days.put(v2);
        p.put("days", days);
        p.put("dayMode", modeSpinner.getSelectedItemPosition());
        List<String> rs = genRules();
        if (rs.isEmpty()) throw new IllegalArgumentException(getString(R.string.err_at_least_one));
        JSONArray rules = new JSONArray();
        for (String r : rs) {
            Scheduler.expandRule(r); // 校验
            rules.put(r);
        }
        p.put("rules", rules);
        p.put("ring", currentRingValue());
        p.put("vol", vol.getProgress() == 0 ? -1 : vol.getProgress());
        p.put("on", true);
        return p;
    }

    private void doSave(JSONObject p) {
        try {
            JSONArray arr = Scheduler.profiles(this);
            if (index >= 0) arr.put(index, p); else arr.put(p);
            Scheduler.saveProfiles(this, arr);
            ChimeReceiver.scheduleNext(this);
            finish();
        } catch (Exception ex) {
            Toast.makeText(this, getString(R.string.save_failed, ex.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) { return Ui.dpi(v, this); }
}

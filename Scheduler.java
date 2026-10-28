package com.len.hourlychime;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TreeSet;

/** 数据与调度核心：方案(JSON)解析、规则展开、下一次触发时间计算 */
public class Scheduler {

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) { return sp(c).getBoolean("enabled", false); }
    public static void setEnabled(Context c, boolean v) { sp(c).edit().putBoolean("enabled", v).apply(); }
    public static boolean vibrate(Context c) { return sp(c).getBoolean("vibrate", true); }
    public static void setVibrate(Context c, boolean v) { sp(c).edit().putBoolean("vibrate", v).apply(); }

    // ── 全局音量（一键覆盖所有方案）──
    public static boolean globalVolOn(Context c) { return sp(c).getBoolean("globalVolOn", false); }
    public static void setGlobalVolOn(Context c, boolean v) { sp(c).edit().putBoolean("globalVolOn", v).apply(); }
    public static int globalVol(Context c) { return Math.max(0, Math.min(100, sp(c).getInt("globalVol", 80))); }
    public static void setGlobalVol(Context c, int v) { sp(c).edit().putInt("globalVol", Math.max(0, Math.min(100, v))).apply(); }

    /** 实际播放音量：全局覆盖开启→全局值（0-100）；否则方案自身值（-1=跟随系统） */
    public static int effectiveVol(Context c, JSONObject p) {
        if (globalVolOn(c)) return globalVol(c);
        return p == null ? -1 : p.optInt("vol", -1);
    }

    private static final String SEED_KEY = "seed_v8";

    /**
     * 方案列表恒为两项：[0]=工作日 [1]=节假日。
     * 不提供新建/删除——组别即方案，索引固定。
     */
    public static JSONArray profiles(Context c) {
        SharedPreferences s = sp(c);
        if (!s.getBoolean(SEED_KEY, false)) {     // v8：清库重建，彻底剔除历史脏数据
            JSONArray def = defaults(c);
            s.edit().putString("profiles", def.toString())
                    .putBoolean(SEED_KEY, true).apply();
            return def;
        }
        JSONArray arr;
        try { arr = new JSONArray(s.getString("profiles", "[]")); }
        catch (Exception e) { arr = new JSONArray(); }
        // 自愈：结构被破坏（数量不对/缺组别）时重建
        if (arr.length() != 2 || arr.optJSONObject(0) == null || arr.optJSONObject(1) == null) {
            JSONArray def = defaults(c);
            s.edit().putString("profiles", def.toString()).apply();
            return def;
        }
        // 清洗：hour 分类只允许整点(60 的倍数)，half 只允许半点(%60==30)。
        // 历史脏数据（例如 930 混进 hour）会导致 15:30 同时命中整点铃，必须先剔除。
        if (sanitize(arr)) {
            s.edit().putString("profiles", arr.toString()).apply();
        }
        return arr;
    }

    /**
     * 清洗分类集合：hour 只留整点，half 只留半点，office 只留 0-1439 的合法分钟。
     * 返回是否发生了修改（需要回写）。
     */
    private static boolean sanitize(JSONArray arr) {
        boolean changed = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p == null) continue;
            JSONArray cats = p.optJSONArray("cats");
            if (cats == null) continue;
            for (int j = 0; j < cats.length(); j++) {
                JSONObject cat = cats.optJSONObject(j);
                if (cat == null) continue;
                String k = cat.optString("k");
                JSONArray src = cat.optJSONArray("m");
                if (src == null) continue;
                JSONArray out = new JSONArray();
                for (int x = 0; x < src.length(); x++) {
                    int m = src.optInt(x, -1);
                    if (m < 0 || m > 1439) { changed = true; continue; }
                    if ("hour".equals(k) && m % 60 != 0) { changed = true; continue; }
                    if ("half".equals(k) && m % 60 != 30) { changed = true; continue; }
                    out.put(m);
                }
                if (out.length() != src.length()) {
                    try { cat.put("m", out); } catch (Exception ignored) { }
                }
            }
        }
        return changed;
    }

    /** 默认两大方案：工作日（高阶：9:00/12:30/13:30/18:00 + 7~24 整点 + 半点）/ 节假日（10~24 整点 + 半点） */
    private static JSONArray defaults(Context c) {
        JSONArray arr = new JSONArray();
        try {
            // ── 工作日 ──
            java.util.TreeSet<Integer> office = new java.util.TreeSet<>();
            office.add(9 * 60);    // 上班 9:00
            office.add(12 * 60 + 30);  // 午休 12:30
            office.add(13 * 60 + 30);  // 午班 13:30
            office.add(18 * 60);   // 下班 18:00

            java.util.TreeSet<Integer> hours = new java.util.TreeSet<>();
            for (int h = 7; h <= 23; h++) hours.add(h * 60);   // 7:00-24:00

            java.util.TreeSet<Integer> halves = new java.util.TreeSet<>();
            for (int h = 7; h <= 23; h++) halves.add(h * 60 + 30);

            JSONObject wd = new JSONObject();
            wd.put("name", c.getString(R.string.group_workday));
            wd.put("group", 0).put("mode", 1).put("dayMode", 1);
            wd.put("days", new JSONArray("[2,3,4,5,6]"));
            wd.put("rules", rulesOf(office, hours, halves));
            wd.put("cats", catsOf(office, hours, halves, "sing", "bell", "wind"));
            wd.put("ring", "bell").put("vol", 50).put("on", true);
            arr.put(wd);

            // ── 节假日 ──
            java.util.TreeSet<Integer> h2 = new java.util.TreeSet<>();
            for (int h = 10; h <= 23; h++) h2.add(h * 60);
            java.util.TreeSet<Integer> hf2 = new java.util.TreeSet<>();
            for (int h = 10; h <= 23; h++) hf2.add(h * 60 + 30);

            JSONObject hd = new JSONObject();
            hd.put("name", c.getString(R.string.group_holiday));
            hd.put("group", 1).put("mode", 0).put("dayMode", 2);
            hd.put("days", new JSONArray("[1,7]"));
            hd.put("rules", rulesOf(new java.util.TreeSet<Integer>(), h2, hf2));
            hd.put("cats", catsOf(new java.util.TreeSet<Integer>(), h2, hf2, "sing", "bell", "wind"));
            hd.put("ring", "bell").put("vol", 50).put("on", true);
            arr.put(hd);
        } catch (Exception ignored) { }
        return arr;
    }

    private static JSONArray rulesOf(java.util.TreeSet<Integer> office,
                                     java.util.TreeSet<Integer> hours,
                                     java.util.TreeSet<Integer> halves) {
        JSONArray out = new JSONArray();
        try {
            for (Integer m : office) out.put(fmtMin(m));
            for (String r : compact(hours)) out.put(r);
            for (String r : compact(halves)) out.put(r);
        } catch (Exception ignored) { }
        return out;
    }

    private static JSONArray catsOf(java.util.TreeSet<Integer> office,
                                    java.util.TreeSet<Integer> hours,
                                    java.util.TreeSet<Integer> halves,
                                    String rOffice, String rHour, String rHalf) {
        JSONArray out = new JSONArray();
        try {
            if (!office.isEmpty()) out.put(cat("office", office, rOffice));
            out.put(cat("hour", hours, rHour));
            out.put(cat("half", halves, rHalf));
        } catch (Exception ignored) { }
        return out;
    }

    private static JSONObject cat(String k, java.util.TreeSet<Integer> set, String ring)
            throws org.json.JSONException {
        JSONArray arr = new JSONArray();
        for (Integer m : set) arr.put(m);
        return new JSONObject().put("k", k).put("m", arr).put("ring", ring).put("vol", 50);
    }

    /** 连续等步长段压缩成 a-b 或 a-b/30 */
    static java.util.List<String> compact(java.util.TreeSet<Integer> set) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (set.isEmpty()) return out;
        java.util.List<Integer> arr = new java.util.ArrayList<>(set);
        int i = 0;
        while (i < arr.size()) {
            int start = arr.get(i);
            int j = i, stride = 0;
            if (i + 1 < arr.size()) {
                int d = arr.get(i + 1) - arr.get(i);
                if (d == 30 || d == 60) {
                    stride = d; j = i + 1;
                    while (j + 1 < arr.size() && arr.get(j + 1) - arr.get(j) == stride) j++;
                }
            }
            if (stride != 0 && j > i) {
                String t = fmtMin(start) + "-" + fmtMin(arr.get(j));
                out.add(stride == 30 ? t + "/30" : (start % 60 != 0 ? t + "/60" : t));
            } else {
                out.add(fmtMin(start));
            }
            i = Math.max(j, i) + 1;
        }
        return out;
    }

    public static void saveProfiles(Context c, JSONArray arr) {
        sp(c).edit().putString("profiles", arr.toString()).apply();
    }

    private static List<String> rules(JSONObject p) {
        List<String> out = new ArrayList<>();
        JSONArray r = p.optJSONArray("rules");
        if (r != null) for (int i = 0; i < r.length(); i++) out.add(r.optString(i, ""));
        return out;
    }

    private static java.util.HashSet<String> HOLI, WORK;

    private static void loadCal(Context c) {
        if (HOLI != null) return;
        HOLI = new java.util.HashSet<>();
        WORK = new java.util.HashSet<>();
        try {
            java.io.InputStream in = c.getAssets().open("holidays.json");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONObject o = new JSONObject(bo.toString("UTF-8"));
            JSONArray h = o.optJSONArray("holidays");
            if (h != null) for (int i = 0; i < h.length(); i++) HOLI.add(h.optString(i));
            JSONArray w = o.optJSONArray("workdays");
            if (w != null) for (int i = 0; i < w.length(); i++) WORK.add(w.optString(i));
        } catch (Exception ignored) { }
    }

    /** 法定工作日判定：调休补班 > 法定假日 > 周一至周五（无数据年份按星期兜底） */
    public static boolean isWorkday(Context c, Calendar day) {
        loadCal(c);
        String d = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
        if (WORK.contains(d)) return true;
        if (HOLI.contains(d)) return false;
        int dow = day.get(Calendar.DAY_OF_WEEK);
        return dow >= Calendar.MONDAY && dow <= Calendar.FRIDAY;
    }

    /** 当天类型标签：法定假期 / 工作日 / 周末(周六) / 周日（随系统语言） */
    public static String dayLabel(Context c, Calendar day) {
        loadCal(c);
        String d = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
        if (HOLI.contains(d)) return c.getString(R.string.holiday);
        if (WORK.contains(d)) return c.getString(R.string.workday);
        int dow = day.get(Calendar.DAY_OF_WEEK);
        if (dow == Calendar.SUNDAY) return c.getString(R.string.sunday);
        if (dow == Calendar.SATURDAY) return c.getString(R.string.weekend);
        return c.getString(R.string.workday);
    }

    /** dayMode: 0=按星期(勾选) 1=法定工作日 2=法定休息日 */
    private static boolean dayMatch(Context c, JSONObject p, Calendar day) {
        int mode = p.optInt("dayMode", 0);
        if (mode == 1) return isWorkday(c, day);
        if (mode == 2) return !isWorkday(c, day);
        JSONArray d = p.optJSONArray("days");
        if (d == null) return true;
        int dow = day.get(Calendar.DAY_OF_WEEK);
        for (int i = 0; i < d.length(); i++) if (d.optInt(i, -1) == dow) return true;
        return false;
    }

    /** 分钟-of-day -> "H:MM"（供 UI 展示） */
    public static String fmtMin(int m) {
        return String.format(java.util.Locale.US, "%d:%02d", m / 60, m % 60);
    }

    /** 展开一个规则串为「分钟-of-day」列表。token: H:M 或 H:M-H:M，可加 /步长(分钟)，默认 60 */
    public static List<Integer> expandRule(String rule) {
        TreeSet<Integer> set = new TreeSet<>();
        for (String tok : rule.split("[,，]")) {
            tok = tok.trim();
            if (tok.isEmpty()) continue;
            set.addAll(expandToken(tok));
        }
        return new ArrayList<>(set);
    }

    private static List<Integer> expandToken(String tok) {
        int step = 60;
        int slash = tok.indexOf('/');
        if (slash >= 0) {
            step = Integer.parseInt(tok.substring(slash + 1).trim());
            if (step <= 0) throw new IllegalArgumentException("步长须>0");
            tok = tok.substring(0, slash).trim();
        }
        int dash = tok.indexOf('-');
        int start, end;
        if (dash >= 0) {
            start = hmin(tok.substring(0, dash));
            end = hmin(tok.substring(dash + 1));
        } else {
            start = end = hmin(tok);
        }
        if (start > 1439 || end > 1439) throw new IllegalArgumentException("时间超 23:59");
        List<Integer> out = new ArrayList<>();
        for (int t = start; t <= end; t += step) out.add(t);
        return out;
    }

    private static int hmin(String s) {
        int c = s.indexOf(':');
        if (c <= 0) throw new IllegalArgumentException("格式应为 H:MM：" + s);
        int h = Integer.parseInt(s.substring(0, c).trim());
        int m = Integer.parseInt(s.substring(c + 1).trim());
        if (h < 0 || h > 23 || m < 0 || m > 59) throw new IllegalArgumentException("时间越界：" + s);
        return h * 60 + m;
    }

    /** 某天（任意 Calendar 日期）所有启用的触发点（分钟-of-day，升序） */
    private static List<Integer> triggersForDay(Context c, JSONArray ps, Calendar day) {
        TreeSet<Integer> set = new TreeSet<>();
        int dow = day.get(Calendar.DAY_OF_WEEK);
        for (int i = 0; i < ps.length(); i++) {
            JSONObject p = ps.optJSONObject(i);
            if (p == null || !p.optBoolean("on", true) || !dayMatch(c, p, day)) continue;
            for (String rule : rules(p)) {
                try { set.addAll(expandRule(rule)); } catch (Exception ignored) { }
            }
        }
        return new ArrayList<>(set);
    }

    /** 下一次触发时间戳（epoch ms）；未启用或无触发点返回 -1 */
    public static long nextTrigger(Context c) {
        if (!enabled(c)) return -1;
        JSONArray ps = profiles(c);
        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        for (int d = 0; d < 8; d++) {
            Calendar day = (Calendar) now.clone();
            day.add(Calendar.DAY_OF_YEAR, d);
            List<Integer> ts = triggersForDay(c, ps, day);
            if (d == 0) while (!ts.isEmpty() && ts.get(0) <= nowMin) ts.remove(0);
            if (!ts.isEmpty()) {
                day.set(Calendar.HOUR_OF_DAY, ts.get(0) / 60);
                day.set(Calendar.MINUTE, ts.get(0) % 60);
                day.set(Calendar.SECOND, 0);
                day.set(Calendar.MILLISECOND, 0);
                return day.getTimeInMillis();
            }
        }
        return -1;
    }

    /** 当前这一分钟应有哪个方案触发（多个命中取第一个） */
    public static JSONObject firingProfile(Context c) {
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        JSONArray ps = profiles(c);
        for (int i = 0; i < ps.length(); i++) {
            JSONObject p = ps.optJSONObject(i);
            if (p == null || !p.optBoolean("on", true) || !dayMatch(c, p, now)) continue;
            for (String rule : rules(p)) {
                try {
                    if (expandRule(rule).contains(minute)) return p;
                } catch (Exception ignored) { }
            }
        }
        return null;
    }

    /**
     * 当前这一分钟应触发的「分类」：返回 {profile, cat, ring, vol}，无命中返回 null。
     *
     * 分类优先级：上下班(office) > 整点(hour) > 半点(half)。
     * 同一分钟被多个分类覆盖时（如 18:00 既是下班也是整点）取优先级高者，
     * 并带出该分类专属的铃声/音量。无 cats 字段的旧方案回退到 firingProfile()。
     */
    public static JSONObject firingCategory(Context c) {
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        JSONArray ps = profiles(c);
        String[] order = {"office", "hour", "half"};
        for (String want : order) {
            for (int i = 0; i < ps.length(); i++) {
                JSONObject p = ps.optJSONObject(i);
                if (p == null || !p.optBoolean("on", true) || !dayMatch(c, p, now)) continue;
                JSONArray cats = p.optJSONArray("cats");
                if (cats == null) continue;
                for (int j = 0; j < cats.length(); j++) {
                    JSONObject cat = cats.optJSONObject(j);
                    if (cat == null || !want.equals(cat.optString("k"))) continue;
                    JSONArray arr = cat.optJSONArray("m");
                    if (arr == null) continue;
                    boolean hit = false;
                    for (int k = 0; k < arr.length(); k++)
                        if (arr.optInt(k, -1) == minute) { hit = true; break; }
                    if (!hit) continue;
                    try {
                        JSONObject out = new JSONObject();
                        out.put("profile", p);
                        out.put("cat", want);
                        out.put("ring", cat.optString("ring", p.optString("ring", "bell")));
                        out.put("vol", cat.optInt("vol", p.optInt("vol", 80)));
                        return out;
                    } catch (Exception ignored) { }
                }
            }
        }
        JSONObject p = firingProfile(c);
        if (p == null) return null;
        try {
            JSONObject out = new JSONObject();
            out.put("profile", p);
            out.put("cat", "");
            out.put("ring", p.optString("ring", "bell"));
            out.put("vol", p.optInt("vol", 80));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // ── 方案时间冲突检测 ──

    /** 日期串 yyyy-MM-dd -> Calendar 星期值（1=周日 ... 7=周六） */
    private static int dowOf(String date) {
        try {
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            Calendar d = Calendar.getInstance();
            d.setTime(f.parse(date));
            return d.get(Calendar.DAY_OF_WEEK);
        } catch (Exception e) { return -1; }
    }

    /** 方案覆盖的星期集合（Calendar.DAY_OF_WEEK 值）：mode1 工作日=周一至五+补班日，mode2=周末+假日 */
    public static java.util.HashSet<Integer> daySet(Context c, JSONObject p) {
        java.util.HashSet<Integer> s = new java.util.HashSet<>();
        int mode = p.optInt("dayMode", 0);
        if (mode == 0) {
            JSONArray d = p.optJSONArray("days");
            if (d == null || d.length() == 0) { for (int v = 1; v <= 7; v++) s.add(v); }
            else for (int i = 0; i < d.length(); i++) s.add(d.optInt(i, -1));
            return s;
        }
        loadCal(c);
        if (mode == 1) {
            for (int v = Calendar.MONDAY; v <= Calendar.FRIDAY; v++) s.add(v);
            if (WORK != null) for (String w : WORK) s.add(dowOf(w));
        } else {
            s.add(Calendar.SATURDAY);
            s.add(Calendar.SUNDAY);
            if (HOLI != null) for (String h : HOLI) s.add(dowOf(h));
        }
        return s;
    }

    /** 候选方案的触发分钟集合 */
    private static TreeSet<Integer> minutesOf(JSONObject p) {
        TreeSet<Integer> set = new TreeSet<>();
        for (String rule : rules(p)) {
            try { set.addAll(expandRule(rule)); } catch (Exception ignored) { }
        }
        return set;
    }

    /** 候选方案与其它启用方案（排除 excludeIndex）的时间冲突列表，格式 "H:MM（方案名）"，升序去重 */
    public static List<String> conflicts(Context c, JSONObject candidate, int excludeIndex) {
        TreeSet<Integer> candMin = minutesOf(candidate);
        if (candMin.isEmpty()) return new ArrayList<>();
        java.util.HashSet<Integer> candDays = daySet(c, candidate);
        TreeSet<String> out = new TreeSet<>();
        JSONArray ps = profiles(c);
        for (int i = 0; i < ps.length(); i++) {
            if (i == excludeIndex) continue;
            JSONObject o = ps.optJSONObject(i);
            if (o == null || !o.optBoolean("on", true)) continue;
            boolean overlap = false;
            for (int v : daySet(c, o)) if (candDays.contains(v)) { overlap = true; break; }
            if (!overlap) continue;
            TreeSet<Integer> otherMin = minutesOf(o);
            String name = o.optString("name", "?");
            for (Integer m : candMin) {
                if (otherMin.contains(m)) {
                    out.add(String.format(java.util.Locale.US, "%d:%02d（%s）",
                            m / 60, m % 60, name));
                }
            }
        }
        return new ArrayList<>(out);
    }
}

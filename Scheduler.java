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

    public static JSONArray profiles(Context c) {
        SharedPreferences s = sp(c);
        if (!s.getBoolean("seed_v5", false)) {   // v5：接入法定工作日/休息日智能同步
            JSONArray def = defaults();
            s.edit().putString("profiles", def.toString())
                    .putBoolean("seed_v5", true).apply();
            return def;
        }
        try { return new JSONArray(s.getString("profiles", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    /** 迁移旧版配置：节点特铃(最高优先) + 工作日两方案 + 周末特铃；数组顺序即优先级 */
    private static JSONArray defaults() {
        try {
            JSONArray arr = new JSONArray();
            JSONObject node = new JSONObject();
            node.put("name", "特铃-节点(最高优先)");
            node.put("days", new JSONArray("[2,3,4,5,6]"));
            node.put("rules", new JSONArray("[\"9:00,18:00\",\"12:30,13:30\"]"));
            node.put("dayMode", 1).put("ring", "sing").put("vol", 80).put("on", true);
            arr.put(node);
            JSONObject wdOn = new JSONObject();
            wdOn.put("name", "工作日-整点");
            wdOn.put("days", new JSONArray("[2,3,4,5,6]"));
            wdOn.put("rules", new JSONArray(
                    "[\"0:00,7:00-8:00,10:00-17:00,19:00-23:00\",\"9:00,18:00\"]"));
            wdOn.put("dayMode", 1).put("ring", "bell").put("vol", 80).put("on", true);
            arr.put(wdOn);
            JSONObject wdHalf = new JSONObject();
            wdHalf.put("name", "工作日-半点");
            wdHalf.put("days", new JSONArray("[2,3,4,5,6]"));
            wdHalf.put("rules", new JSONArray(
                    "[\"0:30,6:30-11:30,14:30-23:30\",\"12:30-13:30\"]"));
            wdHalf.put("dayMode", 1).put("ring", "wind").put("vol", 80).put("on", true);
            arr.put(wdHalf);
            JSONObject wk = new JSONObject();
            wk.put("name", "特铃-周末");
            wk.put("days", new JSONArray("[1,7]"));
            wk.put("rules", new JSONArray("[\"0:00,10:00-23:00\"]"));
            wk.put("dayMode", 2).put("ring", "chimes").put("vol", 80).put("on", true);
            arr.put(wk);
            return arr;
        } catch (Exception e) {
            return new JSONArray();
        }
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

    /** 当天类型标签：法定假期 / 工作日 / 周末(周六) / 周日 */
    public static String dayLabel(Context c, Calendar day) {
        loadCal(c);
        String d = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
        if (HOLI.contains(d)) return "法定假期";
        if (WORK.contains(d)) return "工作日";
        int dow = day.get(Calendar.DAY_OF_WEEK);
        if (dow == Calendar.SUNDAY) return "周日";
        if (dow == Calendar.SATURDAY) return "周末";
        return "工作日";
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
}

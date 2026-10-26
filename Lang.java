package com.len.hourlychime;

import android.content.Context;
import java.util.Locale;

/** 应用内语言切换：prefs "lang" = sys/en/zh；Activity attachBaseContext 时包裹 locale */
public final class Lang {
    private Lang() { }

    public static String pref(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("lang", "sys");
    }

    public static void set(Context c, String v) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
                .edit().putString("lang", v).apply();
    }

    public static Context wrap(Context base) {
        String p = pref(base);
        if ("sys".equals(p)) return base;
        Locale l = new Locale(p);
        android.content.res.Configuration cfg =
                new android.content.res.Configuration(base.getResources().getConfiguration());
        android.os.LocaleList ll = new android.os.LocaleList(l);
        android.os.LocaleList.setDefault(ll);
        cfg.setLocales(ll);
        return base.createConfigurationContext(cfg);
    }
}

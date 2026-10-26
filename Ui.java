package com.len.hourlychime;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 深色 + 109C 黄的设计令牌与样式工具 */
public final class Ui {
    // Color tokens（蓝白浅色主题）
    public static final int BG = 0xFFF4F6FA;      // 页面底
    public static final int CARD = 0xFFFFFFFF;    // 卡片
    public static final int CARD2 = 0xFFEDF1F7;   // 输入框/次级
    public static final int ACCENT = 0xFF2F6FED;  // 主蓝
    public static final int ACCENT_DIM = 0xFFB9CDF4;
    public static final int TEXT = 0xFF1B2027;
    public static final int SUB = 0xFF7C8593;
    public static final int ON_ACCENT = 0xFFFFFFFF;
    public static final int CHIP = 0xFFF5F6F7;

    private Ui() { }

    /** 页面背景：底色 + assets/bg.png 顶部闹钟水印（宽度铺满、顶部对齐、保比例裁底），无 bg.png 时回退纯色 */
    public static android.graphics.drawable.Drawable screenBg(android.app.Activity a) {
        android.graphics.drawable.Drawable base = new android.graphics.drawable.ColorDrawable(BG);
        try {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory
                    .decodeStream(a.getAssets().open("bg.png"));
            if (bmp == null) return base;
            android.graphics.drawable.BitmapDrawable bd =
                    new android.graphics.drawable.BitmapDrawable(a.getResources(), bmp);
            bd.setGravity(Gravity.FILL_HORIZONTAL | Gravity.TOP);
            return new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{base, bd});
        } catch (Exception e) {
            return base;
        }
    }

    public static GradientDrawable fill(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp, c));
        return g;
    }

    public static GradientDrawable outline(int fillColor, int strokeColor,
                                           float radiusDp, float strokeDp, Context c) {
        GradientDrawable g = fill(fillColor, radiusDp, c);
        g.setStroke(Math.max(1, Math.round(dp(strokeDp, c))), strokeColor);
        return g;
    }

    /** 状态栏与页面同色（无缝），去掉系统标题栏，禁止 MIUI 强制深色反色 */
    public static void immersive(android.app.Activity a) {
        a.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        android.view.Window w = a.getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        android.view.View dv = w.getDecorView();
        dv.setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (android.os.Build.VERSION.SDK_INT >= 29) dv.setForceDarkAllowed(false);
    }

    public static int statusBarHeight(android.app.Activity a) {
        int id = a.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? a.getResources().getDimensionPixelSize(id) : dpi(24, a);
    }

    public static float dp(float v, Context c) { return v * c.getResources().getDisplayMetrics().density; }

    public static int dpi(int v, Context c) { return Math.round(dp((float) v, c)); }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView label(Context c, String s) {
        TextView t = text(c, s, 13, SUB, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    public static LinearLayout card(Context c, int bgColor, float radiusDp, int padDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(fill(bgColor, radiusDp, c));
        int p = dpi(padDp, c);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0xFFE5EAF2);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpi(1, c)));
        return v;
    }

    public static void press(View v) {   // 轻微按压反馈
        v.setForeground(new GradientDrawable());
    }

    public static LinearLayout.LayoutParams mw() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams mw(int topMarginDp, Context c) {
        LinearLayout.LayoutParams lp = mw();
        lp.topMargin = dpi(topMarginDp, c);
        return lp;
    }

    public static LinearLayout.LayoutParams center() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }
}

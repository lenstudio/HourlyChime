package com.len.hourlychime;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Frosted glass dashboard design tokens. */
public final class Ui {
    // Color tokens（蓝白浅色主题）
    public static final int BG = 0xFFF1F4F9;      // 页面底
    public static final int CARD = 0xFFFFFFFF;    // 卡片
    public static final int CARD2 = 0xFFF1F4F9;   // 输入框/次级
    public static final int ACCENT = 0xFF287CE3;  // 主蓝
    public static final int ACCENT_DIM = 0xFFB9D2F4;
    public static final int TEXT = 0xFF1B2027;
    public static final int SUB = 0xFF60718A;
    public static final int ON_ACCENT = 0xFFFFFFFF;
    public static final int CHIP = 0xFFF5F6F7;

    private Ui() { }

    /** Soft, already diffused color fields behind translucent glass panels. */
    public static android.graphics.drawable.Drawable screenBg(android.app.Activity a) {
        return new android.graphics.drawable.ColorDrawable(BG);
    }

    private static final class GlassBackdrop extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint p = new android.graphics.Paint(3);
        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            float w=b.width(), h=b.height();
            p.setShader(new android.graphics.LinearGradient(0,0,w,h,
                    0xFFE8F2FF,0xFFEDEAF8,android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(b,p);
            glow(c,w*.05f,h*.25f,w*.95f,0xB078BEFA);
            glow(c,w*.98f,h*.55f,w*.9f,0xA8B7A0F4);
            glow(c,w*.18f,h*.92f,w,0x8877D9D7);
            p.setShader(null);
        }
        private void glow(android.graphics.Canvas c,float x,float y,float r,int color) {
            p.setShader(new android.graphics.RadialGradient(x,y,r,
                    new int[]{color,color & 0x00FFFFFF},null,android.graphics.Shader.TileMode.CLAMP));
            c.drawCircle(x,y,r,p);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
    }

    public static final class GlassSurface extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(3);
        private final float density;
        GlassSurface(Context c) { density=c.getResources().getDisplayMetrics().density; }
        @Override public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b=getBounds();
            float d=density;
            android.graphics.RectF r=new android.graphics.RectF(b.left+3*d,b.top+3*d,b.right-3*d,b.bottom-3*d);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setShadowLayer(3*d, 2*d, 3*d, 0x182D619B);
            paint.setShader(new android.graphics.LinearGradient(r.left,r.top,r.right,r.bottom,
                    0xC8D7EAFF,0xA6E5F1FF,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r,24*d,24*d,paint);
            paint.clearShadowLayer();
            int save=canvas.save();
            android.graphics.Path clip=new android.graphics.Path();
            clip.addRoundRect(r,24*d,24*d,android.graphics.Path.Direction.CW);
            canvas.clipPath(clip);
            paint.setShader(new android.graphics.LinearGradient(0,r.top,0,r.top+85*d,
                    new int[]{0x85FFFFFF,0x25FFFFFF,0x00FFFFFF},new float[]{0,.35f,1},android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(r,paint);
            paint.setShader(new android.graphics.RadialGradient(r.left+20*d,r.top+10*d,180*d,
                    0x70FFFFFF,0x00FFFFFF,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(r,paint);
            canvas.restoreToCount(save);
            paint.setShader(new android.graphics.LinearGradient(r.left,r.top,r.right,r.bottom,
                    0xFFFFFFFF,0x50FFFFFF,android.graphics.Shader.TileMode.CLAMP));
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f*d);
            canvas.drawRoundRect(r,24*d,24*d,paint);
            paint.setShader(null);
            paint.setStyle(android.graphics.Paint.Style.FILL);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    public static GradientDrawable gloss(boolean red, float radius, Context c) {
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                red ? new int[]{0xFFF17679,0xFFE75158,0xFFD63C47} : new int[]{0xFF64ACF5,0xFF3485E5,0xFF256BCB});
        g.setCornerRadius(dp(radius,c));
        g.setStroke(dpi(1,c),red?0xFFFFABB0:0xFFA8D5FF);
        return g;
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
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    public static TextView label(Context c, String s) {
        TextView t = text(c, s, 13, SUB, true);
        t.setLetterSpacing(0.03f);
        return t;
    }

    public static LinearLayout card(Context c, int bgColor, float radiusDp, int padDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(new GlassSurface(c));

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
        v.setForeground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x202F6FED), null,
                fill(0xFFFFFFFF, 12, v.getContext())));
    }

    public static android.content.res.ColorStateList tint() {
        return new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{ACCENT, 0xFFABB7CA});
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

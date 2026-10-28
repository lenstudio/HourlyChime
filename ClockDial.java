package com.len.hourlychime;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Live analog dial with a softly raised face. */
public final class ClockDial extends View {
    private final Paint p = new Paint(3);
    public ClockDial(Context c) {
        super(c);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d=getResources().getDisplayMetrics().density;
        float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(96*d,Math.min(getWidth(),getHeight())*.42f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFE7E9EF);
        p.setShadowLayer(10*d,6*d,7*d,0x306A768C);
        c.drawCircle(x,y,r,p);
        p.setShadowLayer(9*d,-6*d,-6*d,0xFFFFFFFF);
        c.drawCircle(x,y,r,p);
        p.clearShadowLayer();
        p.setShader(new LinearGradient(x-r,y-r,x+r,y+r,0xFFEAECF2,0xFFE2E5EC,Shader.TileMode.CLAMP));
        c.drawCircle(x,y,r,p);p.setShader(null);
        p.setStrokeWidth(d);
        for(int i=0;i<60;i++) {
            double a=Math.toRadians(i*6-90);
            float outer=r-8*d,inner=outer-(i%5==0?4:2)*d;
            p.setColor(i<30?0xFF739BD0:0xFFF9FAFD);
            c.drawLine(x+(float)Math.cos(a)*inner,y+(float)Math.sin(a)*inner,
                       x+(float)Math.cos(a)*outer,y+(float)Math.sin(a)*outer,p);
        }
        p.setShader(new LinearGradient(x-r,y-r,x+r,y+r,0xFFF1F2F6,0xFFE8EAF0,Shader.TileMode.CLAMP));
        c.drawCircle(x,y,r*.61f,p);p.setShader(null);
        java.util.Calendar now=java.util.Calendar.getInstance();
        float minute=now.get(java.util.Calendar.MINUTE)+now.get(java.util.Calendar.SECOND)/60f;
        hand(c,x,y,r*.39f,(now.get(java.util.Calendar.HOUR)+minute/60f)*30,0xFF30353F,2*d);
        hand(c,x,y,r*.69f,minute*6,Ui.ACCENT,1.5f*d);
        p.setColor(Ui.ACCENT);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,2.8f*d,p);
        if(isShown()) postInvalidateDelayed(1000);
    }
    private void hand(Canvas c,float x,float y,float length,float degrees,int color,float width) {
        double a=Math.toRadians(degrees-90);p.setColor(color);p.setStrokeWidth(width);
        c.drawLine(x-(float)Math.cos(a)*4,y-(float)Math.sin(a)*4,
                   x+(float)Math.cos(a)*length,y+(float)Math.sin(a)*length,p);
    }
}

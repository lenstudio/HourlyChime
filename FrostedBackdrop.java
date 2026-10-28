package com.len.hourlychime;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.LinearInterpolator;
import java.util.Random;
/** Cached diffused artwork with gentle background drift. */
public final class FrostedBackdrop extends View {
 private final Paint paint=new Paint(3);
 private final RectF destination=new RectF();
 private Bitmap artwork;
 private float phase;
 private final ValueAnimator drift=ValueAnimator.ofFloat(0f,1f);
 public FrostedBackdrop(Context c) {
  super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  drift.setDuration(72000);drift.setRepeatCount(ValueAnimator.INFINITE);
  drift.setRepeatMode(ValueAnimator.REVERSE);drift.setInterpolator(new LinearInterpolator());
  drift.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});
 }
 @Override protected void onSizeChanged(int w,int h,int ow,int oh) {
  if(w<=0||h<=0)return;
  int bw=384,bh=Math.max(1,Math.round(384f*h/w));
  artwork=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);
  Canvas c=new Canvas(artwork);c.drawColor(0xFFF2F5FA);
  glow(c,bw*.05f,bh*.24f,bw*1.2f,0x8082B7E4);
  glow(c,bw*.92f,bh*.57f,bw*1.1f,0x687C9BD3);
  glow(c,bw*.16f,bh*.9f,bw*1.2f,0x607CBFCB);
  paint.setShader(null);Random random=new Random(71);
  for(int i=0;i<bw*bh/3;i++){
   paint.setColor(i%2==0?0x09FFFFFF:0x04506B86);
   c.drawPoint(random.nextInt(bw),random.nextInt(bh),paint);
  }
 }
 private void glow(Canvas c,float x,float y,float r,int color){
  paint.setShader(new RadialGradient(x,y,r,new int[]{color,color&0x00FFFFFF},null,Shader.TileMode.CLAMP));c.drawCircle(x,y,r,paint);
 }
 @Override protected void onDraw(Canvas c){
  if(artwork==null)return;
  float w=getWidth(),h=getHeight(),t=(float)(.5-.5*Math.cos(phase*Math.PI));
  float dx=-w*.2f*t,dy=-h*.15f*(1-t);
  destination.set(dx,dy,dx+w*1.2f,dy+h*1.15f);paint.setColor(Color.WHITE);
  c.drawBitmap(artwork,null,destination,paint);
 }
 public void setActive(boolean active){
  if(active&&ValueAnimator.areAnimatorsEnabled()){
   if(!drift.isStarted())drift.start();else drift.resume();
  }else if(drift.isStarted())drift.pause();
 }
 @Override protected void onDetachedFromWindow(){drift.cancel();super.onDetachedFromWindow();}
}

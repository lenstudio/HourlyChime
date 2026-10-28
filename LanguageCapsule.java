package com.len.hourlychime;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.animation.ValueAnimator;

/** Two-tone language capsule supporting tap, swipe and accessibility click. */
public final class LanguageCapsule extends View {
 public interface OnLanguage { void select(String code); }
 private final Paint paint=new Paint(3);
 private final RectF rect=new RectF();
 private final OnLanguage listener;
 private boolean chinese,busy;
 private float position,downX,downY;
 private ValueAnimator animation;
 public LanguageCapsule(Context c,OnLanguage callback){
  super(c);listener=callback;
  chinese=c.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh");position=chinese?1:0;
  setClickable(true);setFocusable(true);setContentDescription(chinese?"语言：中文。点击切换英文，或左右滑动":"Language: English. Tap or swipe to switch to Chinese");
 }
 @Override protected void onDraw(Canvas c){
  float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),inset=3*d;
  paint.setShader(new LinearGradient(0,0,w,h,0xFFF9FCFF,0xFFDAE9FC,Shader.TileMode.CLAMP));rect.set(0,0,w,h);c.drawRoundRect(rect,h/2,h/2,paint);
  paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);paint.setColor(0xD0FFFFFF);rect.inset(d/2,d/2);c.drawRoundRect(rect,h/2,h/2,paint);paint.setStyle(Paint.Style.FILL);
  float half=(w-inset*2)/2,left=inset+half*position;
  rect.set(left,inset,left+half,h-inset);
  paint.setShader(new LinearGradient(0,0,0,h,0xFF64ADF6,0xFF2879D8,Shader.TileMode.CLAMP));c.drawRoundRect(rect,h/2,h/2,paint);paint.setShader(null);
  paint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextAlign(Paint.Align.CENTER);
  float baseline=h/2-(paint.ascent()+paint.descent())/2;
  paint.setColor(position<.5f?Color.WHITE:0xFF526D8C);c.drawText("EN",inset+half/2,baseline,paint);
  paint.setColor(position>=.5f?Color.WHITE:0xFF526D8C);c.drawText("中文",inset+half*1.5f,baseline,paint);
 }
 private void select(boolean next){
  if(busy||next==chinese)return;busy=true;
  animation=ValueAnimator.ofFloat(position,next?1:0);animation.setDuration(190);
  animation.addUpdateListener(a->{position=(float)a.getAnimatedValue();invalidate();});
  animation.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(isAttachedToWindow()){chinese=next;listener.select(next?"zh":"en");}}});animation.start();
 }
 @Override public boolean performClick(){super.performClick();select(!chinese);return true;}
 @Override public boolean onTouchEvent(MotionEvent e){
  if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}
  if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(Math.abs(e.getX()-downX)>ViewConfiguration.get(getContext()).getScaledTouchSlop())getParent().requestDisallowInterceptTouchEvent(true);return true;}
  if(e.getActionMasked()==MotionEvent.ACTION_UP){float dx=e.getX()-downX;if(Math.abs(dx)>ViewConfiguration.get(getContext()).getScaledTouchSlop())select(dx>0);else if(Math.abs(e.getY()-downY)<getHeight()) {boolean target=e.getX()>getWidth()/2f;if(target!=chinese)select(target);else performClick();}return true;}return true;
 }
 @Override protected void onDetachedFromWindow(){if(animation!=null){animation.removeAllListeners();animation.cancel();}super.onDetachedFromWindow();}
}

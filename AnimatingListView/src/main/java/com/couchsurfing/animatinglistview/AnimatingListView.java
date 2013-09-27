package com.couchsurfing.animatinglistview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Created by nathanielwolf on 9/24/13.
 */
public class AnimatingListView extends FrameLayout {
  private static final long FRAME_RATE = 1000 / 60;
  Interpolator interpolator;
  private AdjustListScrollPositionListener scrollListener;
  private int animDuration;
  private int[] childHeights;
  private int listContentsHeight;
  // private AnimateState animateState;
  private int targetHeight;
  //private Bitmap screenShot;
  private long startTime;
  private int startHeight;
  private ListView listView;
  Handler handler = new Handler();

  private int counter;
  private int screenShotTranslateY;
  private boolean settingUp;
  private AnimatingViewCapture listCaptureTemp;
  private AnimatingViewCapture listCapture;
  private int scrollAfterAnim;

  public AnimatingListView(Context context) {
    super(context);
  }

  public AnimatingListView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initAttrs(context, attrs);
  }

  public AnimatingListView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initAttrs(context, attrs);
  }

  private void initAttrs(Context context, AttributeSet attrs) {

    TypedArray a =
        context.getTheme().obtainStyledAttributes(attrs, R.styleable.AnimatingListView, 0, 0);
    int defaultDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
    animDuration = a.getInteger(R.styleable.AnimatingListView_animation_duration, defaultDuration);
    try {

    } finally {
      a.recycle();
    }

    interpolator = new AccelerateDecelerateInterpolator();
    scrollListener = new AdjustListScrollPositionListener();
    LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

    listView = new ListView(context);
    listView.setLayoutParams(params);
    listView.setOnScrollListener(scrollListener);
    listView.setBackgroundColor(Color.WHITE);

    addView(listView);
    //addView(screenShotView);
    setWillNotDraw(false);
  }

  public void setAdapter(ListAdapter adapter) {
    listView.setAdapter(adapter);
    childHeights = new int[adapter.getCount()];
    if (adapter == null) {
      listContentsHeight = 0;
      return;
    }
    childHeights = new int[adapter.getCount()];
    for (int i = 0; i < adapter.getCount(); i++) {
      View view = adapter.getView(i, null, this);

      view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
          MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

      childHeights[i] = view.getMeasuredHeight();
      listContentsHeight += childHeights[i];
    }
  }

  public void animateBy(int offset) {
    animateTo(getHeight() + offset);
  }

  public void animateTo(int targetHeight) {
    if (listCapture != null) {
      return;
    }

    this.targetHeight = targetHeight;
    this.startHeight = getHeight();

    //set the startTime to the setup state in case onDraw occurs during this time
    startTime = -1;

    int delta = targetHeight - startHeight;

    int scrollPosition = getListScrollPosition();

    //Y coordinates for the source (bitmap) and destination (view) that will be animated between
    int srcStartY, srcEndY, dstStartY, dstEndY;

    if (delta > 0) {//the list is growing

      //set the source and destination drawing values values
      srcStartY = delta > scrollPosition ? scrollPosition : delta;
      srcEndY = 0;
      dstStartY = delta;
      dstEndY = 0;

      //scroll the list down by the amount being revealed.
      final int scrollBy = srcEndY - srcStartY;

      //resize the view - new time a layo
      getLayoutParams().height = targetHeight;

      //capture the current view of the list
      if(listCapture != null){
        listCapture.recycle();
      }
      listCapture =
          new AnimatingViewCapture(captureListBitmap(), 0, 0, delta, 0);

      listView.setVisibility(View.GONE);
      //if the list needs to be moved before capture, use the scroll listener to do so
      if (scrollBy != 0) {
        listCaptureTemp =
            new AnimatingViewCapture(captureListBitmap(), srcStartY, srcEndY, dstStartY, dstEndY);



        //listView.setVisibility(View.GONE);
        Log.i("@@@","ADJSTING "+listView.getHeight()+" = "+getHeight() + "scrollBy:"+scrollBy);
        scrollListener.beginAdjustment();
        listView.smoothScrollBy(scrollBy,0);

       // requestLayout();
      } else {

        resizeListToTarget();
        Log.i("@@@","PULLING listHeight:"+listView.getHeight());

        listCapture =
            new AnimatingViewCapture(captureListBitmap(), srcStartY, srcEndY, dstStartY, dstEndY);

        beginAnimation();
      }

    } else { //the list is shrinking
      srcStartY = 0;
      dstStartY = 0;
      dstEndY = -delta;

      //if the targetHeight is greater than the list height, the list must be pulled down
      if (listContentsHeight < startHeight) {

        //if the list is smaller than the targetHeight, do not scroll the capture
        srcEndY = listContentsHeight < targetHeight?0: -delta - (startHeight-listContentsHeight);
      } else {
        //the list contents fill the frame, so just scroll the view down
        srcEndY =  -delta;
      }

      //capture the list and animate
      listCapture =
          new AnimatingViewCapture(captureListBitmap(), srcStartY, srcEndY, dstStartY, dstEndY);
      listView.setVisibility(View.GONE);
      scrollAfterAnim = srcEndY; //the list must be scrolled up after the size has changed
      beginAnimation();
    }

    //Log.i("@@@", "createBitmap START: " + (System.currentTimeMillis() - startTime));
  }

  public void beginAnimation() {
    startTime = System.currentTimeMillis();
    handler.postDelayed(new AnimatorRunnable(), 0);
  }

  private Bitmap captureListBitmap() {
    Log.i("@@@", "*************");
    Log.i("@@@", "HEIGHT view:" + getHeight() + "| list:" + listView.getHeight());
    Log.i("@@@", "*************");
    Bitmap bitmap = Bitmap.createBitmap(listView.getWidth(), listView.getHeight(), Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bitmap);
    listView.draw(c);

    settingUp = false;
    return bitmap;
  }

  private void endAnimation() {
    if (listCapture != null) {
      listCapture.recycle();
      listCapture = null;
    }
    listView.setVisibility(View.VISIBLE);

    //when shrinking, the view must resize at the end
    if(startHeight > targetHeight){
      getLayoutParams().height = targetHeight;
      listView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
        @Override public boolean onPreDraw() {
          listView.getViewTreeObserver().removeOnPreDrawListener(this);
          if(scrollAfterAnim > 0){
            Log.i("@@@","SCROLL AFTER "+scrollAfterAnim);
            listView.smoothScrollBy(scrollAfterAnim,0);
            scrollAfterAnim =0;
          }
          return false;
        }
      });
    }

    requestLayout();
  }

  @Override protected void onDraw(Canvas canvas) {
    //Log.i("@@@", "onDraw START: " + (System.currentTimeMillis() - startTime));
    super.onDraw(canvas);
    if (listCapture != null) {
      listCapture.draw(canvas);
    }

    //Log.i("@@@", "onDraw END: " + (System.currentTimeMillis() - startTime));
  }

  /**
   * Gets the absolute scroll position of the listview
   *
   * @return the scroll position
   */
  private int getListScrollPosition() {
    if (listView.getChildCount() == 0) {
      return 0;
    }
    int currentY = 0;
    for (int i = 0; i < listView.getFirstVisiblePosition(); i++) {
      currentY += childHeights[i];
    }
    return currentY - listView.getChildAt(0).getTop();
  }

  /**
   * Allows users of this class to add their own OnScrollListener
   */
  public void setOnScrollListener(AbsListView.OnScrollListener l) {
    scrollListener.setListener(l);
  }

  /**
   * OnScrollListener wrapper to detect when list position adjustment has completed
   */
  private class AdjustListScrollPositionListener implements AbsListView.OnScrollListener {

    boolean scrolling = false;
    boolean adjustingAndCapturing = false;
    AbsListView.OnScrollListener listener;

    @Override public void onScrollStateChanged(AbsListView view, int scrollState) {
      Log.i("@@@","onScrollStateChanged ");
      if (listener != null) {
        listener.onScrollStateChanged(view, scrollState);
      }
      if (!adjustingAndCapturing) {
        return;
      }

      //when scrolling is complete - resize
      if (scrolling && scrollState == SCROLL_STATE_IDLE) {
        adjustingAndCapturing = false;

        resizeListToTarget();

        listCaptureTemp.bitmap = captureListBitmap();
        if(listCapture != null){
          listCapture.recycle();
        }
        listCapture = listCaptureTemp;
        beginAnimation();
        return;
      }

      if (scrollState == SCROLL_STATE_FLING) {
        scrolling = true;
      }
    }

    @Override public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
        int totalItemCount) {
      if (listener != null) {
        listener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
      }
    }

    public void beginAdjustment() {
      adjustingAndCapturing = true;
    }

    public void setListener(AbsListView.OnScrollListener listener) {
      this.listener = listener;
    }
  }

  private void resizeListToTarget() {
    listView.measure(MeasureSpec.makeMeasureSpec(getWidth(),MeasureSpec.EXACTLY),
        (MeasureSpec.makeMeasureSpec(targetHeight,MeasureSpec.EXACTLY)));
    listView.layout(0,0,listView.getMeasuredWidth(),listView.getMeasuredHeight());
  }

  private class AnimatorRunnable implements Runnable {
    @Override
    public void run() {

      //Stop the animation if time has expired
      if (System.currentTimeMillis() - startTime > animDuration) {
        endAnimation();
      } else {
        invalidate(); //create a redraw and continue animation
        handler.postDelayed(this, FRAME_RATE);
      }
    }
  }

  private class AnimatingViewCapture {

    private Bitmap bitmap;

    private int srcStartY, srcEndY, dstStartY, dstEndY;

    private AnimatingViewCapture(Bitmap bitmap, int srcStartY, int srcEndY, int dstStartY,
        int dstEndY) {
      this.bitmap = bitmap;
      this.dstStartY = dstStartY;
      this.dstEndY = dstEndY;
      this.srcEndY = srcEndY;
      this.srcStartY = srcStartY;
    }

    void recycle() {
      if (bitmap != null) {
        bitmap.recycle();
        bitmap = null;
      }
    }

    /**
     * Draw a frame of animation to the canvas
     */
    void draw(Canvas canvas) {

      //statTime is before the animation has started, so draw the initial state
      float input = startTime == -1 ? 0f
          : (float) (System.currentTimeMillis() - startTime) / (float) animDuration;

      float interpolateValue = interpolator.getInterpolation(input);
      int srcY =
          (int) ((float) srcStartY + ((float) srcEndY - (float) srcStartY) * interpolateValue);
      int dstY =
          (int) ((float) dstStartY + ((float) dstEndY - (float) dstStartY) * interpolateValue);

      int srcBottom = bitmap.getHeight();

      if (bitmap.getHeight() > getHeight()) {
        srcBottom = getHeight();
      }

      Rect src = new Rect(0, srcY, bitmap.getWidth(), srcBottom);
      Rect dst = new Rect(0, dstY, bitmap.getWidth(), dstY + src.height());

      canvas.drawBitmap(bitmap, src, dst, null);

      //Log.i("@@@", "_______________________________________");
      //Log.i("@@@", "input:" + input + "|interpolateValue:" + interpolateValue);
      //Log.i("@@@", "**SRC :" + srcY + " | start:" + srcStartY + " end:" + srcEndY);
      //Log.i("@@@", "***DST " + dstY + " | start:" + dstStartY + " end:" + dstEndY);
      //Log.i("@@@", "***HEI src:" + src.height() + " | dst:" + dst.height()+" | view:"+getHeight()+" | bitmap:"+bitmap.getHeight());
    }
  }
}
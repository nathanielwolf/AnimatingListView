package com.couchsurfing.animatinglistview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Created by nathanielwolf on 9/24/13.
 */
public class AnimatingListView extends FrameLayout {
  private static final long FRAME_RATE = 1000 / 60;
  Interpolator interpolator;
  private int animDuration;
  private int[] childHeights;
  private int listContentsHeight;
  private int targetHeight;
  private long startTime;
  private int startHeight;
  private ListView listView;
  Handler handler = new Handler();

  private AnimatingViewCapture listCapture;
  private int scrollToAfterAnim;

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
    try {
      int defaultDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
      animDuration =
          a.getInteger(R.styleable.AnimatingListView_animation_duration, defaultDuration);
    } finally {
      a.recycle();
    }

    interpolator = new AccelerateDecelerateInterpolator();
    LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

    listView = new ListView(context);
    listView.setLayoutParams(params);
    listView.setBackgroundColor(Color.WHITE);

    addView(listView);
    //addView(screenShotView);
    setWillNotDraw(false);
  }

  public void setAdapter(ListAdapter adapter) {
    listView.setAdapter(adapter);
    measureList();
  }

  private void measureList(){
    ListAdapter adapter = listView.getAdapter();
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

      childHeights[i] = view.getMeasuredHeight() +  (i < adapter.getCount()-1?listView.getDividerHeight():0);
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
      srcStartY = Math.min(scrollPosition, delta);
      srcEndY = 0;
      dstStartY = delta;
      dstEndY = 0;

      //scroll the list down by the amount being revealed.
      final int scrollBy = srcEndY - srcStartY;

      //The list need to be scrolled before capture
      if (scrollBy != 0) {
        listCapture = new AnimatingViewCapture(null, srcStartY, srcEndY, dstStartY, dstEndY);
        repositionList(scrollPosition + scrollBy);
        //listen for the scroll to complete before beginning capturing and starting the animation
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
          @Override public boolean onPreDraw() {
            getViewTreeObserver().removeOnPreDrawListener(this);
            getLayoutParams().height = AnimatingListView.this.targetHeight;
            resizeListToTarget();
            listCapture.bitmap = captureListBitmap();
            listView.setVisibility(View.GONE);
            beginAnimation();
            return false;
          }
        });
        return;
      }

      getLayoutParams().height = targetHeight;
      resizeListToTarget();
    } else { //the list is shrinking
      srcStartY = 0;
      dstStartY = 0;
      dstEndY = -delta;

      //if the targetHeight is greater than the list height, the list must be pulled down
      if (listContentsHeight < startHeight) {

        //if the list is smaller than the targetHeight, do not scroll the capture
        srcEndY =
            listContentsHeight < targetHeight ? 0 : -delta - (startHeight - listContentsHeight);
      } else {
        //the list contents fill the frame, so just scroll the view down
        srcEndY = -delta;
      }

      //the list must be scrolled up after the size has changed
      scrollToAfterAnim = scrollPosition + srcEndY - srcStartY;
      //beginAnimation();
    }
    listView.setVisibility(View.GONE);
    listCapture =
        new AnimatingViewCapture(captureListBitmap(), srcStartY, srcEndY, dstStartY, dstEndY);
    beginAnimation();
  }

  private void repositionList(int scrollToY) {
    int sum = 0;
    Log.d("@@@", "**scrollToY " + scrollToY);
    for (int i = 0; i < childHeights.length; i++) {
      if (sum + childHeights[i] > scrollToY) {
        listView.setSelectionFromTop(i, sum - scrollToY);
        return;
      }
      sum += childHeights[i];
    }
  }

  public void beginAnimation() {
    startTime = System.currentTimeMillis();
    handler.postDelayed(new AnimatorRunnable(), 0);
  }

  private Bitmap captureListBitmap() {
    Bitmap bitmap =
        Bitmap.createBitmap(listView.getWidth(), listView.getHeight(), Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bitmap);
    listView.draw(c);

    return bitmap;
  }

  private void endAnimation() {
    if (listCapture != null) {
      listCapture.recycle();
      listCapture = null;
    }

    //when shrinking, the view must resize at the end, after it's resized
    if (startHeight > targetHeight) {
      getLayoutParams().height = targetHeight;
      getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
        @Override public boolean onPreDraw() {
          getViewTreeObserver().removeOnPreDrawListener(this);
          if (scrollToAfterAnim > 0) {
            repositionList(scrollToAfterAnim);
            scrollToAfterAnim = 0;
          }
          return false;
        }
      });
    }

    //this will generate a layout call
    listView.setVisibility(View.VISIBLE);
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (listCapture != null) {
      listCapture.draw(canvas);
    }
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

  private void resizeListToTarget() {
    listView.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
        (MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)));
    listView.layout(0, 0, listView.getMeasuredWidth(), listView.getMeasuredHeight());
  }

  private class AnimatorRunnable implements Runnable {
    @Override
    public void run() {

      //Stop the animation if time has expired
      if (System.currentTimeMillis() - startTime > animDuration) {
        endAnimation();
      } else { //continue animation
        invalidate();
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

      //statTime == -1 means this is before the animation has started, so draw the initial state
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
    }
  }
}
package com.couchsurfing.animatinglistview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Created by nathanielwolf on 9/24/13.
 */
public class AnimatingListView extends ListView {
  private static final long FRAME_RATE = 1000 / 60;
  Interpolator interpolator;
  private long animDuration;
  private int[] childHeights;
  private int listContentsHeight;
  private int targetHeight;
  private long startTime;
  private int startHeight;

  Handler handler = new Handler();

  private AnimatingDrawable animatingDrawable;
  private int scrollToAfterAnim;
  private Drawable cachedBackground;
  private boolean scrollBeforeCapture;
  private boolean capturing;

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
          (long) a.getInteger(R.styleable.AnimatingListView_animation_duration, defaultDuration);
    } finally {
      a.recycle();
    }

    interpolator = new AccelerateDecelerateInterpolator();
  }

  /**
   * Set the animation interpolator
   */
  public void setInterpolator(Interpolator interpolator) {
    this.interpolator = interpolator;
  }

  /**
   * Set the animation duration
   */
  public void setAnimationDuration(long duration) {
    animDuration = duration;
  }

  /**
   * Set the animation duration
   */
  public long getAnimationDuration() {
    return animDuration;
  }

  @Override
  public void setAdapter(ListAdapter adapter) {
    super.setAdapter(adapter);
    measureList();
  }

  private void measureList() {
    ListAdapter adapter = getAdapter();
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

      childHeights[i] =
          view.getMeasuredHeight() + (i < adapter.getCount() - 1 ? getDividerHeight() : 0);
      listContentsHeight += childHeights[i];
    }
  }

  /**
   * Animate the height of the list by the given offset
   *
   * @param offset pixels to animate the height by
   */
  public void animateBy(int offset) {
    animateTo(getHeight() + offset);
  }

  /**
   * Animate the height to the provided hieght
   *
   * @param targetHeight height in pixels to animate the list to
   */
  public void animateTo(int targetHeight) {
    if (animatingDrawable != null) {
      return;
    }

    this.targetHeight = targetHeight;
    this.startHeight = getHeight();

    int delta = targetHeight - startHeight;

    int scrollPosition = getListScrollPosition();

    //Y coordinates for the source (bitmap) and destination (view) that will be animated between
    int srcStartY, srcEndY, dstStartY, dstEndY;

    if (delta > 0) {//the list is growing

      //set the source and destination drawing values
      srcStartY = Math.min(scrollPosition, delta);
      srcEndY = 0;
      dstStartY = delta;
      dstEndY = 0;

      //scroll the list down by the amount being revealed.
      final int scrollBy = srcEndY - srcStartY;

      //Add a pre-draw listener to catch both the results of the setSelectionFromTop
      //and resize in order to capture a bitmap from listview
      getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
        @Override public boolean onPreDraw() {

          //ListView.setSelectionFromTop calls requestLayout to adjust the list
          //Catch the results before they are drawn, increase the height and request layout again
          if (scrollBeforeCapture) {
            getLayoutParams().height = AnimatingListView.this.targetHeight;
            scrollBeforeCapture = false;
            requestLayout();
            return false;
          }

          //capture the resized list, store into the drawable and start the animation
          getViewTreeObserver().removeOnPreDrawListener(this);
          animatingDrawable.bitmap = captureListBitmap();
          beginAnimation();
          return false;
        }
      });

      //init the caputure drawable without a bitmap
      animatingDrawable = new AnimatingDrawable(null, srcStartY, srcEndY, dstStartY, dstEndY);

      if (scrollBy != 0) {//The list need to be scrolled before capture
        scrollBeforeCapture = true;
        repositionList(scrollPosition + scrollBy);
      } else { //The list does not need to be scrolled
        getLayoutParams().height = targetHeight;
        requestLayout();
      }
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

      animatingDrawable =
          new AnimatingDrawable(captureListBitmap(), srcStartY, srcEndY, dstStartY, dstEndY);

      beginAnimation();
    }
  }

  /**
   * Moves the list to a specific position
   */
  private void repositionList(int scrollToY) {
    int sum = 0;
    for (int i = 0; i < childHeights.length; i++) {
      if (sum + childHeights[i] > scrollToY) {
        setSelectionFromTop(i, sum - scrollToY);
        return;
      }
      sum += childHeights[i];
    }
  }

  /**
   * Setup and start the animation and start
   */
  private void beginAnimation() {
    startTime = System.currentTimeMillis();
    //Cache the current list background to restore when the animation finishes
    cachedBackground = getBackground();

    //Set the animationDrawable as the new background
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      setBackground(animatingDrawable);
    } else {
      setBackgroundDrawable(animatingDrawable);
    }

    //start the animation
    handler.postDelayed(new AnimatorRunnable(), 0);
  }

  private Bitmap captureListBitmap() {
    Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bitmap);
    capturing = true;
    draw(c);
    capturing = false;

    return bitmap;
  }

  /**
   * Called to transition back to normal list operation
   */
  private void endAnimation() {
    if (animatingDrawable != null) {
      animatingDrawable.recycle();
      animatingDrawable = null;
    }

    //when shrinking, the view must be scrolled and resize at the end
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

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      setBackground(cachedBackground);
    } else {
      setBackgroundDrawable(cachedBackground);
    }

    requestLayout();
  }

  @Override protected void onDraw(Canvas canvas) {
    //when capturing == true, the provided canvas is from the bitmap for animatingDrawable
    if (animatingDrawable == null || capturing) {
      super.onDraw(canvas);
    } else {
      animatingDrawable.draw(canvas);
    }
  }

  @Override protected void dispatchDraw(Canvas canvas) {
    //when capturing == true, the provided canvas is from the bitmap for animatingDrawable
    if (animatingDrawable == null || capturing) {
      super.dispatchDraw(canvas);
    }
  }

  /**
   * Gets the absolute scroll position of the listview
   *
   * @return the scroll position
   */
  private int getListScrollPosition() {
    if (getChildCount() == 0) {
      return 0;
    }
    int currentY = 0;
    for (int i = 0; i < getFirstVisiblePosition(); i++) {
      currentY += childHeights[i];
    }
    return currentY - getChildAt(0).getTop();
  }

  /**
   * Runnable that runs the animation
   */
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

  /**
   * A drawable that will animate the drawing of the bitmap provided bitmap
   * given the source and destination y values
   */
  public class AnimatingDrawable extends Drawable {

    private Bitmap bitmap;

    private int srcStartY, srcEndY, dstStartY, dstEndY;

    public AnimatingDrawable(Bitmap bitmap, int srcStartY, int srcEndY, int dstStartY,
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
    @Override
    public void draw(Canvas canvas) {

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

    @Override public void setAlpha(int alpha) {

    }

    @Override public void setColorFilter(ColorFilter cf) {

    }

    @Override public int getOpacity() {
      return PixelFormat.OPAQUE;
    }
  }
}
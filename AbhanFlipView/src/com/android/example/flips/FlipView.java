package com.android.example.flips;

import java.util.LinkedList;
import java.util.Queue;
import com.android.example.R;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.ValueAnimator;
import com.nineoldandroids.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.Scroller;

public class FlipView extends FrameLayout {

	public interface OnFlipListener {
		public void onFlippedToPage(FlipView view, int position, long id);
	}

	static class Page {
		int position;
		View view;

		public Page(int position, View view) {
			this.position = position;
			this.view = view;
		}
	}

	private static final int PEAK_ANIM_DURATION = 1000;
	private static final int MAX_SINGLE_PAGE_FLIP_ANIM_DURATION = 650;
	private static final int FLIP_DISTANCE_PER_PAGE = 180;
	private static final int MAX_SHADOW_ALPHA = 180;
	private static final int MAX_SHADE_ALPHA = 130;
	private static final int MAX_SHINE_ALPHA = 100;
	private static final int INVALID_POINTER = -1;

	private DataSetObserver dataSetObserver = new DataSetObserver() {

		@Override
		public void onChanged() {
			dataSetChanged();
		}

		@Override
		public void onInvalidated() {
			dataSetInvalidated();
		}

	};

	private Scroller mScroller;
	private final Interpolator flipInterpolator = new DecelerateInterpolator();
	private ValueAnimator mPeakAnim;
	private TimeInterpolator mPeakInterpolator = new AccelerateDecelerateInterpolator();

	private boolean mIsFlippingVertically = true;
	private boolean mIsFlipping;
	private boolean mIsUnableToFlip;
	private boolean mIsFlippingEnabled = true;
	private boolean mLastTouchAllowed = true;
	private int mTouchSlop;

	private float mLastX = -1;
	private float mLastY = -1;
	private int mActivePointerId = INVALID_POINTER;

	private VelocityTracker mVelocityTracker;
	private int mMinimumVelocity;
	private int mMaximumVelocity;

	private Recycler mRecycler = new Recycler();
	private Queue<Page> mActivePageQueue = new LinkedList<FlipView.Page>();

	private ListAdapter mAdapter;
	private int mPageCount = 0;
	private OnFlipListener mOnFlipListener;

	private float mFlipDistance = 0;
	private int mCurrentPage = 0;

	private Rect mTopRect = new Rect();
	private Rect mBottomRect = new Rect();
	private Rect mRightRect = new Rect();
	private Rect mLeftRect = new Rect();

	private Camera mCamera = new Camera();
	private Matrix mMatrix = new Matrix();

	private Paint mShadowPaint = new Paint();
	private Paint mShadePaint = new Paint();
	private Paint mShinePaint = new Paint();

	private EdgeEffectCompat mPreviousEdgeEffect;
	private EdgeEffectCompat mNextEdgeEffect;

	public FlipView(Context context) {
		this(context, null);
	}

	public FlipView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public FlipView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		TypedArray typedArray = context.obtainStyledAttributes(attrs,
				R.styleable.FlipView);
		mIsFlippingVertically = typedArray.getInt(R.styleable.FlipView_orientation, 0) < 1;
		typedArray.recycle();
		init();
	}

	private void init() {
		final Context context = getContext();
		mScroller = new Scroller(context, flipInterpolator);
		final ViewConfiguration configuration = ViewConfiguration.get(context);
		mTouchSlop = configuration.getScaledPagingTouchSlop();
		mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

		mShadowPaint.setColor(Color.BLACK);
		mShadowPaint.setStyle(Style.FILL);
		mShadePaint.setColor(Color.BLACK);
		mShadePaint.setStyle(Style.FILL);
		mShinePaint.setColor(Color.WHITE);
		mShinePaint.setStyle(Style.FILL);

		mPreviousEdgeEffect = new EdgeEffectCompat(context);
		mNextEdgeEffect = new EdgeEffectCompat(context);
	}

	private void dataSetChanged() {
		mPageCount = mAdapter.getCount();
		removeAllViews();
		mActivePageQueue.clear();
		mRecycler.setViewTypeCount(mAdapter.getViewTypeCount());
		addView(viewForPage(mCurrentPage));
	}

	private void dataSetInvalidated() {
		if (mAdapter != null) {
			mAdapter.unregisterDataSetObserver(dataSetObserver);
			mAdapter = null;
		}
		mRecycler = new Recycler();
		removeAllViews();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int width = getDefaultSize(0, widthMeasureSpec);
		int height = getDefaultSize(0, heightMeasureSpec);
		measureChildren(widthMeasureSpec, heightMeasureSpec);
		setMeasuredDimension(width, height);
	}

	@Override
	protected void measureChildren(int widthMeasureSpec, int heightMeasureSpec) {
		int width = getDefaultSize(0, widthMeasureSpec);
		int height = getDefaultSize(0, heightMeasureSpec);

		int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width,
				MeasureSpec.EXACTLY);
		int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height,
				MeasureSpec.EXACTLY);
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			measureChild(child, childWidthMeasureSpec, childHeightMeasureSpec);
		}
	}

	@Override
	protected void measureChild(View child, int parentWidthMeasureSpec,
			int parentHeightMeasureSpec) {
		child.measure(parentWidthMeasureSpec, parentHeightMeasureSpec);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		layoutChildren();

		mTopRect.top = 0;
		mTopRect.left = 0;
		mTopRect.right = getWidth();
		mTopRect.bottom = getHeight() / 2;

		mBottomRect.top = getHeight() / 2;
		mBottomRect.left = 0;
		mBottomRect.right = getWidth();
		mBottomRect.bottom = getHeight();

		mLeftRect.top = 0;
		mLeftRect.left = 0;
		mLeftRect.right = getWidth() / 2;
		mLeftRect.bottom = getHeight();

		mRightRect.top = 0;
		mRightRect.left = getWidth() / 2;
		mRightRect.right = getWidth();
		mRightRect.bottom = getHeight();
	}

	private void layoutChildren() {
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			layoutChild(child);
		}
	}

	private void layoutChild(View child) {
		child.layout(0, 0, getWidth(), getHeight());
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent motionEvent) {

		if (!mIsFlippingEnabled) {
			return false;
		}

		final int action = motionEvent.getAction() & MotionEvent.ACTION_MASK;

		if (action == MotionEvent.ACTION_CANCEL
				|| action == MotionEvent.ACTION_UP) {
			mIsFlipping = false;
			mIsUnableToFlip = false;
			mActivePointerId = INVALID_POINTER;
			if (mVelocityTracker != null) {
				mVelocityTracker.recycle();
				mVelocityTracker = null;
			}
			return false;
		}

		if (action != MotionEvent.ACTION_DOWN) {
			if (mIsFlipping) {
				return true;
			} else if (mIsUnableToFlip) {
				return false;
			}
		}

		switch (action) {
		case MotionEvent.ACTION_MOVE:
			final int activePointerId = mActivePointerId;
			if (activePointerId == INVALID_POINTER) {
				break;
			}

			final int pointerIndex = MotionEventCompat.findPointerIndex(motionEvent,
					activePointerId);
			if (pointerIndex == -1) {
				mActivePointerId = INVALID_POINTER;
				break;
			}

			final float x = MotionEventCompat.getX(motionEvent, pointerIndex);
			final float dx = x - mLastX;
			final float xDiff = Math.abs(dx);
			final float y = MotionEventCompat.getY(motionEvent, pointerIndex);
			final float dy = y - mLastY;
			final float yDiff = Math.abs(dy);

			if ((mIsFlippingVertically && yDiff > mTouchSlop && yDiff > xDiff)
					|| (!mIsFlippingVertically && xDiff > mTouchSlop && xDiff > yDiff)) {
				mIsFlipping = true;
				mLastX = x;
				mLastY = y;
			} else if ((mIsFlippingVertically && xDiff > mTouchSlop)
					|| (!mIsFlippingVertically && yDiff > mTouchSlop)) {
				mIsUnableToFlip = true;
			}
			break;

		case MotionEvent.ACTION_DOWN:
			mActivePointerId = motionEvent.getAction()
					& MotionEvent.ACTION_POINTER_INDEX_MASK;
			mLastX = MotionEventCompat.getX(motionEvent, mActivePointerId);
			mLastY = MotionEventCompat.getY(motionEvent, mActivePointerId);

			mIsFlipping = !mScroller.isFinished() | mPeakAnim != null;
			mIsUnableToFlip = false;
			mLastTouchAllowed = true;

			break;
		case MotionEventCompat.ACTION_POINTER_UP:
			onSecondaryPointerUp(motionEvent);
			break;
		}

		if (!mIsFlipping) {
			trackVelocity(motionEvent);
		}

		return mIsFlipping;
	}

	@Override
	public boolean onTouchEvent(MotionEvent motionEvent) {

		if (!mIsFlippingEnabled || !mIsFlipping && !mLastTouchAllowed) {
			return false;
		}

		final int action = motionEvent.getAction();

		if (action == MotionEvent.ACTION_UP
				|| action == MotionEvent.ACTION_CANCEL
				|| action == MotionEvent.ACTION_OUTSIDE) {
			mLastTouchAllowed = false;
		} else {
			mLastTouchAllowed = true;
		}

		trackVelocity(motionEvent);

		switch (action & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:

			if (endScroll() || endPeak()) {
				mIsFlipping = true;
			}

			mLastX = motionEvent.getX();
			mLastY = motionEvent.getY();
			mActivePointerId = MotionEventCompat.getPointerId(motionEvent, 0);
			break;
		case MotionEvent.ACTION_MOVE:
			if (!mIsFlipping) {
				final int pointerIndex = MotionEventCompat.findPointerIndex(motionEvent,
						mActivePointerId);
				if (pointerIndex == -1) {
					mActivePointerId = INVALID_POINTER;
					break;
				}
				final float x = MotionEventCompat.getX(motionEvent, pointerIndex);
				final float xDiff = Math.abs(x - mLastX);
				final float y = MotionEventCompat.getY(motionEvent, pointerIndex);
				final float yDiff = Math.abs(y - mLastY);
				if ((mIsFlippingVertically && yDiff > mTouchSlop && yDiff > xDiff)
						|| (!mIsFlippingVertically && xDiff > mTouchSlop && xDiff > yDiff)) {
					mIsFlipping = true;
					mLastX = x;
					mLastY = y;
				}
			}
			if (mIsFlipping) {
				final int activePointerIndex = MotionEventCompat
						.findPointerIndex(motionEvent, mActivePointerId);
				if (activePointerIndex == -1) {
					mActivePointerId = INVALID_POINTER;
					break;
				}
				final float x = MotionEventCompat.getX(motionEvent, activePointerIndex);
				final float deltaX = mLastX - x;
				final float y = MotionEventCompat.getY(motionEvent, activePointerIndex);
				final float deltaY = mLastY - y;
				mLastX = x;
				mLastY = y;

				float deltaFlipDistance = 0;
				if (mIsFlippingVertically) {
					deltaFlipDistance = deltaY;
				} else {
					deltaFlipDistance = deltaX;
				}

				deltaFlipDistance /= ((isFlippingVertically() ? getHeight()
						: getWidth()) / FLIP_DISTANCE_PER_PAGE);
				mFlipDistance += deltaFlipDistance;

				float distanceBound = bindFlipDistance();
				if (distanceBound > 0) {
					mNextEdgeEffect.onPull(distanceBound
							/ (isFlippingVertically() ? getHeight()
									: getWidth()));
				} else if (distanceBound < 0) {
					mPreviousEdgeEffect.onPull(-distanceBound
							/ (isFlippingVertically() ? getHeight()
									: getWidth()));
				}
				invalidate();
			}
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (mIsFlipping) {
				final VelocityTracker velocityTracker = mVelocityTracker;
				velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);

				int velocity = 0;
				if (isFlippingVertically()) {
					velocity = (int) VelocityTrackerCompat.getYVelocity(
							velocityTracker, mActivePointerId);
				} else {
					velocity = (int) VelocityTrackerCompat.getXVelocity(
							velocityTracker, mActivePointerId);
				}
				smoothFlipTo(getNextPage(velocity));

				mActivePointerId = INVALID_POINTER;
				endFlip();

				mNextEdgeEffect.onRelease();
				mPreviousEdgeEffect.onRelease();
			}
			break;
		case MotionEventCompat.ACTION_POINTER_DOWN: {
			final int index = MotionEventCompat.getActionIndex(motionEvent);
			final float x = MotionEventCompat.getX(motionEvent, index);
			final float y = MotionEventCompat.getY(motionEvent, index);
			mLastX = x;
			mLastY = y;
			mActivePointerId = MotionEventCompat.getPointerId(motionEvent, index);
			break;
		}
		case MotionEventCompat.ACTION_POINTER_UP:
			onSecondaryPointerUp(motionEvent);
			final int index = MotionEventCompat.findPointerIndex(motionEvent,
					mActivePointerId);
			final float x = MotionEventCompat.getX(motionEvent, index);
			final float y = MotionEventCompat.getY(motionEvent, index);
			mLastX = x;
			mLastY = y;
			break;
		}
		if (mActivePointerId == INVALID_POINTER) {
			mLastTouchAllowed = false;
		}
		return true;
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {

		if (mPageCount < 1) {
			return;
		}

		boolean needsInvalidate = false;

		if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
			mFlipDistance = mScroller.getCurrY();
			needsInvalidate = true;
		}

		if (mIsFlipping || !mScroller.isFinished() || mPeakAnim != null) {
			drawPreviousHalf(canvas);
			drawNextHalf(canvas);
			drawFlippingHalf(canvas);
		} else {
			endScroll();
			final int currentPage = getCurrentPageFloor();
			if (mCurrentPage != currentPage) {
				postRemoveView(getChildAt(0));
			}
			final View view = viewForPage(currentPage);
			if (mCurrentPage != currentPage) {
				postAddView(view);
				postFlippedToPage(currentPage);
				mCurrentPage = currentPage;
			}
			view.draw(canvas);
		}

		needsInvalidate |= drawEdgeEffects(canvas);

		if (needsInvalidate) {
			invalidate();
		}
	}

	private boolean drawEdgeEffects(Canvas canvas) {
		return drawPreviousEdgeEffect(canvas) | drawNextEdgeEffect(canvas);
	}

	private boolean drawNextEdgeEffect(Canvas canvas) {
		boolean needsMoreDrawing = false;
		if (!mNextEdgeEffect.isFinished()) {
			canvas.save();
			if (isFlippingVertically()) {
				mNextEdgeEffect.setSize(getWidth(), getHeight());
				canvas.rotate(180);
				canvas.translate(-getWidth(), -getHeight());
			} else {
				mNextEdgeEffect.setSize(getHeight(), getWidth());
				canvas.rotate(90);
				canvas.translate(0, -getWidth());
			}
			needsMoreDrawing = mNextEdgeEffect.draw(canvas);
			canvas.restore();
		}
		return needsMoreDrawing;
	}

	private boolean drawPreviousEdgeEffect(Canvas canvas) {
		boolean needsMoreDrawing = false;
		if (!mPreviousEdgeEffect.isFinished()) {
			canvas.save();
			if (isFlippingVertically()) {
				mPreviousEdgeEffect.setSize(getWidth(), getHeight());
				canvas.rotate(0);
			} else {
				mPreviousEdgeEffect.setSize(getHeight(), getWidth());
				canvas.rotate(270);
				canvas.translate(-getHeight(), 0);
			}
			needsMoreDrawing = mPreviousEdgeEffect.draw(canvas);
			canvas.restore();
		}
		return needsMoreDrawing;
	}

	private void drawPreviousHalf(Canvas canvas) {
		final View view = viewForPage(getCurrentPageFloor());
		canvas.save();
		canvas.clipRect(isFlippingVertically() ? mTopRect : mLeftRect);
		view.draw(canvas);
		drawPreviousShadow(canvas);
		canvas.restore();
	}

	private void drawPreviousShadow(Canvas canvas) {
		final float degreesFlipped = getDegreesFlipped();
		if (degreesFlipped > 90) {
			final int alpha = (int) (((degreesFlipped - 90) / 90f) * MAX_SHADOW_ALPHA);
			mShadowPaint.setAlpha(alpha);
			canvas.drawPaint(mShadowPaint);
		}
	}

	private void drawNextHalf(Canvas canvas) {
		final View view = viewForPage(getCurrentPageCeil());
		canvas.save();
		canvas.clipRect(isFlippingVertically() ? mBottomRect : mRightRect);
		view.draw(canvas);
		drawNextShadow(canvas);
		canvas.restore();
	}

	private void drawNextShadow(Canvas canvas) {
		final float degreesFlipped = getDegreesFlipped();
		if (degreesFlipped < 90) {
			final int alpha = (int) ((Math.abs(degreesFlipped - 90) / 90f) * MAX_SHADOW_ALPHA);
			mShadowPaint.setAlpha(alpha);
			canvas.drawPaint(mShadowPaint);
		}
	}

	private void drawFlippingHalf(Canvas canvas) {
		final View view = viewForPage(getCurrentPageRound());
		final float degreesFlipped = getDegreesFlipped();
		canvas.save();
		mCamera.save();

		if (degreesFlipped > 90) {
			canvas.clipRect(isFlippingVertically() ? mTopRect : mLeftRect);
			if (mIsFlippingVertically) {
				mCamera.rotateX(degreesFlipped - 180);
			} else {
				mCamera.rotateY(180 - degreesFlipped);
			}
		} else {
			canvas.clipRect(isFlippingVertically() ? mBottomRect : mRightRect);
			if (mIsFlippingVertically) {
				mCamera.rotateX(degreesFlipped);
			} else {
				mCamera.rotateY(-degreesFlipped);
			}
		}

		mCamera.getMatrix(mMatrix);
		positionMatrix();
		canvas.concat(mMatrix);
		view.draw(canvas);
		drawFlippingShadeShine(canvas);
		mCamera.restore();
		canvas.restore();
	}

	private void drawFlippingShadeShine(Canvas canvas) {
		final float degreesFlipped = getDegreesFlipped();
		if (degreesFlipped < 90) {
			final int alpha = (int) ((degreesFlipped / 90f) * MAX_SHINE_ALPHA);
			mShinePaint.setAlpha(alpha);
			canvas.drawRect(isFlippingVertically() ? mBottomRect : mRightRect,
					mShinePaint);
		} else {
			final int alpha = (int) ((Math.abs(degreesFlipped - 180) / 90f) * MAX_SHADE_ALPHA);
			mShadePaint.setAlpha(alpha);
			canvas.drawRect(isFlippingVertically() ? mTopRect : mLeftRect,
					mShadePaint);
		}
	}

	private void positionMatrix() {
		mMatrix.preScale(0.25f, 0.25f);
		mMatrix.postScale(4.0f, 4.0f);
		mMatrix.preTranslate(-getWidth() / 2, -getHeight() / 2);
		mMatrix.postTranslate(getWidth() / 2, getHeight() / 2);
	}

	private float getDegreesFlipped() {
		final float localFlipDistance = mFlipDistance % FLIP_DISTANCE_PER_PAGE;
		return (localFlipDistance / FLIP_DISTANCE_PER_PAGE) * 180;
	}

	private View viewForPage(int page) {
		final int viewType = mAdapter.getItemViewType(page);

		View view = getActiveView(page);
		if (view != null) {
			return view;
		}

		view = mRecycler.getScrapView(page, viewType);
		view = mAdapter.getView(page, view, this);
		addToActiveView(view, page, viewType);
		measureAndLayoutChild(view);

		return view;
	}

	private void measureAndLayoutChild(View view) {
		int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(getWidth(),
				MeasureSpec.EXACTLY);
		int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(getHeight(),
				MeasureSpec.EXACTLY);
		measureChild(view, childWidthMeasureSpec, childHeightMeasureSpec);
		layoutChild(view);
	}

	private void addToActiveView(View passedView, int page, int viewType) {
		mActivePageQueue.add(new Page(page, passedView));
		if (mActivePageQueue.size() > 3) {
			final View view = mActivePageQueue.remove().view;
			mRecycler.addScrapView(view, page, viewType);
		}
	}

	private View getActiveView(int position) {
		Page page = null;
		for (Page iPage : mActivePageQueue) {
			if (iPage.position == position) {
				page = iPage;
			}
		}
		if (page != null) {
			mActivePageQueue.remove(page);
			mActivePageQueue.add(page);
			return page.view;
		}
		return null;
	}

	private void postAddView(final View view) {
		post(new Runnable() {

			@Override
			public void run() {
				addView(view);
			}
		});
	}

	private void postRemoveView(final View view) {
		post(new Runnable() {

			@Override
			public void run() {
				removeView(view);
			}
		});
	}

	private void postFlippedToPage(final int page) {
		post(new Runnable() {

			@Override
			public void run() {
				mOnFlipListener.onFlippedToPage(FlipView.this, page,
						mAdapter.getItemId(page));
			}
		});
	}

	private float bindFlipDistance() {
		final int minFlipDistance = 0;
		final int maxFlipDistance = (mPageCount - 1) * FLIP_DISTANCE_PER_PAGE;
		final float flipDistanceBeforeBinding = mFlipDistance;
		if (mFlipDistance < minFlipDistance) {
			mFlipDistance = 0;
		} else if (mFlipDistance > maxFlipDistance) {
			mFlipDistance = maxFlipDistance;
		}
		return flipDistanceBeforeBinding - mFlipDistance;
	}

	private void onSecondaryPointerUp(MotionEvent ev) {
		final int pointerIndex = MotionEventCompat.getActionIndex(ev);
		final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
		if (pointerId == mActivePointerId) {
			final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
			mLastX = MotionEventCompat.getX(ev, newPointerIndex);
			mActivePointerId = MotionEventCompat.getPointerId(ev,
					newPointerIndex);
			if (mVelocityTracker != null) {
				mVelocityTracker.clear();
			}
		}
	}

	private int getFlipDuration(int deltaFlipDistance) {
		float distance = Math.abs(deltaFlipDistance);
		return (int) (MAX_SINGLE_PAGE_FLIP_ANIM_DURATION * Math.sqrt(distance
				/ FLIP_DISTANCE_PER_PAGE));
	}

	private int getNextPage(int velocity) {
		if (velocity > mMinimumVelocity) {
			return getCurrentPageFloor();
		} else if (velocity < -mMinimumVelocity) {
			return getCurrentPageCeil();
		} else {
			return getCurrentPageRound();
		}
	}

	private int getCurrentPageRound() {
		return Math.round(mFlipDistance / FLIP_DISTANCE_PER_PAGE);
	}

	private int getCurrentPageFloor() {
		return (int) Math.floor(mFlipDistance / FLIP_DISTANCE_PER_PAGE);
	}

	private int getCurrentPageCeil() {
		return (int) Math.ceil(mFlipDistance / FLIP_DISTANCE_PER_PAGE);
	}

	private boolean endFlip() {
		final boolean wasflipping = mIsFlipping;
		mIsFlipping = false;
		mIsUnableToFlip = false;
		mLastTouchAllowed = false;

		if (mVelocityTracker != null) {
			mVelocityTracker.recycle();
			mVelocityTracker = null;
		}
		return wasflipping;
	}

	private boolean endScroll() {
		final boolean wasScrolling = !mScroller.isFinished();
		mScroller.abortAnimation();
		return wasScrolling;
	}

	private boolean endPeak() {
		final boolean wasPeaking = mPeakAnim != null;
		if (mPeakAnim != null) {
			mPeakAnim.cancel();
			mPeakAnim = null;
		}
		return wasPeaking;
	}

	private void peak(boolean next, boolean once) {
		final float baseFlipDistance = mCurrentPage * FLIP_DISTANCE_PER_PAGE;
		if (next) {
			mPeakAnim = ValueAnimator.ofFloat(baseFlipDistance,
					baseFlipDistance + FLIP_DISTANCE_PER_PAGE / 4);
		} else {
			mPeakAnim = ValueAnimator.ofFloat(baseFlipDistance,
					baseFlipDistance - FLIP_DISTANCE_PER_PAGE / 4);
		}
		mPeakAnim.setInterpolator((Interpolator) mPeakInterpolator);
		mPeakAnim.addUpdateListener(new AnimatorUpdateListener() {

			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				mFlipDistance = (Float) animation.getAnimatedValue();
				invalidate();
			}
		});
		mPeakAnim.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				endPeak();
			}
		});
		mPeakAnim.setDuration(PEAK_ANIM_DURATION);
		mPeakAnim.setRepeatMode(ValueAnimator.REVERSE);
		mPeakAnim.setRepeatCount(once ? 1 : ValueAnimator.INFINITE);
		mPeakAnim.start();
	}

	private void trackVelocity(MotionEvent ev) {
		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
		mVelocityTracker.addMovement(ev);
	}

	public void setAdapter(ListAdapter adapter) {
		if (mAdapter != null) {
			mAdapter.unregisterDataSetObserver(dataSetObserver);
			mAdapter = null;
		}
		removeAllViews();
		mActivePageQueue.clear();
		if (adapter != null) {
			mAdapter = adapter;
			mPageCount = mAdapter.getCount();
			mAdapter.registerDataSetObserver(dataSetObserver);
			mRecycler.setViewTypeCount(mAdapter.getViewTypeCount());
			addView(viewForPage(mCurrentPage));
		} else {
			mPageCount = 0;
		}
	}

	public ListAdapter getAdapter() {
		return mAdapter;
	}

	public int getPageCount() {
		return mPageCount;
	}

	public int getCurrentPage() {
		return mCurrentPage;
	}

	public void flipTo(int page) {
		if (page < 0 || page > mPageCount - 1) {
			throw new IllegalArgumentException("That page does not exist");
		}
		mFlipDistance = page * FLIP_DISTANCE_PER_PAGE;
		invalidate();
	}

	public void smoothFlipTo(int page) {
		if (page < 0 || page > mPageCount - 1) {
			throw new IllegalArgumentException("That page does not exist");
		}
		final int start = (int) mFlipDistance;
		final int delta = page * FLIP_DISTANCE_PER_PAGE - start;

		mScroller.startScroll(0, start, 0, delta, getFlipDuration(delta));
		invalidate();
	}

	public void peakNext(boolean once) {
		if (mCurrentPage < mPageCount - 1) {
			peak(true, once);
		}
	}

	public void peakPrevious(boolean once) {
		if (mCurrentPage > 0) {
			peak(false, once);
		}
	}

	public boolean isFlippingVertically() {
		return mIsFlippingVertically;
	}

	public void setOnFlipListener(OnFlipListener onFlipListener) {
		mOnFlipListener = onFlipListener;
	}
}
package com.vodafone.glitchtest.ui.main

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.NestedScrollingChild
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.NestedScrollType
import androidx.customview.widget.ViewDragHelper
import com.vodafone.glitchtest.R
import java.lang.ref.WeakReference
import java.util.*

class TabBehavior<V : View> : CoordinatorLayout.Behavior<V> {

    /**
     * Callback for monitoring events about bottom sheets.
     */
    abstract class BottomSheetCallback {
        /**
         * Called when the bottom sheet changes its state.
         *
         * @param bottomSheet The bottom sheet view.
         * @param newState    The new state. This will be one of [.STATE_DRAGGING],
         * [.STATE_ANCHOR_POINT], [.STATE_EXPANDED],
         * [.STATE_COLLAPSED], or [.STATE_HIDDEN].
         */
        abstract fun onStateChanged(
            bottomSheet: View,
            @State newState: Int
        )

        /**
         * Called when the bottom sheet is being dragged.
         *
         * @param bottomSheet The bottom sheet view.
         * @param slideOffset The new offset of this bottom sheet within its range, from 0 to 1
         * when it is moving upward, and from 0 to -1 when it moving downward.
         */
        abstract fun onSlide(bottomSheet: View, slideOffset: Float)
    }

    companion object {
        /** The bottom sheet is dragging. */
        const val STATE_DRAGGING = 1

        /** The bottom sheet is blocked in anchor point */
        const val STATE_BLOCKED = 2

        /** The bottom sheet is expanded_half_way.*/
        const val STATE_ANCHOR_POINT = 3

        /** The bottom sheet is expanded./ */
        const val STATE_EXPANDED = 4

        /** The bottom sheet is collapsed. */
        const val STATE_COLLAPSED = 5

        /** The bottom sheet is hidden. */
        const val STATE_HIDDEN = 6

        /**
         * A utility function to get the [TabBehavior] associated with the `view`.
         *
         * @param view The [View] with [TabBehavior].
         * @param <V>  Instance of behavior
         * @return The [TabBehavior] associated with the `view`.
        </V> */
        fun <V : View> from(view: V): TabBehavior<V> {
            val params = view.layoutParams
            require(params is CoordinatorLayout.LayoutParams) { "The view is not a child of CoordinatorLayout" }
            val behavior = params
                .behavior
            require(behavior is TabBehavior<*>) { "The view is not associated with TabBehaviorOrigin" }
            return behavior as TabBehavior<V>
        }

    }

    @IntDef(
        STATE_EXPANDED,
        STATE_BLOCKED,
        STATE_COLLAPSED,
        STATE_DRAGGING,
        STATE_ANCHOR_POINT,
        STATE_HIDDEN
    )
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class State

    private val HIDE_THRESHOLD = 0.5f
    private val HIDE_FRICTION = 0.1f

    private var minimumVelocity = 0f

    private var peekHeight = 0

    private var minOffset = 0
    private var maxOffset = 0

    private val DEFAULT_ANCHOR_POINT = 700
    private var anchorPoint = 0

    private var hideable = false

    private var collapsible = false

    @State
    private var _state = STATE_ANCHOR_POINT

    @State
    private var lastStableState = STATE_ANCHOR_POINT

    private var viewDragHelper: ViewDragHelper? = null

    private val ignoreEvents = false

    private var nestedScrolled = false

    private var parentHeight = 0

    private var viewRef: WeakReference<V>? = null

    private var nestedScrollingChildRef: WeakReference<View>? = null

    private var callback: Vector<BottomSheetCallback>? = null

    private val activePointerId = 0

    private val initialY = 0

    private val touchingScrollingChild = false

    constructor()

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        var a = context.obtainStyledAttributes(
            attrs,
            R.styleable.BottomSheetBehavior_Layout
        )
        setPeekHeight(
            a.getDimensionPixelSize(
                R.styleable.BottomSheetBehavior_Layout_behavior_peekHeight, 0
            )
        )
        setHideable(a.getBoolean(R.styleable.BottomSheetBehavior_Layout_behavior_hideable, false))
        a.recycle()

        /**
         * Getting the anchorPoint...
         */
        /**
         * Getting the anchorPoint...
         */
        anchorPoint = DEFAULT_ANCHOR_POINT
        collapsible = true
        a = context.obtainStyledAttributes(attrs, R.styleable.TabBehaviour)
        if (attrs != null) {
            anchorPoint =
                a.getDimension(R.styleable.TabBehaviour_anchorTabPoint, 0f).toInt()
            _state = a.getInt(
                R.styleable.TabBehaviour_defaultTabState,
                STATE_ANCHOR_POINT
            )
        }
        a.recycle()

        val configuration = ViewConfiguration.get(context)
        minimumVelocity = configuration.scaledMinimumFlingVelocity.toFloat()
    }

    override fun onSaveInstanceState(parent: CoordinatorLayout, child: V): Parcelable {
        return SavedState(
            super.onSaveInstanceState(parent, child),
            _state
        )
    }

    override fun onRestoreInstanceState(parent: CoordinatorLayout, child: V, state: Parcelable) {
        val ss = state as SavedState
        super.onRestoreInstanceState(parent, child, ss.superState)
        // Intermediate states are restored as collapsed state
        this._state = if (ss.state == STATE_DRAGGING) {
            STATE_COLLAPSED
        } else {
            ss.state
        }
        lastStableState = this._state
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        // First let the parent lay it out
        if (_state != STATE_DRAGGING) {
            if (parent.fitsSystemWindows &&
                !child.fitsSystemWindows
            ) {
                child.fitsSystemWindows = true
            }
            parent.onLayoutChild(child, layoutDirection)
        }
        // Offset the bottom sheet
        parentHeight = parent.height
        minOffset = Math.max(0, parentHeight - child.height)
        maxOffset = Math.max(parentHeight - peekHeight, minOffset)
        /**
         * New behavior
         */
        if (_state == STATE_ANCHOR_POINT || _state == STATE_BLOCKED) {
            ViewCompat.offsetTopAndBottom(child, anchorPoint)
        } else if (_state == STATE_EXPANDED) {
            ViewCompat.offsetTopAndBottom(child, minOffset)
        } else if (hideable && _state == STATE_HIDDEN) {
            ViewCompat.offsetTopAndBottom(child, parentHeight)
        } else if (_state == STATE_COLLAPSED) {
            ViewCompat.offsetTopAndBottom(child, maxOffset)
        }
        if (viewDragHelper == null) {
            viewDragHelper = ViewDragHelper.create(parent, mDragCallback)
        }
        viewRef = WeakReference(child)
        nestedScrollingChildRef = WeakReference(findScrollingChild(child))
        return true
    }

    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        event: MotionEvent
    ): Boolean {
        return false
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        return false
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        directTargetChild: View,
        target: View,
        nestedScrollAxes: Int,
        @NestedScrollType type: Int
    ): Boolean {
        nestedScrolled = false
        return nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
    }

    private val mScrollVelocityTracker: ScrollVelocityTracker = ScrollVelocityTracker()

    private class ScrollVelocityTracker {
        private var mPreviousScrollTime: Long = 0
        var scrollVelocity = 0f
            private set

        fun recordScroll(dy: Int) {
            val now = System.currentTimeMillis()
            if (mPreviousScrollTime != 0L) {
                val elapsed = now - mPreviousScrollTime
                scrollVelocity = dy.toFloat() / elapsed * 1000 // pixels per sec
            }
            mPreviousScrollTime = now
        }

        fun clear() {
            mPreviousScrollTime = 0
            scrollVelocity = 0f
        }
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        @NestedScrollType type: Int
    ) {
        if (_state != STATE_BLOCKED) {
            val scrollingChild = nestedScrollingChildRef!!.get()
            if (target !== scrollingChild) {
                return
            }
            mScrollVelocityTracker.recordScroll(dy)
            val currentTop = child.top
            val newTop = currentTop - dy
            if (dy > 0) {
                if (newTop < minOffset) {
                    consumed[1] = currentTop - minOffset
                    ViewCompat.offsetTopAndBottom(child, -consumed[1])
                    setStateInternal(STATE_EXPANDED)
                } else {
                    consumed[1] = dy
                    ViewCompat.offsetTopAndBottom(child, -dy)
                    setStateInternal(STATE_DRAGGING)
                }
            } else if (dy < 0 && newTop <= anchorPoint) { // Downward
                if (!ViewCompat.canScrollVertically(target, -1)) {
                    if (newTop <= maxOffset || hideable) {
                        // Restrict STATE_COLLAPSED if restrictedState is set
                        if (collapsible == true || collapsible == false && anchorPoint - newTop >= 0) {
                            consumed[1] = dy
                            ViewCompat.offsetTopAndBottom(child, -dy)
                            if (newTop == anchorPoint) {
                                setStateInternal(STATE_ANCHOR_POINT)
                            } else {
                                setStateInternal(STATE_DRAGGING)
                            }
                        }
                    } else {
                        consumed[1] = currentTop - maxOffset
                        ViewCompat.offsetTopAndBottom(child, -consumed[1])
                        setStateInternal(STATE_COLLAPSED)
                    }
                }
            }
            dispatchOnSlide(child.top)
            nestedScrolled = true
        }
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        @NestedScrollType type: Int
    ) {
        nestedScrolled = false
    }

    override fun onNestedPreFling(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return target === nestedScrollingChildRef!!.get() &&
                (_state != STATE_EXPANDED ||
                        super.onNestedPreFling(
                            coordinatorLayout, child, target,
                            velocityX, velocityY
                        ))
    }

    /**
     * Sets the height of the bottom sheet when it is collapsed.
     *
     * @param peekHeight The height of the collapsed bottom sheet in pixels.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Params_behavior_peekHeight
     */
    fun setPeekHeight(peekHeight: Int) {
        this.peekHeight = Math.max(0, peekHeight)
        maxOffset = parentHeight - peekHeight
    }

    /**
     * Gets the height of the bottom sheet when it is collapsed.
     *
     * @return The height of the collapsed bottom sheet.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Params_behavior_peekHeight
     */
    fun getPeekHeight(): Int {
        return peekHeight
    }

    fun setAnchorPoint(anchorPoint: Int) {
        this.anchorPoint = anchorPoint
    }

    fun getAnchorPoint(): Int {
        return anchorPoint
    }

    /**
     * Sets whether this bottom sheet can hide when it is swiped down.
     *
     * @param hideable `true` to make this bottom sheet hideable.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Params_behavior_hideable
     */
    fun setHideable(hideable: Boolean) {
        this.hideable = hideable
    }

    /**
     * Sets whether some states should be skipped.
     *
     * @param collapsible `true` to make this bottom sheet hideable.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Params_behavior_hideable
     */
    fun setCollapsible(collapsible: Boolean) {
        this.collapsible = collapsible
    }

    /**
     * Gets whether some states should be skipped.
     *
     * @return `true` if this bottom sheet can hide.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Params_behavior_hideable
     */
    fun isCollapsible(): Boolean {
        return collapsible
    }

    /**
     * Adds a callback to be notified of bottom sheet events.
     *
     * @param callback The callback to notify when bottom sheet events occur.
     */
    fun addBottomSheetCallback(callback: BottomSheetCallback?) {
        if (this.callback == null) this.callback = Vector()
        this.callback?.add(callback)
    }

    /**
     * Sets the state of the bottom sheet. The bottom sheet will transition to that state with
     * animation.
     *
     * @param state One of [.STATE_COLLAPSED], [.STATE_ANCHOR_POINT],
     * [.STATE_EXPANDED] or [.STATE_HIDDEN].
     */
    @State
    var state: Int
        get() = _state
        set(value) {
            //        if (state == mState) {
//            return;
//        }
            /**
             * New behavior (added: state == STATE_ANCHOR_POINT ||)
             */
            if (value == STATE_COLLAPSED || value == STATE_EXPANDED || value == STATE_ANCHOR_POINT ||
                hideable && value == STATE_HIDDEN
            ) {
                _state = value
                lastStableState = value
            }
            val child = (if (viewRef == null) null else viewRef!!.get()) ?: return
            val top: Int = if (value == STATE_COLLAPSED) {
                maxOffset
            } else if (value == STATE_ANCHOR_POINT) {
                anchorPoint
            } else if (value == STATE_EXPANDED) {
                minOffset
            } else if (hideable && value == STATE_HIDDEN) {
                parentHeight
            } else if (value == STATE_BLOCKED) {
                anchorPoint
            } else {
                throw IllegalArgumentException("Illegal state argument: $value")
            }
            setStateInternal(value)
            if (viewDragHelper!!.smoothSlideViewTo(child, child.left, top)) {
                ViewCompat.postOnAnimation(
                    child,
                    SettleRunnable(child, value)
                )
            }

        }

    private fun setStateInternal(@State state: Int) {
//        if (mState == state) {
//            return;
//        }
        this._state = state
        val bottomSheet: View? = viewRef!!.get()
        if (bottomSheet != null && callback != null) {
            notifyStateChangedToListeners(bottomSheet, state)
        }
    }

    private fun notifyStateChangedToListeners(
        bottomSheet: View,
        @State newState: Int
    ) {
        for (bottomSheetCallback in callback!!) {
            bottomSheetCallback.onStateChanged(bottomSheet, newState)
        }
    }

    private fun notifyOnSlideToListeners(bottomSheet: View, slideOffset: Float) {
        for (bottomSheetCallback in callback!!) {
            bottomSheetCallback.onSlide(bottomSheet, slideOffset)
        }
    }

    private fun findScrollingChild(view: View): View? {
        if (view is NestedScrollingChild) {
            return view
        }
        if (view is ViewGroup) {
            val group = view
            var i = 0
            val count = group.childCount
            while (i < count) {
                val scrollingChild = findScrollingChild(group.getChildAt(i))
                if (scrollingChild != null) {
                    return scrollingChild
                }
                i++
            }
        }
        return null
    }

    private val mDragCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            if (_state == STATE_DRAGGING) {
                return false
            }
            if (touchingScrollingChild) {
                return false
            }
            if (_state == STATE_EXPANDED && activePointerId == pointerId) {
                val scroll = nestedScrollingChildRef!!.get()
                if (scroll != null && scroll.canScrollVertically(-1)) {
                    // Let the content scroll up
                    return false
                }
            }
            return viewRef != null && viewRef!!.get() === child
        }

        override fun onViewPositionChanged(
            changedView: View,
            left: Int,
            top: Int,
            dx: Int,
            dy: Int
        ) {
            dispatchOnSlide(top)
        }

        override fun onViewDragStateChanged(state: Int) {
            if (state == ViewDragHelper.STATE_DRAGGING) {
                setStateInternal(STATE_DRAGGING)
            }
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
//            int top;
//            @State int targetState;
//            if (yvel < 0) { // Moving up
//                top = mMinOffset;
//                targetState = STATE_EXPANDED;
//            } else if (mHideable && shouldHide(releasedChild, yvel)) {
//                top = mParentHeight;
//                targetState = STATE_HIDDEN;
//            } else if (yvel == 0.f) {
//                int currentTop = releasedChild.getTop();
//                if (Math.abs(currentTop - mMinOffset) < Math.abs(currentTop - mMaxOffset)) {
//                    top = mMinOffset;
//                    targetState = STATE_EXPANDED;
//                } else {
//                    top = mMaxOffset;
//                    targetState = STATE_COLLAPSED;
//                }
//            } else {
//                top = mMaxOffset;
//                targetState = STATE_COLLAPSED;
//            }
//
//            // Restrict Collapsed view (optional)
//            if (!mCollapsible && targetState == STATE_COLLAPSED) {
//                top = mAnchorPoint;
//                targetState = STATE_ANCHOR_POINT;
//            }
//
//            if (mViewDragHelper.settleCapturedViewAt(releasedChild.getLeft(), top)) {
////                setStateInternal(STATE_SETTLING);
//                ViewCompat.postOnAnimation(releasedChild,
//                        new SettleRunnable(releasedChild, targetState));
//            } else {
//                setStateInternal(targetState);
//            }
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            return constrain(top, minOffset, if (hideable) parentHeight else maxOffset)
        }

        fun constrain(amount: Int, low: Int, high: Int): Int {
            return if (amount < low) low else if (amount > high) high else amount
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            return child.left
        }

        override fun getViewVerticalDragRange(child: View): Int {
            return if (hideable) {
                parentHeight - minOffset
            } else {
                maxOffset - minOffset
            }
        }
    }

    private fun dispatchOnSlide(top: Int) {
        val bottomSheet: View? = viewRef!!.get()
        if (bottomSheet != null && callback != null) {
            if (top > maxOffset) {
                notifyOnSlideToListeners(bottomSheet, (maxOffset - top).toFloat() / peekHeight)
            } else {
                notifyOnSlideToListeners(
                    bottomSheet,
                    (maxOffset - top).toFloat() / (maxOffset - minOffset)
                )
            }
        }
    }

    private inner class SettleRunnable internal constructor(
        private val mView: View,
        @field:State @param:State private val mTargetState: Int
    ) : Runnable {
        override fun run() {
            if (viewDragHelper != null && viewDragHelper?.continueSettling(true) == true) {
                ViewCompat.postOnAnimation(mView, this)
            } else {
                setStateInternal(mTargetState)
            }
        }
    }

    private class SavedState : View.BaseSavedState {
        @State
        val state: Int

        constructor(source: Parcel) : super(source) {
            // noinspection ResourceType
            state = source.readInt()
        }

        constructor(superState: Parcelable?, @State state: Int) : super(
            superState
        ) {
            this.state = state
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(state)
        }

        companion object {
            @JvmField
            val CREATOR: Creator<SavedState> = object : Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }
}
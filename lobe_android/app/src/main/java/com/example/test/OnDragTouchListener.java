package com.example.test;

import android.app.Activity;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.RequiresApi;

import static java.lang.Thread.sleep;

public class OnDragTouchListener implements View.OnTouchListener {

    /**
     * Callback used to indicate when the drag is finished
     */
    public interface OnDragActionListener {
        /**
         * Called when drag event is started
         *
         * @param view The view dragged
         */
        void onDragStart(View view);

        /**
         * Called when drag event is completed
         *
         * @param view The view dragged
         */
        void onDragEnd(View view);
    }

    private ImageView mView;
    private View mParent;
    private boolean isDragging;
    private boolean isInitialized = false;

    private int width;
    private float xWhenAttached;
    private float maxLeft;
    private float maxRight;
    private float dX;

    private int height;
    private float yWhenAttached;
    private float maxTop;
    private float maxBottom;
    private float dY;
    CameraActivity mAct;

    private OnDragActionListener mOnDragActionListener;

    public OnDragTouchListener(ImageView view, Activity act) {
        this(view, (View) view.getParent(), act, null);
    }

    public OnDragTouchListener(ImageView view, View parent, Activity act, OnDragActionListener onDragActionListener) {
        initListener(view, parent, act);
        setOnDragActionListener(onDragActionListener);
    }

    public void setOnDragActionListener(OnDragActionListener onDragActionListener) {
        mOnDragActionListener = onDragActionListener;
    }

    public void initListener(ImageView view, View parent, Activity act) {
        mAct = (CameraActivity) act;
        mView = view;
        mParent = parent;
        isDragging = false;
        isInitialized = false;
    }

    public void updateBounds() {
        updateViewBounds();
        updateParentBounds();
        isInitialized = true;
    }

    public void updateViewBounds() {
        width = mView.getWidth();
        xWhenAttached = mView.getX();
        dX = 0;

        height = mView.getHeight();
        yWhenAttached = mView.getY();
        dY = 0;
    }

    public void updateParentBounds() {
        maxLeft = 0;
        maxRight = maxLeft + mParent.getWidth();

        maxTop = 0;
        maxBottom = maxTop + mParent.getHeight();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        System.out.println(isDragging);
        if (isDragging) {
            float[] bounds = new float[4];
            bounds[0] = event.getRawX() + dX;
            bounds[2] = bounds[0] + width;
            bounds[1] = event.getRawY() + dY;
            bounds[3] = bounds[1] + height;
            switch (event.getAction()) {
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_DOWN:
                    mView.animate().scaleX((float) 0.3).alpha((float) 0).scaleY((float) 0.3).alpha((float) 0).x(0).y(maxBottom).setDuration(500).withEndAction(new Runnable() {
                        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                        @Override
                        public void run() {
                            mAct.setMode(false);
                        }
                    }).start();
                    break;
                case MotionEvent.ACTION_MOVE:
                    break;
            }
            return true;
        } else {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDragging = true;
                    if (!isInitialized) {
                        updateBounds();
                    }
                    dX = v.getX() - event.getRawX();
                    dY = v.getY() - event.getRawY();
                    if (mOnDragActionListener != null) {
                        mOnDragActionListener.onDragStart(mView);
                    }
                    return true;
            }
        }
        return false;
    }
}

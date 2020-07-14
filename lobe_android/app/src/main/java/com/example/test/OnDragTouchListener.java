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

//    public OnDragTouchListener(ImageView view, View parent) {
//        this(view, parent, null);
//    }
//
//    public OnDragTouchListener(ImageView view, OnDragActionListener onDragActionListener) {
//        this(view, (View) view.getParent(), onDragActionListener);
//    }

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


//            ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) mView.getLayoutParams();
//            params.width = (int) (mView.getWidth() * 100 / event.getRawX());
//// existing height is ok as is, no need to edit it
//            mView.setLayoutParams(params);


            float[] bounds = new float[4];
            // LEFT
            bounds[0] = event.getRawX() + dX;
//            if (bounds[0] < maxLeft) {
//                bounds[0] = maxLeft;
//            }
//            // RIGHT
            bounds[2] = bounds[0] + width;
//            if (bounds[2] > maxRight) {
//                bounds[2] = maxRight;
//                bounds[0] = bounds[2] - width;
//            }
//            // TOP
            bounds[1] = event.getRawY() + dY;
//            if (bounds[1] < maxTop) {
//                bounds[1] = maxTop;
//            }
//            // BOTTOM
            bounds[3] = bounds[1] + height;
//            if (bounds[3] > maxBottom) {
//                bounds[3] = maxBottom;
//                bounds[1] = bounds[3] - height;
//            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_DOWN:
                    mView.animate().scaleX((float) 0.3).alpha((float) 0).scaleY((float) 0.3).alpha((float) 0).x(0).y(maxBottom).setDuration(500).withEndAction(new Runnable() {
                        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                        @Override
                        public void run() {
//                            mView.setImageBitmap(null);
                            mAct.setMode(false);
                        }
                    }).start();
//                    mView.animate().scaleX(1).setDuration(100).start();
//                                        onDragFinish();
                    break;
                case MotionEvent.ACTION_MOVE:
//                    mView.animate().scaleX((float) 10/event.getRawX()).alpha((float) 0.5).x(bounds[0]).y(bounds[1]).setDuration(500).start();
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

    private void onDragFinish(){
//        mView.setVisibility(0);
//        System.out.println("hahaha");
//        mView.animate().scaleX(1).scaleY(1).start();
//        try {
//            sleep(500);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        mView.setImageBitmap(null);
//        if (mOnDragActionListener != null) {
//            mOnDragActionListener.onDragEnd(mView);
//        }

        dX = 0;
        dY = 0;
        isDragging = false;
    }
}

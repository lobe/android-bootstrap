package com.example.test;

import android.content.Context;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.core.view.MotionEventCompat;

import static android.view.MotionEvent.INVALID_POINTER_ID;

public class OnSwipeTouchListener implements View.OnTouchListener {

  private final GestureDetector gestureDetector;

  public OnSwipeTouchListener(Context ctx){
    gestureDetector = new GestureDetector(ctx, new GestureListener());
  }

  Handler handler = new Handler();

  int numberOfTaps = 0;
  long lastTapTimeMs = 0;
  long touchDownMs = 0;



  @Override
  public boolean onTouch(View v, MotionEvent event) {

    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        touchDownMs = System.currentTimeMillis();
        break;
      case MotionEvent.ACTION_UP:
        handler.removeCallbacksAndMessages(null);

        if ((System.currentTimeMillis() - touchDownMs) > ViewConfiguration.getTapTimeout()) {
          //it was not a tap

          numberOfTaps = 0;
          lastTapTimeMs = 0;
          break;
        }

        if (numberOfTaps > 0
                && (System.currentTimeMillis() - lastTapTimeMs) < ViewConfiguration.getDoubleTapTimeout()) {
          numberOfTaps += 1;
        } else {
          numberOfTaps = 1;
        }

        lastTapTimeMs = System.currentTimeMillis();

        if (numberOfTaps == 3) {
          System.out.println( "tttttttttttttttttttripletap");
          //handle triple tap
          tripleTap();
        } else if (numberOfTaps == 2) {
          handler.postDelayed(new Runnable() {
            @Override
            public void run() {
              //handle double tap
              System.out.println( "onDoubleTappppppppppppppppppppp");
              doubleTap();
            }
          }, ViewConfiguration.getDoubleTapTimeout());
        }
    }


    return gestureDetector.onTouchEvent(event);
  }

  private final class GestureListener extends GestureDetector.SimpleOnGestureListener {



    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    @Override
    public boolean onDown(MotionEvent e) {
      return true;
    }

//    @Override
//    public boolean onDoubleTap(MotionEvent e) {
//      System.out.println( "onDoubleTappppppppppppppppppppp");
//      doubleTap();
//      return super.onDoubleTap(e);
//    }


    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
      boolean result = false;
      try {
        float diffY = e2.getY() - e1.getY();
        float diffX = e2.getX() - e1.getX();
        if (Math.abs(diffX) > Math.abs(diffY)) {
          if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
            if (diffX > 0) {
              onSwipeRight();
            } else {
              onSwipeLeft();
            }
            result = true;
          }
        }
        else if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
          if (diffY > 0) {
            onSwipeBottom();
          } else {
            onSwipeTop();
          }
          result = true;
        }
      } catch (Exception exception) {
        exception.printStackTrace();
      }
      return result;
    }
  }

  public void onSwipeRight() {
  }

  public void onSwipeLeft() {
  }

  public void onSwipeTop() {
  }

  public void onSwipeBottom() {
  }

  public void doubleTap() {
  }
  public void tripleTap(){
  }
}


//class hehe extends GestureDetector {
//  public hehe(Context context, OnGestureListener listener) {
//    super(context, listener);
//  }
//
//  private int mActivePointerId = INVALID_POINTER_ID;
//
//  @Override
//  public boolean onTouchEvent(MotionEvent ev) {
//    // Let the ScaleGestureDetector inspect all events.
//    mScaleDetector.onTouchEvent(ev);
//
//    final int action = MotionEventCompat.getActionMasked(ev);
//
//    switch (action) {
//      case MotionEvent.ACTION_DOWN: {
//        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
//        final float x = MotionEventCompat.getX(ev, pointerIndex);
//        final float y = MotionEventCompat.getY(ev, pointerIndex);
//
//        // Remember where we started (for dragging)
//        mLastTouchX = x;
//        mLastTouchY = y;
//        // Save the ID of this pointer (for dragging)
//        mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
//        break;
//      }
//
//      case MotionEvent.ACTION_MOVE: {
//        // Find the index of the active pointer and fetch its position
//        final int pointerIndex =
//                MotionEventCompat.findPointerIndex(ev, mActivePointerId);
//
//        final float x = MotionEventCompat.getX(ev, pointerIndex);
//        final float y = MotionEventCompat.getY(ev, pointerIndex);
//
//        // Calculate the distance moved
//        final float dx = x - mLastTouchX;
//        final float dy = y - mLastTouchY;
//
//        mPosX += dx;
//        mPosY += dy;
//
//        invalidate();
//
//        // Remember this touch position for the next move event
//        mLastTouchX = x;
//        mLastTouchY = y;
//
//        break;
//      }
//
//      case MotionEvent.ACTION_UP: {
//        mActivePointerId = INVALID_POINTER_ID;
//        break;
//      }
//
//      case MotionEvent.ACTION_CANCEL: {
//        mActivePointerId = INVALID_POINTER_ID;
//        break;
//      }
//
//      case MotionEvent.ACTION_POINTER_UP: {
//
//        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
//        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
//
//        if (pointerId == mActivePointerId) {
//          // This was our active pointer going up. Choose a new
//          // active pointer and adjust accordingly.
//          final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
//          mLastTouchX = MotionEventCompat.getX(ev, newPointerIndex);
//          mLastTouchY = MotionEventCompat.getY(ev, newPointerIndex);
//          mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
//        }
//        break;
//      }
//    }
//    return true;
//  }
//}

package com.moez.QKSMS.service;

import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import butterknife.Bind;
import com.moez.QKSMS.R;
import com.moez.QKSMS.common.LiveViewManager;
import com.moez.QKSMS.enums.QKPreference;
import com.moez.QKSMS.ui.ThemeManager;

public class QKReplyService extends Service {

    private WindowManager mWindowManager;

    @Bind(R.id.card) View mCard;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mCard = LayoutInflater.from(this).inflate(R.layout.view_qkreply, null);

        mCard.findViewById(R.id.compose_view).setBackgroundDrawable(null);

        LiveViewManager.registerView(key -> {
            mCard.getBackground().setColorFilter(ThemeManager.getBackgroundColor(), PorterDuff.Mode.MULTIPLY);
        }, this, QKPreference.BACKGROUND);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP;
        params.y = 100;

        int density = (int) (getResources().getDisplayMetrics().densityDpi / 160f);
        mCard.setOnTouchListener(new View.OnTouchListener() {
            private int initialY;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = params.y;
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        ValueAnimator animator = ValueAnimator.ofInt(params.y, 0);
                        animator.addUpdateListener(animation -> {
                            params.y = (int) animation.getAnimatedValue();
                            mWindowManager.updateViewLayout(mCard, params);
                        });
                        animator.setInterpolator(new AccelerateDecelerateInterpolator());
                        animator.setDuration(params.y / density);
                        animator.start();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        mWindowManager.updateViewLayout(mCard, params);
                        return true;
                }
                return false;
            }
        });

        mWindowManager.addView(mCard, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCard != null) {
            mWindowManager.removeView(mCard);
        }
    }
}
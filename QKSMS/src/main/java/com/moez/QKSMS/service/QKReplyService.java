package com.moez.QKSMS.service;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import butterknife.Bind;
import butterknife.ButterKnife;
import com.moez.QKSMS.R;
import com.moez.QKSMS.common.LiveViewManager;
import com.moez.QKSMS.common.utils.DateFormatter;
import com.moez.QKSMS.data.Contact;
import com.moez.QKSMS.enums.QKPreference;
import com.moez.QKSMS.ui.ThemeManager;
import com.moez.QKSMS.ui.view.AvatarView;
import com.moez.QKSMS.ui.view.ComposeView;
import com.moez.QKSMS.ui.view.QKEditText;
import com.moez.QKSMS.ui.view.QKTextView;

public class QKReplyService extends Service implements View.OnTouchListener {
    private final String TAG = "QKReplyService";

    private static boolean sIsOpen = false;

    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mParams;

    private Handler mHandler;
    private Runnable mDismissRunnable;

    @Bind(R.id.card) View mCard;
    @Bind(R.id.avatar) AvatarView mAvatar;
    @Bind(R.id.name) QKTextView mName;
    @Bind(R.id.message) QKTextView mMessage;
    @Bind(R.id.date) QKTextView mDateView;
    @Bind(R.id.compose_view) ComposeView mComposeView;
    @Bind(R.id.compose_reply_text) QKEditText mReplyText;

    private int mDensity;
    private int mInitialY;
    private float mInitialTouchY;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (sIsOpen) {
            return super.onStartCommand(intent, flags, startId);
        }
        sIsOpen = true;

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mDensity = (int) (getResources().getDisplayMetrics().densityDpi / 160f);
        mHandler = new Handler();
        mDismissRunnable = this::dismiss;

        mCard = LayoutInflater.from(this).inflate(R.layout.view_qkreply, null);
        ButterKnife.bind(this, mCard);

        mComposeView.setBackgroundDrawable(null);

        LiveViewManager.registerView(key -> {
            mCard.getBackground().setColorFilter(ThemeManager.getBackgroundColor(), PorterDuff.Mode.MULTIPLY);
        }, this, QKPreference.BACKGROUND);

        String address = intent.getStringExtra("address");
        String body = intent.getStringExtra("body");
        long date = intent.getLongExtra("date", 0);
        Contact contact = Contact.get(address, true);

        mAvatar.setImageDrawable(contact.getAvatar(this, null));
        mName.setText(contact.getName());
        mMessage.setText(body);
        mDateView.setText(DateFormatter.getMessageTimestamp(this, date));

        mParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        mParams.gravity = Gravity.TOP;

        mCard.setOnTouchListener(this);

        mReplyText.setOnClickListener(v -> {
            mParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            mWindowManager.updateViewLayout(mCard, mParams);
            mReplyText.setOnClickListener(null);

            mHandler.removeCallbacks(mDismissRunnable);
        });

        mReplyText.setKeyboardDismissedListener(() -> mHandler.postDelayed(mDismissRunnable, 3000));

        mWindowManager.addView(mCard, mParams);

        mCard.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mParams.y = -mCard.getHeight();
                mWindowManager.updateViewLayout(mCard, mParams);
                appear();

                ViewTreeObserver observer = mCard.getViewTreeObserver();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    observer.removeOnGlobalLayoutListener(this);
                } else {
                    observer.removeGlobalOnLayoutListener(this);
                }
            }
        });

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v == mCard) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mInitialY = mParams.y;
                    mInitialTouchY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_UP:
                    ValueAnimator animator = ValueAnimator.ofInt(mParams.y, 0);
                    animator.addUpdateListener(animation -> {
                        mParams.y = (int) animation.getAnimatedValue();
                        mWindowManager.updateViewLayout(mCard, mParams);
                    });
                    animator.setInterpolator(new AccelerateDecelerateInterpolator());
                    animator.setDuration(Math.abs(mParams.y / mDensity));
                    animator.start();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    mParams.y = mInitialY + (int) (event.getRawY() - mInitialTouchY);
                    mWindowManager.updateViewLayout(mCard, mParams);
                    return true;
            }
        }
        return false;
    }

    private void appear() {
        ValueAnimator animator = ValueAnimator.ofInt(-mCard.getHeight(), 0);
        animator.addUpdateListener(animation -> {
            mParams.y = (int) animation.getAnimatedValue();
            mWindowManager.updateViewLayout(mCard, mParams);
        });
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setDuration(mCard.getHeight() / mDensity);
        animator.start();
    }

    private void dismiss() {
        ValueAnimator animator = ValueAnimator.ofInt(mParams.y, -mCard.getHeight());
        animator.addUpdateListener(animation -> {
            mParams.y = (int) animation.getAnimatedValue();
            mWindowManager.updateViewLayout(mCard, mParams);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                stopSelf();
            }
        });
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setDuration((mParams.y + mCard.getHeight()) / mDensity);
        animator.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sIsOpen = false;
        if (mCard != null) {
            mWindowManager.removeView(mCard);
        }
    }
}
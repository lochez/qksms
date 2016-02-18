package com.moez.QKSMS.service;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.net.Uri;
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
import com.moez.QKSMS.common.utils.Units;
import com.moez.QKSMS.data.Contact;
import com.moez.QKSMS.data.Conversation;
import com.moez.QKSMS.data.ConversationLegacy;
import com.moez.QKSMS.data.Message;
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
    private WindowManager.LayoutParams mCardParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
    private WindowManager.LayoutParams mShadowParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);

    private Handler mHandler;
    private Runnable mDismissRunnable;

    private View mShadow;

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
        mCardParams.gravity = Gravity.TOP;

        String address = intent.getStringExtra("address");
        String body = intent.getStringExtra("body");
        long date = intent.getLongExtra("date", 0);
        Contact contact = Contact.get(address, true);
        Uri uri = intent.getParcelableExtra("uri");

        Message message = new Message(this, uri);
        Conversation conversation = Conversation.get(this, message.getThreadId(), false);
        ConversationLegacy conversationLegacy = new ConversationLegacy(this, message.getThreadId());

        mCard = LayoutInflater.from(this).inflate(R.layout.view_qkreply, null);
        ButterKnife.bind(this, mCard);

        mAvatar.setImageDrawable(contact.getAvatar(this, null));
        mName.setText(contact.getName());
        mMessage.setText(body);
        mDateView.setText(DateFormatter.getMessageTimestamp(this, date));

        mComposeView.setBackgroundDrawable(null);
        mComposeView.onOpenConversation(conversation, conversationLegacy);
        mComposeView.setOnSendListener((addresses, body1) -> dismiss());

        LiveViewManager.registerView(key -> {
            mCard.getBackground().setColorFilter(ThemeManager.getBackgroundColor(), PorterDuff.Mode.MULTIPLY);
        }, this, QKPreference.BACKGROUND);
        mCard.setOnTouchListener(this);

        mReplyText.setOnClickListener(v -> {
            mCardParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            mWindowManager.updateViewLayout(mCard, mCardParams);
            mReplyText.setOnClickListener(null);

            mHandler.removeCallbacks(mDismissRunnable);
        });

        mReplyText.setKeyboardDismissedListener(() -> {
            mHandler.postDelayed(mDismissRunnable, 3000);
            mCardParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            mWindowManager.updateViewLayout(mCard, mCardParams);
        });

        mShadow = new View(this);
        mShadow.setBackgroundResource(R.drawable.qkreply_shadow);
        mShadowParams.y = -Units.dpToPx(this, 32);
        mShadowParams.height = mWindowManager.getDefaultDisplay().getHeight() / 2;
        mShadowParams.gravity = Gravity.TOP;

        mWindowManager.addView(mShadow, mShadowParams);
        mWindowManager.addView(mCard, mCardParams);

        mCard.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mCardParams.y = -mCard.getHeight();
                mWindowManager.updateViewLayout(mCard, mCardParams);
                mHandler.postDelayed(mDismissRunnable, 5000);
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
                    mInitialY = mCardParams.y;
                    mInitialTouchY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_UP:
                    ValueAnimator animator = ValueAnimator.ofInt(mCardParams.y, 0);
                    animator.addUpdateListener(animation -> {
                        mCardParams.y = (int) animation.getAnimatedValue();
                        mWindowManager.updateViewLayout(mCard, mCardParams);
                    });
                    animator.setInterpolator(new AccelerateDecelerateInterpolator());
                    animator.setDuration(Math.abs(mCardParams.y / mDensity));
                    animator.start();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    mCardParams.y = mInitialY + (int) (event.getRawY() - mInitialTouchY);
                    mWindowManager.updateViewLayout(mCard, mCardParams);
                    return true;
            }
        }
        return false;
    }

    private void appear() {
        ValueAnimator animator = ValueAnimator.ofInt(-mCard.getHeight(), 0);
        animator.addUpdateListener(animation -> {
            mCardParams.y = (int) animation.getAnimatedValue();
            mWindowManager.updateViewLayout(mCard, mCardParams);
            mShadow.setAlpha(animation.getAnimatedFraction());
        });
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setDuration(mCard.getHeight() / mDensity);
        animator.start();
    }

    private void dismiss() {
        ValueAnimator animator = ValueAnimator.ofInt(mCardParams.y, -mCard.getHeight());
        animator.addUpdateListener(animation -> {
            mCardParams.y = (int) animation.getAnimatedValue();
            mWindowManager.updateViewLayout(mCard, mCardParams);
            mShadow.setAlpha(1f - animation.getAnimatedFraction());
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                stopSelf();
            }
        });
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setDuration((mCardParams.y + mCard.getHeight()) / mDensity);
        animator.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sIsOpen = false;
        if (mCard != null) {
            mWindowManager.removeView(mCard);
            mWindowManager.removeView(mShadow);
        }
    }
}
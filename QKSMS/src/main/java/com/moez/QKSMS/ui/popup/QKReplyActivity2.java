package com.moez.QKSMS.ui.popup;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
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
import com.moez.QKSMS.ui.view.QKTextView;

public class QKReplyActivity2 extends Activity {

    @Bind(R.id.card) View mCard;
    @Bind(R.id.avatar) AvatarView mAvatar;
    @Bind(R.id.name) QKTextView mName;
    @Bind(R.id.message) QKTextView mMessage;
    @Bind(R.id.date) QKTextView mDateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        setContentView(R.layout.view_qkreply);
        ButterKnife.bind(this);

        mCard.findViewById(R.id.compose_view).setBackgroundDrawable(null);

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

        int density = (int) (getResources().getDisplayMetrics().densityDpi / 160f);
        mCard.setOnTouchListener(new View.OnTouchListener() {
            private float initialY;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = mCard.getY();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        ValueAnimator animator = ValueAnimator.ofFloat(mCard.getY(), 0);
                        animator.addUpdateListener(animation -> {
                            mCard.setY((Float) animation.getAnimatedValue());
                        });
                        animator.setInterpolator(new AccelerateDecelerateInterpolator());
                        animator.setDuration((long) Math.abs(mCard.getY() / density));
                        animator.start();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        mCard.setY(initialY + (int) (event.getRawY() - initialTouchY));
                        return true;
                }
                return false;
            }
        });


        WindowManager.LayoutParams wlp = getWindow().getAttributes();
        wlp.dimAmount = 0;
        wlp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        wlp.gravity = Gravity.TOP;
        getWindow().setAttributes(wlp);
    }
}

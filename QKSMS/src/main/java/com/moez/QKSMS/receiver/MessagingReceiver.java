package com.moez.QKSMS.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;
import com.moez.QKSMS.common.utils.PackageUtils;
import com.moez.QKSMS.service.InsertMessageService;
import com.moez.QKSMS.ui.settings.SettingsFragment;
import org.mistergroup.muzutozvednout.ShouldIAnswerBinder;

public class MessagingReceiver extends BroadcastReceiver {
    private final String TAG = "MessagingReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive");
        abortBroadcast();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (intent.getExtras() != null) {
            Object[] pdus = (Object[]) intent.getExtras().get("pdus");
            SmsMessage[] messages = new SmsMessage[pdus.length];
            for (int i = 0; i < messages.length; i++) {
                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
            }

            SmsMessage sms = messages[0];
            String body;
            if (messages.length == 1 || sms.isReplace()) {
                body = sms.getDisplayMessageBody();
            } else {
                StringBuilder bodyText = new StringBuilder();
                for (SmsMessage message : messages) {
                    bodyText.append(message.getMessageBody());
                }
                body = bodyText.toString();
            }

            String address = sms.getDisplayOriginatingAddress();

            Intent insertMessageIntent = new Intent(context, InsertMessageService.class);
            insertMessageIntent.putExtra("address", address);
            insertMessageIntent.putExtra("body", body);
            insertMessageIntent.putExtra("date", sms.getTimestampMillis());

            if (prefs.getBoolean(SettingsFragment.SHOULD_I_ANSWER, false) &&
                    PackageUtils.isAppInstalled(context, "org.mistergroup.muzutozvednout")) {

                ShouldIAnswerBinder shouldIAnswerBinder = new ShouldIAnswerBinder();
                shouldIAnswerBinder.setCallback(new ShouldIAnswerBinder.Callback() {
                    @Override
                    public void onNumberRating(String number, int rating) {
                        Log.i(TAG, "onNumberRating " + number + ": " + String.valueOf(rating));
                        shouldIAnswerBinder.unbind(context.getApplicationContext());
                        if (rating != ShouldIAnswerBinder.RATING_NEGATIVE) {
                            context.startService(insertMessageIntent);
                        }
                    }

                    @Override
                    public void onServiceConnected() {
                        try {
                            shouldIAnswerBinder.getNumberRating(address);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onServiceDisconnected() {
                    }
                });

                shouldIAnswerBinder.bind(context.getApplicationContext());
            } else {
                context.startService(insertMessageIntent);
            }
        }
    }
}

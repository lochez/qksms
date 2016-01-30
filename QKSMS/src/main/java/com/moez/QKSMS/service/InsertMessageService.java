package com.moez.QKSMS.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import com.moez.QKSMS.common.BlockedConversationHelper;
import com.moez.QKSMS.common.ConversationPrefsHelper;
import com.moez.QKSMS.common.LifecycleHandler;
import com.moez.QKSMS.data.Message;
import com.moez.QKSMS.receiver.UnreadBadgeService;
import com.moez.QKSMS.transaction.NotificationManager;
import com.moez.QKSMS.transaction.SmsHelper;
import com.moez.QKSMS.ui.popup.QKReplyActivity;
import com.moez.QKSMS.ui.settings.SettingsFragment;

public class InsertMessageService extends IntentService {
    private static final String LOG = "InsertMessageService";

    private SharedPreferences mPrefs;
    private ConversationPrefsHelper mConversationPrefs;
    private Message mMessage;

    public InsertMessageService() {
        super(LOG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        String body = intent.getStringExtra("body");
        String address = intent.getStringExtra("address");
        long date = intent.getLongExtra("date", 0);

        Uri uri = SmsHelper.addMessageToInbox(this, address, body, date);

        mMessage = new Message(this, uri);
        mConversationPrefs = new ConversationPrefsHelper(this, mMessage.getThreadId());

        // The user has set messages from this address to be blocked, but we at the time there weren't any
        // messages from them already in the database, so we couldn't block any thread URI. Now that we have one,
        // we can block it, so that the conversation list adapter knows to ignore this thread in the main list
        if (BlockedConversationHelper.isFutureBlocked(mPrefs, address)) {
            BlockedConversationHelper.unblockFutureConversation(mPrefs, address);
            BlockedConversationHelper.blockConversation(mPrefs, mMessage.getThreadId());
            mMessage.markSeen();
            BlockedConversationHelper.FutureBlockedConversationObservable.getInstance().futureBlockedConversationReceived();

            // If we have notifications enabled and this conversation isn't blocked
        } else if (mConversationPrefs.getNotificationsEnabled() && !BlockedConversationHelper.getBlockedConversationIds(
                PreferenceManager.getDefaultSharedPreferences(this)).contains(mMessage.getThreadId())) {

            // Only show QuickReply if we're outside of the app, and they have popups and QuickReply enabled.
            if (!LifecycleHandler.isApplicationVisible() && mPrefs.getBoolean(SettingsFragment.QUICKREPLY, true)) {
                Intent popupIntent = new Intent(this, QKReplyActivity.class);
                popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                popupIntent.putExtra(QKReplyActivity.EXTRA_THREAD_ID, mMessage.getThreadId());
                startActivity(popupIntent);
            }

            UnreadBadgeService.update(this);
            NotificationManager.create(this);

        } else { // We shouldn't show a notification for this message
            mMessage.markSeen();
        }

        if (mConversationPrefs.getWakePhoneEnabled()) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock = pm.newWakeLock((PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "MessagingReceiver");
            wakeLock.acquire();
            wakeLock.release();
        }
    }


}

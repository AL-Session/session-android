package org.session.libsession.utilities

import android.content.Context
import android.util.Log
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.sending_receiving.notifications.MessageNotifier
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.recipients.Recipient

class SSKEnvironment(
    val typingIndicators: TypingIndicatorsProtocol,
    val readReceiptManager: ReadReceiptManagerProtocol,
    val profileManager: ProfileManagerProtocol,
    val notificationManager: MessageNotifier,
    val messageExpirationManager: MessageExpirationManagerProtocol
) {

    interface TypingIndicatorsProtocol {
        fun didReceiveTypingStartedMessage(context: Context, threadId: Long, author: Address, device: Int)
        fun didReceiveTypingStoppedMessage(context: Context, threadId: Long, author: Address, device: Int, isReplacedByIncomingMessage: Boolean)
        fun didReceiveIncomingMessage(context: Context, threadId: Long, author: Address, device: Int)
    }

    interface ReadReceiptManagerProtocol {
        fun processReadReceipts(context: Context, fromRecipientId: String, sentTimestamps: List<Long>, readTimestamp: Long)
    }

    interface ProfileManagerProtocol {
        companion object {
            const val NAME_PADDED_LENGTH = 64
        }

        fun setNickname(context: Context, recipient: Recipient, nickname: String?)
        fun setName(context: Context, recipient: Recipient, name: String?)
        fun setProfilePicture(context: Context, recipient: Recipient, profilePictureURL: String?, profileKey: ByteArray?)
        fun setUnidentifiedAccessMode(context: Context, recipient: Recipient, unidentifiedAccessMode: Recipient.UnidentifiedAccessMode)
        fun contactUpdatedInternal(contact: Contact): String?
    }

    interface MessageExpirationManagerProtocol {
        fun insertExpirationTimerMessage(message: ExpirationTimerUpdate)
        fun startAnyExpiration(timestamp: Long, author: String, expireStartedAt: Long)
        fun startAnyExpiration(message: Message, coerceToDisappearAfterRead: Boolean = false) {
            Log.d("MessageExpirationManagerProtocol", "startAnyExpiration() called with: message = $message, coerceToDisappearAfterRead = $coerceToDisappearAfterRead")

            val timestamp = message.sentTimestamp ?: return
            startAnyExpiration(
                timestamp = timestamp,
                author = message.sender ?: return,
                expireStartedAt = if (message.expiryMode is ExpiryMode.AfterRead || coerceToDisappearAfterRead && message.expiryMode.expiryMillis > 0) SnodeAPI.nowWithOffset.coerceAtLeast(timestamp + 1)
                else if (message.expiryMode is ExpiryMode.AfterSend) timestamp
                else return
            )
        }
    }

    companion object {
        lateinit var shared: SSKEnvironment

        fun configure(typingIndicators: TypingIndicatorsProtocol,
                      readReceiptManager: ReadReceiptManagerProtocol,
                      profileManager: ProfileManagerProtocol,
                      notificationManager: MessageNotifier,
                      messageExpirationManager: MessageExpirationManagerProtocol) {
            if (Companion::shared.isInitialized) { return }
            shared = SSKEnvironment(typingIndicators, readReceiptManager, profileManager, notificationManager, messageExpirationManager)
        }
    }
}

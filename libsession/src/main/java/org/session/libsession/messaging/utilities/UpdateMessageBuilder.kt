package org.session.libsession.messaging.utilities

import android.content.Context
import com.squareup.phrase.Phrase
import org.session.libsession.R
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.messaging.calls.CallMessageType.CALL_FIRST_MISSED
import org.session.libsession.messaging.calls.CallMessageType.CALL_INCOMING
import org.session.libsession.messaging.calls.CallMessageType.CALL_MISSED
import org.session.libsession.messaging.calls.CallMessageType.CALL_OUTGOING
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage.Kind.MEDIA_SAVED
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage.Kind.SCREENSHOT
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.getExpirationTypeDisplayValue
import org.session.libsession.utilities.truncateIdForDisplay

object UpdateMessageBuilder {

    // Keys for Phrase library substitution
    const val NAME_KEY = "name"

    val storage = MessagingModuleConfiguration.shared.storage

    private fun getSenderName(senderId: String) = storage.getContactWithSessionID(senderId)
        ?.displayName(Contact.ContactContext.REGULAR)
        ?: truncateIdForDisplay(senderId)

    fun buildGroupUpdateMessage(context: Context, updateMessageData: UpdateMessageData, senderId: String? = null, isOutgoing: Boolean = false): String {
        val updateData = updateMessageData.kind
        if (updateData == null || !isOutgoing && senderId == null) return ""
        val senderName: String = if (isOutgoing) context.getString(R.string.you)
        else getSenderName(senderId!!)

        // ACL TODO: Replace below with substitutions from phrase library

        return when (updateData) {
            is UpdateMessageData.Kind.GroupCreation -> {
                if (isOutgoing) context.getString(R.string.disappearingMessagesNewGroup)
                else Phrase.from(context, R.string.disappearingMessagesAddedYou).put(NAME_KEY, senderName).format().toString()
            }
            is UpdateMessageData.Kind.GroupNameChange -> {
                if (isOutgoing) context.getString(R.string.MessageRecord_you_renamed_the_group_to_s, updateData.name)
                else context.getString(R.string.MessageRecord_s_renamed_the_group_to_s, senderName, updateData.name)
            }
            is UpdateMessageData.Kind.GroupMemberAdded -> {
                val members = updateData.updatedMembers.joinToString(", ", transform = ::getSenderName)
                if (isOutgoing) context.getString(R.string.MessageRecord_you_added_s_to_the_group, members)
                else context.getString(R.string.MessageRecord_s_added_s_to_the_group, senderName, members)
            }
            is UpdateMessageData.Kind.GroupMemberRemoved -> {
                val userPublicKey = storage.getUserPublicKey()!!
                // 1st case: you are part of the removed members
                return if (userPublicKey in updateData.updatedMembers) {
                    if (isOutgoing) context.getString(R.string.groupMemberYouLeft)
                    else context.getString(R.string.MessageRecord_you_were_removed_from_the_group)
                } else {
                    // 2nd case: you are not part of the removed members
                    val members = updateData.updatedMembers.joinToString(", ", transform = ::getSenderName)
                    if (isOutgoing) context.getString(R.string.MessageRecord_you_removed_s_from_the_group, members)
                    else context.getString(R.string.MessageRecord_s_removed_s_from_the_group, senderName, members)
                }
            }
            is UpdateMessageData.Kind.GroupMemberLeft -> {
                if (isOutgoing) context.getString(R.string.groupMemberYouLeft)
                else context.getString(R.string.ConversationItem_group_action_left, senderName)
            }
            else -> return ""
        }
    }

    fun buildExpirationTimerMessage(
        context: Context,
        duration: Long,
        isGroup: Boolean,
        senderId: String? = null,
        isOutgoing: Boolean = false,
        timestamp: Long,
        expireStarted: Long
    ): String {
        if (!isOutgoing && senderId == null) return ""
        val senderName = if (isOutgoing) context.getString(R.string.you) else getSenderName(senderId!!)
        return if (duration <= 0) {
            if (isOutgoing) context.getString(if (isGroup) R.string.MessageRecord_you_turned_off_disappearing_messages else R.string.MessageRecord_you_turned_off_disappearing_messages_1_on_1)
            else context.getString(if (isGroup) R.string.MessageRecord_s_turned_off_disappearing_messages else R.string.MessageRecord_s_turned_off_disappearing_messages_1_on_1, senderName)
        } else {
            val time = ExpirationUtil.getExpirationDisplayValue(context, duration.toInt())
            val action = context.getExpirationTypeDisplayValue(timestamp >= expireStarted)
            if (isOutgoing) context.getString( //disappearingMessagesSetYou
                if (isGroup) R.string.MessageRecord_s_changed_messages_to_disappear_s_after_s else R.string.MessageRecord_you_set_messages_to_disappear_s_after_s_1_on_1,
                time,
                action
            ) else context.getString(
                if (isGroup) R.string.MessageRecord_s_set_messages_to_disappear_s_after_s else R.string.MessageRecord_s_set_messages_to_disappear_s_after_s_1_on_1,
                senderName,
                time,
                action
            )
        }
    }

    fun buildDataExtractionMessage(context: Context, kind: DataExtractionNotificationInfoMessage.Kind, senderId: String? = null) = when (kind) {
        SCREENSHOT -> R.string.MessageRecord_s_took_a_screenshot
        MEDIA_SAVED -> R.string.MessageRecord_media_saved_by_s
    }.let { context.getString(it, getSenderName(senderId!!)) }

    fun buildCallMessage(context: Context, type: CallMessageType, sender: String): String =
        when (type) {
            CALL_INCOMING -> R.string.MessageRecord_s_called_you
            CALL_OUTGOING -> R.string.MessageRecord_called_s
            CALL_MISSED, CALL_FIRST_MISSED -> R.string.MessageRecord_missed_call_from
        }.let {
            context.getString(it, storage.getContactWithSessionID(sender)?.displayName(Contact.ContactContext.REGULAR) ?: sender)
        }
}

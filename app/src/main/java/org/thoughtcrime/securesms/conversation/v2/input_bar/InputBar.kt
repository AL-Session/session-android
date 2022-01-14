package org.thoughtcrime.securesms.conversation.v2.input_bar

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewInputBarBinding
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.components.LinkPreviewDraftView
import org.thoughtcrime.securesms.conversation.v2.components.LinkPreviewDraftViewDelegate
import org.thoughtcrime.securesms.conversation.v2.messages.QuoteView
import org.thoughtcrime.securesms.conversation.v2.messages.QuoteViewDelegate
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.toDp
import org.thoughtcrime.securesms.util.toPx
import kotlin.math.max
import kotlin.math.roundToInt

class InputBar : RelativeLayout, InputBarEditTextDelegate, QuoteViewDelegate, LinkPreviewDraftViewDelegate {
    private lateinit var binding: ViewInputBarBinding
    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    private val vMargin by lazy { toDp(4, resources) }
    private val minHeight by lazy { toPx(56, resources) }
    private var linkPreviewDraftView: LinkPreviewDraftView? = null
    var delegate: InputBarDelegate? = null
    var additionalContentHeight = 0
    var quote: MessageRecord? = null
    var linkPreview: LinkPreview? = null
    var showInput: Boolean = true
        set(value) { field = value; showOrHideInputIfNeeded() }

    var text: String
        get() { return binding.inputBarEditText.text?.toString() ?: "" }
        set(value) { binding.inputBarEditText.setText(value) }

    val attachmentButtonsContainerHeight: Int
        get() = binding.attachmentsButtonContainer.height

    private val attachmentsButton by lazy { InputBarButton(context, R.drawable.ic_plus_24) }
    private val microphoneButton by lazy { InputBarButton(context, R.drawable.ic_microphone) }
    private val sendButton by lazy { InputBarButton(context, R.drawable.ic_arrow_up, true) }

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewInputBarBinding.inflate(LayoutInflater.from(context), this, true)
        // Attachments button
        binding.attachmentsButtonContainer.addView(attachmentsButton)
        attachmentsButton.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        attachmentsButton.onPress = { toggleAttachmentOptions() }
        // Microphone button
        binding.microphoneOrSendButtonContainer.addView(microphoneButton)
        microphoneButton.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        microphoneButton.onLongPress = { startRecordingVoiceMessage() }
        microphoneButton.onMove = { delegate?.onMicrophoneButtonMove(it) }
        microphoneButton.onCancel = { delegate?.onMicrophoneButtonCancel(it) }
        microphoneButton.onUp = { delegate?.onMicrophoneButtonUp(it) }
        // Send button
        binding.microphoneOrSendButtonContainer.addView(sendButton)
        sendButton.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        sendButton.isVisible = false
        sendButton.onUp = { delegate?.sendMessage() }
        // Edit text
        val incognitoFlag = if (TextSecurePreferences.isIncognitoKeyboardEnabled(context)) 16777216 else 0
        binding.inputBarEditText.imeOptions = binding.inputBarEditText.imeOptions or incognitoFlag // Always use incognito keyboard if setting enabled
        binding.inputBarEditText.inputType = binding.inputBarEditText.inputType or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        binding.inputBarEditText.delegate = this
    }
    // endregion

    // region General
    private fun setHeight(newHeight: Int) {
        val layoutParams = binding.inputBarLinearLayout.layoutParams as LayoutParams
        layoutParams.height = newHeight
        binding.inputBarLinearLayout.layoutParams = layoutParams
        delegate?.inputBarHeightChanged(newHeight)
    }
    // endregion

    // region Updating
    override fun inputBarEditTextContentChanged(text: CharSequence) {
        sendButton.isVisible = text.isNotEmpty()
        microphoneButton.isVisible = text.isEmpty()
        delegate?.inputBarEditTextContentChanged(text)
    }

    override fun inputBarEditTextHeightChanged(newValue: Int) {
        val newHeight = max(newValue + 2 * vMargin, minHeight) + binding.inputBarAdditionalContentContainer.height
        setHeight(newHeight)
    }

    override fun commitInputContent(contentUri: Uri) {
        delegate?.commitInputContent(contentUri)
    }

    private fun toggleAttachmentOptions() {
        delegate?.toggleAttachmentOptions()
    }

    private fun startRecordingVoiceMessage() {
        delegate?.startRecordingVoiceMessage()
    }

    // Drafting quotes and drafting link previews is mutually exclusive, i.e. you can't draft
    // a quote and a link preview at the same time.

    fun draftQuote(thread: Recipient, message: MessageRecord, glide: GlideRequests) {
        quote = message
        linkPreview = null
        linkPreviewDraftView = null
        binding.inputBarAdditionalContentContainer.removeAllViews()
        val quoteView = QuoteView(context, QuoteView.Mode.Draft)
        quoteView.delegate = this
        binding.inputBarAdditionalContentContainer.addView(quoteView)
        val attachments = (message as? MmsMessageRecord)?.slideDeck
        // The max content width is the screen width - 2 times the horizontal input bar padding - the
        // quote view content area's start and end margins. This unfortunately has to be calculated manually
        // here to get the layout right.
        val maxContentWidth = (screenWidth - 2 * resources.getDimension(R.dimen.medium_spacing) - toPx(16, resources) - toPx(30, resources)).roundToInt()
        val sender = if (message.isOutgoing) TextSecurePreferences.getLocalNumber(context)!! else message.individualRecipient.address.serialize()
        quoteView.bind(sender, message.body, attachments,
            thread, true, maxContentWidth, message.isOpenGroupInvitation, message.threadId, false, glide)
        // The 6 DP below is the padding the quote view applies to itself, which isn't included in the
        // intrinsic height calculation.
        val quoteViewIntrinsicHeight = quoteView.getIntrinsicHeight(maxContentWidth) + toPx(6, resources)
        val newHeight = max(binding.inputBarEditText.height + 2 * vMargin, minHeight) + quoteViewIntrinsicHeight
        additionalContentHeight = quoteViewIntrinsicHeight
        setHeight(newHeight)
    }

    override fun cancelQuoteDraft() {
        quote = null
        binding.inputBarAdditionalContentContainer.removeAllViews()
        val newHeight = max(binding.inputBarEditText.height + 2 * vMargin, minHeight)
        additionalContentHeight = 0
        setHeight(newHeight)
    }

    fun draftLinkPreview() {
        quote = null
        val linkPreviewDraftHeight = toPx(88, resources)
        binding.inputBarAdditionalContentContainer.removeAllViews()
        val linkPreviewDraftView = LinkPreviewDraftView(context)
        linkPreviewDraftView.delegate = this
        this.linkPreviewDraftView = linkPreviewDraftView
        binding.inputBarAdditionalContentContainer.addView(linkPreviewDraftView)
        val newHeight = max(binding.inputBarEditText.height + 2 * vMargin, minHeight) + linkPreviewDraftHeight
        additionalContentHeight = linkPreviewDraftHeight
        setHeight(newHeight)
    }

    fun updateLinkPreviewDraft(glide: GlideRequests, linkPreview: LinkPreview) {
        this.linkPreview = linkPreview
        val linkPreviewDraftView = this.linkPreviewDraftView ?: return
        linkPreviewDraftView.update(glide, linkPreview)
    }

    override fun cancelLinkPreviewDraft() {
        if (quote != null) { return }
        linkPreview = null
        binding.inputBarAdditionalContentContainer.removeAllViews()
        val newHeight = max(binding.inputBarEditText.height + 2 * vMargin, minHeight)
        additionalContentHeight = 0
        setHeight(newHeight)
    }

    private fun showOrHideInputIfNeeded() {
        if (showInput) {
            setOf( binding.inputBarEditText, attachmentsButton ).forEach { it.isVisible = true }
            microphoneButton.isVisible = text.isEmpty()
            sendButton.isVisible = text.isNotEmpty()
        } else {
            cancelQuoteDraft()
            cancelLinkPreviewDraft()
            val views = setOf( binding.inputBarEditText, attachmentsButton, microphoneButton, sendButton )
            views.forEach { it.isVisible = false }
        }
    }

    fun addTextChangedListener(textWatcher: TextWatcher) {
        binding.inputBarEditText.addTextChangedListener(textWatcher)
    }

    fun setSelection(index: Int) {
        binding.inputBarEditText.setSelection(index)
    }
    // endregion
}

interface InputBarDelegate {

    fun inputBarHeightChanged(newValue: Int)
    fun inputBarEditTextContentChanged(newContent: CharSequence)
    fun toggleAttachmentOptions()
    fun showVoiceMessageUI()
    fun startRecordingVoiceMessage()
    fun onMicrophoneButtonMove(event: MotionEvent)
    fun onMicrophoneButtonCancel(event: MotionEvent)
    fun onMicrophoneButtonUp(event: MotionEvent)
    fun sendMessage()
    fun commitInputContent(contentUri: Uri)
}
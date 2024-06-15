package org.thoughtcrime.securesms.conversation.start

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ui.Colors
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.ItemButton
import org.thoughtcrime.securesms.ui.LocalColors
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.components.AppBar
import org.thoughtcrime.securesms.ui.components.QrImage
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.createThemedComposeView
import org.thoughtcrime.securesms.ui.small
import org.thoughtcrime.securesms.ui.xl
import javax.inject.Inject

@AndroidEntryPoint
class NewConversationHomeFragment : Fragment() {

    @Inject
    lateinit var textSecurePreferences: TextSecurePreferences

    var delegate = MutableStateFlow<NewConversationDelegate?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = createThemedComposeView {
        // Warning, brittle code, NewConversationScreen will not be visible if no delegate is set
        // before onCreateView is called.
        delegate.collectAsState().value?.let {
            NewConversationScreen(
                accountId = TextSecurePreferences.getLocalNumber(requireContext())!!,
                delegate = it
            )
        }
    }
}

@Preview
@Composable
fun PreviewNewConversationScreen(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        NewConversationScreen(
            accountId = "059287129387123",
            object: NewConversationDelegate {
                override fun onNewMessageSelected() {}
                override fun onCreateGroupSelected() {}
                override fun onJoinCommunitySelected() {}
                override fun onContactSelected(address: String) {}
                override fun onDialogBackPressed() {}
                override fun onDialogClosePressed() {}
                override fun onInviteFriend() {}
            }
        )
    }
}

@Composable
fun NewConversationScreen(
    accountId: String,
    delegate: NewConversationDelegate
) {
    Column(modifier = Modifier.background(LocalColors.current.backgroundSecondary)) {
        AppBar(stringResource(R.string.dialog_new_conversation_title), onClose = delegate::onDialogClosePressed)
        Surface(
            modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Items(accountId, delegate)
            }
        }
    }
}

/**
 * Items of the NewConversationHome screen. Use in a [Column]
 */
@Suppress("UnusedReceiverParameter")
@Composable
fun ColumnScope.Items(
    accountId: String,
    delegate: NewConversationDelegate
) {
    ItemButton(textId = R.string.messageNew, icon = R.drawable.ic_message, onClick = delegate::onNewMessageSelected)
    Divider(startIndent = LocalDimensions.current.dividerIndent)
    ItemButton(textId = R.string.activity_create_group_title, icon = R.drawable.ic_group, onClick = delegate::onCreateGroupSelected)
    Divider(startIndent = LocalDimensions.current.dividerIndent)
    ItemButton(textId = R.string.dialog_join_community_title, icon = R.drawable.ic_globe, onClick = delegate::onJoinCommunitySelected)
    Divider(startIndent = LocalDimensions.current.dividerIndent)
    ItemButton(textId = R.string.activity_settings_invite_button_title, icon = R.drawable.ic_invite_friend, contentDescription = R.string.AccessibilityId_invite_friend_button, onClick = delegate::onInviteFriend)
    Column(
        modifier = Modifier
            .padding(horizontal = LocalDimensions.current.marginMedium)
            .padding(top = LocalDimensions.current.itemSpacingMedium)
    ) {
        Text(stringResource(R.string.accountIdYours), style = xl)
        Spacer(modifier = Modifier.height(LocalDimensions.current.itemSpacingTiny))
        Text(
            text = stringResource(R.string.qrYoursDescription),
            color = LocalColors.current.textSecondary,
            style = small
        )
        Spacer(modifier = Modifier.height(LocalDimensions.current.itemSpacingSmall))
        QrImage(string = accountId, Modifier.contentDescription(R.string.AccessibilityId_qr_code))
    }
}
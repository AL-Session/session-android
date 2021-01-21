package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.AsyncTask
import android.os.Bundle
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_create_closed_group.*
import kotlinx.android.synthetic.main.activity_linked_devices.recyclerView
import network.loki.messenger.R
import nl.komponents.kovenant.ui.successUi
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.loki.protocol.ClosedGroupsProtocolV2
import org.thoughtcrime.securesms.loki.utilities.fadeIn
import org.thoughtcrime.securesms.loki.utilities.fadeOut
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.util.guava.Optional
import java.lang.ref.WeakReference

class CreateClosedGroupActivity : PassphraseRequiredActionBarActivity(), LoaderManager.LoaderCallbacks<List<String>> {
    private var isLoading = false
        set(newValue) { field = newValue; invalidateOptionsMenu() }
    private var members = listOf<String>()
        set(value) { field = value; selectContactsAdapter.members = value }

    private val selectContactsAdapter by lazy {
        SelectContactsAdapter(this, GlideApp.with(this))
    }

    companion object {
        const val closedGroupCreatedResultCode = 100
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_create_closed_group)
        supportActionBar!!.title = resources.getString(R.string.activity_create_closed_group_title)
        recyclerView.adapter = this.selectContactsAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        createNewPrivateChatButton.setOnClickListener { createNewPrivateChat() }
        LoaderManager.getInstance(this).initLoader(0, null, this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_done, menu)
        return members.isNotEmpty() && !isLoading
    }
    // endregion

    // region Updating
    override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<List<String>> {
        return SelectContactsLoader(this, setOf())
    }

    override fun onLoadFinished(loader: Loader<List<String>>, members: List<String>) {
        update(members)
    }

    override fun onLoaderReset(loader: Loader<List<String>>) {
        update(listOf())
    }

    private fun update(members: List<String>) {
        this.members = members
        mainContentContainer.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
        emptyStateContainer.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
        invalidateOptionsMenu()
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.doneButton -> if (!isLoading) { createClosedGroup() }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createNewPrivateChat() {
        setResult(Companion.closedGroupCreatedResultCode)
        finish()
    }

    private fun createClosedGroup() {
        val name = nameEditText.text.trim()
        if (name.isEmpty()) {
            return Toast.makeText(this, R.string.activity_create_closed_group_group_name_missing_error, Toast.LENGTH_LONG).show()
        }
        if (name.length >= 64) {
            return Toast.makeText(this, R.string.activity_create_closed_group_group_name_too_long_error, Toast.LENGTH_LONG).show()
        }
        val selectedMembers = this.selectContactsAdapter.selectedMembers
        if (selectedMembers.count() < 1) {
            return Toast.makeText(this, R.string.activity_create_closed_group_not_enough_group_members_error, Toast.LENGTH_LONG).show()
        }
        if (selectedMembers.count() >= ClosedGroupsProtocolV2.groupSizeLimit) { // Minus one because we're going to include self later
            return Toast.makeText(this, R.string.activity_create_closed_group_too_many_group_members_error, Toast.LENGTH_LONG).show()
        }
        val userPublicKey = TextSecurePreferences.getLocalNumber(this)
        isLoading = true
        loader.fadeIn()
        ClosedGroupsProtocolV2.createClosedGroup(this, name.toString(), selectedMembers + setOf( userPublicKey )).successUi { groupID ->
            loader.fadeOut()
            isLoading = false
            val threadID = DatabaseFactory.getThreadDatabase(this).getOrCreateThreadIdFor(Recipient.from(this, Address.fromSerialized(groupID), false))
            if (!isFinishing) {
                openConversationActivity(this, threadID, Recipient.from(this, Address.fromSerialized(groupID), false))
                finish()
            }
        }
    }
    // endregion

}

// region Convenience
private fun openConversationActivity(context: Context, threadId: Long, recipient: Recipient) {
    val intent = Intent(context, ConversationActivity::class.java)
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId)
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT)
    intent.putExtra(ConversationActivity.ADDRESS_EXTRA, recipient.address)
    context.startActivity(intent)
}
// endregion
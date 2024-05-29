package org.thoughtcrime.securesms.preferences

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.isPasswordDisabled
import org.session.libsession.utilities.TextSecurePreferences.Companion.setScreenLockEnabled
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.areNotificationsEnabled
import org.thoughtcrime.securesms.util.IntentUtils
import javax.inject.Inject

@AndroidEntryPoint
class PrivacySettingsPreferenceFragment : ListSummaryPreferenceFragment() {

    @Inject lateinit var configFactory: ConfigFactory

    override fun onCreate(paramBundle: Bundle?) {
        super.onCreate(paramBundle)
        findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK)!!
            .onPreferenceChangeListener = ScreenLockListener()
        findPreference<Preference>(TextSecurePreferences.TYPING_INDICATORS)!!
            .onPreferenceChangeListener = TypingIndicatorsToggleListener()

        // Voice and Video Calls permissions are a special case because on Android API 30+ the
        // RECORD_AUDIO permission can be granted "Only this time" - in which case on reboot / cold
        // boot of a device our saved preference value will be true, but we may actually NOT have
        // the RECORD_AUDIO permission any longer - which can cause crashes.
        findPreference<Preference>(TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED)!!
            .onPreferenceChangeListener = CallToggleListener(this) {
                // This gets called each time the user changes the checked/unchecked state, and will
                // only accept checking the box if we have the RECORD_AUDIO permission.
                setVoiceAndVideoCallEnabledState(it)
            }

        // This runs on startup of the privacy activity and will force the checked state of "Voice
        // and Video Calls" to be off regardless of its saved state if we lack the necessary
        // permissions to use the feature, such as if they allowed us to RECORD_AUDIO "This time
        // only". In such a case the user will be prompted to re-enable Voice and Video Calls again
        // the next time they attempt to make a voice or video call.
        val haveMicrophonePerm = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!haveMicrophonePerm) {
            (findPreference<Preference>(TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED) as SwitchPreferenceCompat?)!!.isChecked = false
        }

        findPreference<PreferenceCategory>(getString(R.string.preferences__message_requests_category))?.let { category ->
            when (val user = configFactory.user) {
                null -> category.isVisible = false
                else -> SwitchPreferenceCompat(requireContext()).apply {
                    key = TextSecurePreferences.ALLOW_MESSAGE_REQUESTS
                    preferenceDataStore = object : PreferenceDataStore() {

                        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
                            if (key == TextSecurePreferences.ALLOW_MESSAGE_REQUESTS) {
                                return user.getCommunityMessageRequests()
                            }
                            return super.getBoolean(key, defValue)
                        }

                        override fun putBoolean(key: String?, value: Boolean) {
                            if (key == TextSecurePreferences.ALLOW_MESSAGE_REQUESTS) {
                                user.setCommunityMessageRequests(value)
                                return
                            }
                            super.putBoolean(key, value)
                        }
                    }
                    title = getString(R.string.preferences__message_requests_title)
                    summary = getString(R.string.preferences__message_requests_summary)
                }.let(category::addPreference)
            }
        }
        initializeVisibility()
    }

    private fun setVoiceAndVideoCallEnabledState(isEnabled: Boolean) {
        // Live check of whether we currently have the RECORD_AUDIO permission
        val haveMicPerm = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        // Only check the box if asked to AND we have the requisite permissions
        val enabledAndHaveMicPerm = isEnabled && haveMicPerm
        (findPreference<Preference>(TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED) as SwitchPreferenceCompat?)!!.isChecked = enabledAndHaveMicPerm

        // Warn if turning on voice & video calls but notifications are disabled
        if (enabledAndHaveMicPerm && !areNotificationsEnabled(requireActivity())) {
            showSessionDialog {
                title(R.string.CallNotificationBuilder_system_notification_title)
                text(R.string.CallNotificationBuilder_system_notification_message)
                button(R.string.activity_notification_settings_title) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID))
                    }
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        .takeIf { IntentUtils.isResolvable(requireContext(), it) }.let {
                        startActivity(it)
                    }
                }
                button(R.string.dismiss) {
                    (findPreference<Preference>(TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED) as SwitchPreferenceCompat?)!!.isChecked = false
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_app_protection)
    }

    override fun onResume() {
        super.onResume()
    }

    private fun initializeVisibility() {
        if (isPasswordDisabled(requireContext())) {
            val keyguardManager =
                requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!keyguardManager.isKeyguardSecure) {
                findPreference<SwitchPreferenceCompat>(TextSecurePreferences.SCREEN_LOCK)!!.isChecked = false
                findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK)!!.isEnabled = false
            }
        } else {
            findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK)!!.isVisible = false
            findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK_TIMEOUT)!!.isVisible = false
        }
    }

    private inner class ScreenLockListener : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            val enabled = newValue as Boolean
            setScreenLockEnabled(context!!, enabled)
            val intent = Intent(context, KeyCachingService::class.java)
            intent.action = KeyCachingService.LOCK_TOGGLED_EVENT
            context!!.startService(intent)
            return true
        }
    }

    private inner class TypingIndicatorsToggleListener : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            val enabled = newValue as Boolean
            if (!enabled) {
                ApplicationContext.getInstance(requireContext()).typingStatusRepository.clear()
            }
            return true
        }
    }

}
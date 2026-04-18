package io.github.landwarderer.neyon.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.exceptions.resolve.SnackbarErrorObserver
import io.github.landwarderer.neyon.core.network.DoHProvider
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.core.ui.BasePreferenceFragment
import io.github.landwarderer.neyon.core.util.ext.observe
import io.github.landwarderer.neyon.core.util.ext.observeEvent
import io.github.landwarderer.neyon.core.util.ext.restartApplication
import io.github.landwarderer.neyon.core.util.ext.setDefaultValueCompat
import org.koitharu.kotatsu.parsers.util.names
import io.github.landwarderer.neyon.settings.userdata.storage.StorageUsagePreference
import java.net.Proxy

@AndroidEntryPoint
class StorageAndNetworkSettingsFragment :
    BasePreferenceFragment(R.string.storage_and_network),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val viewModel by viewModels<StorageAndNetworkSettingsViewModel>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_network_storage)
        findPreference<ListPreference>(AppSettings.KEY_DOH)?.run {
            entryValues = DoHProvider.entries.names()
            setDefaultValueCompat(DoHProvider.NONE.name)
        }
        bindProxySummary()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(listView, this))
        settings.subscribe(this)
        findPreference<StorageUsagePreference>(AppSettings.KEY_STORAGE_USAGE)?.let { pref ->
            viewModel.storageUsage.observe(viewLifecycleOwner, pref)
        }
    }

    override fun onDestroyView() {
        settings.unsubscribe(this)
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        when (key) {
            AppSettings.KEY_SSL_BYPASS,
            AppSettings.KEY_PAGES_CACHE_SIZE -> {
                Snackbar.make(listView, R.string.settings_apply_restart_required, Snackbar.LENGTH_LONG)
                    .setAction(R.string.restart) {
                        requireContext().restartApplication()
                    }
                    .show()
            }

            AppSettings.KEY_PROXY_TYPE,
            AppSettings.KEY_PROXY_ADDRESS,
            AppSettings.KEY_PROXY_PORT -> {
                bindProxySummary()
            }
        }
    }

    private fun bindProxySummary() {
        findPreference<Preference>(AppSettings.KEY_PROXY)?.run {
            val type = settings.proxyType
            val address = settings.proxyAddress
            val port = settings.proxyPort
            summary = when {
                type == Proxy.Type.DIRECT -> context.getString(R.string.disabled)
                address.isNullOrEmpty() || port == 0 -> context.getString(R.string.invalid_proxy_configuration)
                else -> "$address:$port"
            }
        }
    }
}

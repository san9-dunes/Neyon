package io.github.landwarderer.neyon.mihon.extensions.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.landwarderer.neyon.core.util.ext.processLifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ExternalExtensionPackageObserver(
	private val context: Context,
) {

	private val _packageChanges = MutableSharedFlow<String>(extraBufferCapacity = 8)
	val packageChanges = _packageChanges.asSharedFlow()

	private var isObserving = false

	private val receiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			val packageName = intent?.data?.schemeSpecificPart ?: return
			when (intent.action) {
				Intent.ACTION_PACKAGE_ADDED,
				Intent.ACTION_PACKAGE_REPLACED,
				Intent.ACTION_PACKAGE_REMOVED -> {
					val pendingResult = goAsync()
					processLifecycleScope.launch(Dispatchers.IO) {
						try {
							_packageChanges.emit(packageName)
						} finally {
							pendingResult?.finish()
						}
					}
				}
			}
		}
	}

	fun startObserving() {
		if (isObserving) {
			return
		}
		ContextCompat.registerReceiver(
			context,
			receiver,
			IntentFilter().apply {
				addAction(Intent.ACTION_PACKAGE_ADDED)
				addAction(Intent.ACTION_PACKAGE_REPLACED)
				addAction(Intent.ACTION_PACKAGE_REMOVED)
				addDataScheme("package")
			},
			ContextCompat.RECEIVER_EXPORTED,
		)
		isObserving = true
	}

	fun stopObserving() {
		if (!isObserving) {
			return
		}
		runCatching {
			context.unregisterReceiver(receiver)
		}
		isObserving = false
	}
}

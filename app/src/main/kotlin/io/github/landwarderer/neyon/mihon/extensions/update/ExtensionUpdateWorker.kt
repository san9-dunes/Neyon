package io.github.landwarderer.neyon.mihon.extensions.update

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.util.ext.awaitUniqueWorkInfoByName
import io.github.landwarderer.neyon.mihon.MihonExtensionManager
import io.github.landwarderer.neyon.mihon.extensions.repo.ExternalExtensionRepoRepository
import io.github.landwarderer.neyon.mihon.extensions.repo.ExternalExtensionType
import io.github.landwarderer.neyon.settings.sources.extension.ExtensionDownloaderActivity
import io.github.landwarderer.neyon.settings.work.PeriodicWorkScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltWorker
class ExtensionUpdateWorker @AssistedInject constructor(
	@Assisted appContext: Context,
	@Assisted params: WorkerParameters,
	private val repoRepository: ExternalExtensionRepoRepository,
	private val extensionManager: MihonExtensionManager,
) : CoroutineWorker(appContext, params) {

	override suspend fun doWork(): Result {
		val repos = repoRepository.getByType(ExternalExtensionType.MIHON)
		if (repos.isEmpty()) {
			return Result.success()
		}

		repoRepository.refresh(ExternalExtensionType.MIHON)
		extensionManager.loadExtensions()

		val installedByPackage = extensionManager.installedExtensions.value.associateBy { it.pkgName }
		val updates = repoRepository.getAvailableExtensions(ExternalExtensionType.MIHON)
			.filter { available ->
				val installed = installedByPackage[available.pkgName]
				installed != null && available.versionCode > installed.versionCode
			}

		if (updates.isNotEmpty()) {
			showUpdatesNotification(updates.size)
		}
		return Result.success()
	}

	private fun showUpdatesNotification(count: Int) {
		val notificationManager = NotificationManagerCompat.from(applicationContext)
		val channel = NotificationChannelCompat.Builder(
			WORKER_CHANNEL_ID,
			NotificationManagerCompat.IMPORTANCE_DEFAULT,
		)
			.setName(applicationContext.getString(R.string.extension_updates))
			.setShowBadge(true)
			.setVibrationEnabled(false)
			.build()
		notificationManager.createNotificationChannel(channel)

		val intent = android.content.Intent(applicationContext, ExtensionDownloaderActivity::class.java)
		val pendingIntent = PendingIntentCompat.getActivity(
			applicationContext,
			0,
			intent,
			0,
			false,
		)
		val notification = NotificationCompat.Builder(applicationContext, WORKER_CHANNEL_ID)
			.setSmallIcon(android.R.drawable.stat_sys_download_done)
			.setContentTitle(applicationContext.getString(R.string.extension_updates_available))
			.setContentText(applicationContext.resources.getQuantityString(R.plurals.extension_updates_available_count, count, count))
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)
			.setCategory(NotificationCompat.CATEGORY_STATUS)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.build()

		runCatching {
			notificationManager.notify(WORKER_NOTIFICATION_ID, notification)
		}
	}

	@Reusable
	class Scheduler @Inject constructor(
		private val workManager: WorkManager,
	) : PeriodicWorkScheduler {

		override suspend fun schedule() {
			val request = PeriodicWorkRequestBuilder<ExtensionUpdateWorker>(1, TimeUnit.DAYS)
				.setConstraints(createConstraints())
				.addTag(TAG)
				.setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
				.build()
			workManager
				.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
				.await()
		}

		override suspend fun unschedule() {
			workManager.cancelUniqueWork(TAG).await()
		}

		override suspend fun isScheduled(): Boolean {
			return workManager
				.awaitUniqueWorkInfoByName(TAG)
				.any { !it.state.isFinished }
		}

		private fun createConstraints(): Constraints {
			return Constraints.Builder()
				.setRequiredNetworkType(NetworkType.CONNECTED)
				.build()
		}
	}

	private companion object {
		const val TAG = "extension_updates"
		const val WORKER_CHANNEL_ID = "extension_updates"
		const val WORKER_NOTIFICATION_ID = 47
	}
}

package io.github.landwarderer.neyon.main.ui.protect

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import io.github.landwarderer.neyon.core.exceptions.WrongPasswordException
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.core.ui.BaseViewModel
import io.github.landwarderer.neyon.core.util.ext.MutableEventFlow
import io.github.landwarderer.neyon.core.util.ext.call
import io.github.landwarderer.neyon.core.util.HashUtils
import org.koitharu.kotatsu.parsers.util.md5
import javax.inject.Inject

private const val PASSWORD_COMPARE_DELAY = 1_000L

@HiltViewModel
class ProtectViewModel @Inject constructor(
	private val settings: AppSettings,
	private val protectHelper: AppProtectHelper,
) : BaseViewModel() {

	private var job: Job? = null

	val onUnlockSuccess = MutableEventFlow<Unit>()

	val isBiometricEnabled
		get() = settings.isBiometricProtectionEnabled

	val isNumericPassword
		get() = settings.isAppPasswordNumeric

	fun tryUnlock(password: String) {
		if (job?.isActive == true) {
			return
		}
		job = launchLoadingJob {
			val appPasswordHash = settings.appPassword

			// Migration from legacy MD5
			val isCorrect = if (appPasswordHash != null && !appPasswordHash.startsWith("PBKDF2\$")) {
				if (password.md5() == appPasswordHash) {
					// Correct legacy password, migrate to PBKDF2
					settings.appPassword = HashUtils.hashPassword(password)
					true
				} else {
					false
				}
			} else {
				appPasswordHash != null && HashUtils.verifyPassword(password, appPasswordHash)
			}

			if (isCorrect) {
				unlock()
			} else {
				delay(PASSWORD_COMPARE_DELAY)
				throw WrongPasswordException()
			}
		}
	}

	fun unlock() {
		protectHelper.unlock()
		onUnlockSuccess.call(Unit)
	}
}

package io.github.landwarderer.neyon.settings.about

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.neyon.core.github.AppUpdateRepository
import io.github.landwarderer.neyon.core.ui.BaseViewModel
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
	private val repository: AppUpdateRepository,
) : BaseViewModel() {

	val nextVersion = repository.observeAvailableUpdate()

	init {
		if (nextVersion.value == null) {
			launchLoadingJob {
				repository.fetchUpdate()
			}
		}
	}
}

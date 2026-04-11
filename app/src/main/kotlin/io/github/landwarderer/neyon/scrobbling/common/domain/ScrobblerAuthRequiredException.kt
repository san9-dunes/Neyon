package io.github.landwarderer.neyon.scrobbling.common.domain

import okio.IOException
import io.github.landwarderer.neyon.scrobbling.common.domain.model.ScrobblerService

class ScrobblerAuthRequiredException(
	val scrobbler: ScrobblerService,
) : IOException()

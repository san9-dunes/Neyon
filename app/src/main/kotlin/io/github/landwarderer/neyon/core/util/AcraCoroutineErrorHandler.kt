package io.github.landwarderer.neyon.core.util

import io.github.landwarderer.neyon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.neyon.core.util.ext.report
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class AcraCoroutineErrorHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler),
	CoroutineExceptionHandler {

	override fun handleException(context: CoroutineContext, exception: Throwable) {
		exception.printStackTraceDebug("AcraCoroutineErrorHandler::handleException")
		exception.report()
	}
}

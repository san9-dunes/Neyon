package io.github.landwarderer.neyon.mihon.parsers.config

interface ContentSourceConfig {
	operator fun <T> get(key: ConfigKey<T>): T
}

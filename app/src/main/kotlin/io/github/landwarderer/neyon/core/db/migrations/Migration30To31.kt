package io.github.landwarderer.neyon.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration30To31 : Migration(30, 31) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			DELETE FROM external_extension_repos
			WHERE type = 'MIHON'
				AND baseUrl = 'https://raw.githubusercontent.com/keiyoushi/extensions/refs/heads/repo'
			""".trimIndent(),
		)
	}
}

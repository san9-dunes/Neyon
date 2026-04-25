package io.github.landwarderer.neyon.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration31To32 : Migration(31, 32) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"ALTER TABLE external_extension_repos ADD COLUMN isBuiltIn INTEGER NOT NULL DEFAULT 0",
		)
	}
}

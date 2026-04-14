package io.github.landwarderer.neyon.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration27To28 : Migration(27, 28) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE suggestions ADD COLUMN reason TEXT DEFAULT NULL")
	}
}

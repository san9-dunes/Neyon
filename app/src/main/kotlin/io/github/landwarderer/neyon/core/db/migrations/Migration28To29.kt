package io.github.landwarderer.neyon.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration28To29 : Migration(28, 29) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("CREATE INDEX IF NOT EXISTS index_history_updated_at ON history (updated_at)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_history_created_at ON history (created_at)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_history_deleted_at ON history (deleted_at)")
	}
}

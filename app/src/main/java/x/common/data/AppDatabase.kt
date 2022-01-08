package x.common.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import x.common.DATABASE_NAME
import x.common.game.data.Converters
import x.common.game.data.Score
import x.common.game.data.ScoreDao

@Database(entities = [Score::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
	abstract fun scoreDao(): ScoreDao

	companion object {

		// For Singleton instantiation
		@Volatile
		lateinit var instance: AppDatabase

		fun init(context: Context) {
			synchronized(this) {
				if (!this::instance.isInitialized) instance = buildDatabase(context)
			}
		}

		// Create and pre-populate the database. See this article for more details:
		// https://medium.com/google-developers/7-pro-tips-for-room-fbadea4bfbd1#4785
		private fun buildDatabase(context: Context): AppDatabase {
			return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
//                .addCallback(
//                    object : RoomDatabase.Callback() {
//                        override fun onCreate(db: SupportSQLiteDatabase) {
//                            super.onCreate(db)
//                            Log.i("DB", "--  onCreate")
//                        }
//                    }
//                )
//                .allowMainThreadQueries()
				.build()
		}
	}
}
package x.common

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import java.util.*

class AppHelper : Helper.IHelper {
	companion object {
		private val TAG = "~☸"

		private lateinit var applicationContext: Application

		private lateinit var sharedPreferences: SharedPreferences

		fun init(application: Application) {
			applicationContext = application
			sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
		}
	}

	override fun buildUserActivitiesInfo(tag: String, context: Context, errors: HashMap<String, Any>): HashMap<String, Any> {
		val data: HashMap<String, Any> = Helper.buildUserActivitiesInfo(tag, context, errors)

		try {
			if (sharedPreferences.contains(CommonConfig.Keys.gameLevel))
				data["game_level"] = sharedPreferences.getInt(CommonConfig.Keys.gameLevel, -1)
			if (sharedPreferences.contains(CommonConfig.Keys.bestScore))
				data["best_score"] = sharedPreferences.getInt(CommonConfig.Keys.bestScore, -1)
		} catch (e: Exception) {
			Log.e(TAG, "!-  [$tag] buildUserActivitiesInfo: error", e)
			errors["user-activities"] = e.stackTraceToString()
		}
		Log.i(TAG, "--> [$tag] buildUserActivitiesInfo: $data")

		return data
	}
}
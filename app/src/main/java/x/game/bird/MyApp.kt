package x.game.bird

import android.util.Log
import kotlinx.coroutines.runBlocking
import x.common.AppHelper
import x.common.BaseApp
import x.common.CommonConfig
import x.common.Helper
import x.common.data.AppDatabase
import x.common.game.GameCenterHelper
import x.common.iap.AdsStore
import x.common.iap.GooglePayment
import x.common.iap.Security
import x.common.ui.AdInterstitial


class MyApp : BaseApp() {

	companion object {
		private const val TAG = "☯︎"
	}

	init {
		Log.i(TAG, "-------")
	}

	override fun onCreate() {
		Log.i(TAG, "--  onCreate")

		AppHelper.init(this)
		Helper.appHelper = AppHelper()

		AppDatabase.init(this)

		super.onCreate()

		// update app data on new version update
		val appVersion = Helper.appLongVersion
		val appDataVersion = Helper.appDataVersion
		if (appDataVersion != appVersion) {
			Log.i(TAG, "*******  Welcome to our new app / version update | old version: $appDataVersion | new version: $appVersion")

			Helper.updatePreference(TAG, CommonConfig.Keys.appDataVersion, appVersion)
		}

		GameCenterHelper.instance.apply {
			this.onGoogleConnected = {
				// if local best score is not submitted to GC then submit
				runBlocking {
					val bestS = AppDatabase.instance.scoreDao().getLastBest()
					if (bestS != null && bestS.isSummitedGC != true) {
						Log.i(TAG, "--  submitScore GC: $bestS")
						mLeaderboardsClient!!.submitScore(
							getString(R.string.leaderboard_all_players), bestS.score.toLong()
						)
						bestS.isSummitedGC = true
						runBlocking {
							AppDatabase.instance.scoreDao().update(bestS)
						}
					}
				}
			}
		}

		// A helpful hint to prevent confusion when billing transactions silently fail
		if (BuildConfig.BASE64_ENCODED_PUBLIC_KEY == "null") {
			throw Error(getString(R.string.alert_error_message_encoded_public_key_not_set))
		}

		// IAP
		Security.initiate(BuildConfig.BASE64_ENCODED_PUBLIC_KEY)
		GooglePayment.initiate(this, applicationScope)
		AdsStore.initiate()
		adsStore = AdsStore(applicationScope)
		adsStore.registerPayments(mutableSetOf(GooglePayment.shared))
		GooglePayment.shared.start()

		AdInterstitial.init(this)
	}
}
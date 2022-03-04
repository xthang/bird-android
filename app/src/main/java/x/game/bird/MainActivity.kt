package x.game.bird

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import x.common.CommonConfig
import x.common.Helper
import x.common.ui.AdBanner
import x.common.ui.BaseGameActivity
import x.game.bird.scene.HomeScene
import java.util.*
import kotlin.properties.Delegates

class MainActivity : BaseGameActivity() {

	companion object {
		private const val TAG = "►"
	}

	private var lastShowAdInterstitialGameNo by Delegates.notNull<Int>()

	override fun onCreate(savedInstanceState: Bundle?) {
		lastShowAdInterstitialGameNo =
			PreferenceManager.getDefaultSharedPreferences(this).getInt(CommonConfig.Keys.gamesCount, 0) - 1

		homeScene = HomeScene(this)

		super.onCreate(savedInstanceState)
	}

	override fun homeEntered(intent: Intent) {
		super.homeEntered(intent)

		if (Helper.adsRemoved()) return

		checkAndUpdateAdsBanner("homeEntered", true, AdBanner.Position.TOP)
	}

	override fun gameEntered(intent: Intent) {
		super.gameEntered(intent)

		canShowAds = false

		updateAdsBanner("gameEntered", false)
	}

	override fun gameFinished(intent: Intent) {
		super.gameFinished(intent)

		val level = intent.getIntExtra("object", 0)

		if (level >= 15 && (level + 35) % 50 == 0) {
			val manager = ReviewManagerFactory.create(this) // FakeReviewManager(this)
			val request = manager.requestReviewFlow()
			request.addOnCompleteListener { task ->
				if (task.isSuccessful) {
					// We got the ReviewInfo object
					val reviewInfo = task.result
					Log.i(TAG, "--  requestReviewFlow isSuccessful: $reviewInfo")
					val flow = manager.launchReviewFlow(this, reviewInfo)
					flow.addOnCompleteListener { task2 ->
						// The flow has finished. The API does not indicate whether the user
						// reviewed or not, or even whether the review dialog was shown. Thus, no
						// matter the result, we continue our app flow.
						Log.i(TAG, "--  launchReviewFlow completed: ${task2.isSuccessful} - ${task2.exception} - ${task2.result}")
					}
				} else {
					// There was some problem, log or handle the error code.
					val exception = task.exception as ReviewException
					@ReviewErrorCode val reviewErrorCode = exception.errorCode
					Log.e(TAG, "--  launchReviewFlow isNOTSuccessful: $exception | $reviewErrorCode")
				}
			}
		}

		if (Helper.adsRemoved()) return

		if (level >= 0) {
			updateAdsBanner("gameFinished", true, AdBanner.Position.BOTTOM)
		}
	}

	override fun showAdInterstitial(tag: String, gameNo: Int): Boolean {
		if (Helper.adsRemoved()) return false

		Log.i(TAG, "--  showAdInterstitial [$tag]: gameNo: $gameNo | last: $lastShowAdInterstitialGameNo ~ $lastShowAdInterstitial")

		val d = Date()
		if (gameNo >= 20
			&& (((gameNo - 0) % 10 == 0 && d.time - lastShowAdInterstitial.time > 90000)
					|| gameNo - lastShowAdInterstitialGameNo >= 12)
		) {
			if (adInterstitial.present("$TAG|showAdInterstitial|$tag", this)) {
				lastShowAdInterstitial = d
				lastShowAdInterstitialGameNo = gameNo
				return true
			}
		}

		return false
	}
}
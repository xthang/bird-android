package x.game.bird

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.contains
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.auth.api.Auth
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import x.common.CommonConfig
import x.common.Helper
import x.common.Notification
import x.common.Singletons
import x.common.game.GameCenterHelper
import x.common.iap.AdsStore
import x.common.iap.GooglePayment
import x.common.iap.Message
import x.common.ui.AdBanner
import x.common.ui.AdInterstitial
import x.common.ui.ButtonStyle
import x.common.ui.PopupAlert
import x.game.bird.databinding.ActivityMainBinding
import java.util.*
import kotlin.properties.Delegates

class MainActivity : ComponentActivity(), GameCenterHelper.GameCenterDelegate {

	companion object {
		private const val TAG = "►"
	}

	private lateinit var binding: ActivityMainBinding
	// private val mLifecycleRegistry = LifecycleRegistry(this)

	private var isNewUpdate = false
	private var isResumedFromLoginIntent = false

	private var initialLayoutComplete = false
	private val adBannerPosition: AdBanner.Position?
		get() = if (binding.adViewContainer.contains(adBanner)) AdBanner.Position.BOTTOM
		else if (binding.adViewContainer.contains(adBanner)) AdBanner.Position.TOP
		else null
	private lateinit var adBanner: AdBanner
	private lateinit var adInterstitial: AdInterstitial
	private var lastShowAdInterstitial = Date()
	private var lastShowAdInterstitialGameNo by Delegates.notNull<Int>()

	private var wakeLock: WakeLock? = null

	private lateinit var googlePaymentMsgLive: LiveData<Message>
	private lateinit var adsStoreMsgLive: LiveData<Message>

	// private lateinit var storeMsgLive: LiveData<Message>
	private var adsPurchaseStateLive: LiveData<Boolean>? = null

	private val msgObserver: Observer<Message>
	private val adsPurchaseStateObserver: Observer<Boolean>

	private val broadcastReceivers: ArrayList<BroadcastReceiver> = arrayListOf()

	init {
		Log.i(TAG, "-------")

		msgObserver = Observer<Message> { msgData ->
			Log.d(TAG, "<-- msgData: $msgData")
			val msg = msgData.text ?: getString(msgData.resourceID!!, *(msgData.args ?: emptyArray()))
			Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
		}

		adsPurchaseStateObserver = Observer<Boolean> { purchased ->
			Log.d(TAG, "<-- adsStore isPurchased: $purchased")
			checkAndUpdateAdsBanner("isPurchased", !purchased)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		val referrer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) referrer else null
		Log.i(TAG, "--  onCreate | $callingActivity | $referrer | ${intent?.getStringExtra("startingActivity")}")
		super.onCreate(savedInstanceState)

		lastShowAdInterstitialGameNo =
			PreferenceManager.getDefaultSharedPreferences(this).getInt(CommonConfig.Keys.gamesCount, 0) - 1

		// Handle the splash screen transition.
		// val splashScreen = installSplashScreen()

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		val skView = binding.skView
		skView.scene = HomeScene(this)

		skView.showsFPS = true
		skView.showsDrawCount = true
		skView.showsNodeCount = true
		skView.showsQuadCount = true
		skView.showsPhysics = true
		skView.showsFields = true
		skView.showsLargeContentViewer = true

		// ADS
		adBanner = AdBanner(this)
		adInterstitial = AdInterstitial.shared

		// Since we're loading the banner based on the adContainerView size, we need to wait until this
		// view is laid out before we can get the width.
		binding.adViewContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
			override fun onGlobalLayout() {
				Log.i(TAG, "--  adViewContainer.onGlobalLayout")
				// IMPORTANT (by XT): remember to remove this Listener (for example: on viewDestroyed), or this view will not be garbage-cleaned
				binding.adViewContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
				initialLayoutComplete = true
			}
		})

		GameCenterHelper.getInstance(null).delegate = this

		LocalBroadcastManager.getInstance(this).apply {
			registerReceiver(object : BroadcastReceiver() {
				override fun onReceive(context: Context?, intent: Intent?) {
					Log.i(
						TAG,
						"--  adsStatusChanged: intent: (${intent?.action} | ${intent?.`package`} | ${intent?.type} | ${intent?.data} | ${intent?.clipData} | ${intent?.dataString} | ${intent?.extras})"
					)

					checkAndUpdateAdsBanner("intent", intent?.getBooleanExtra("object", true) ?: true)
				}
			}.also {
				broadcastReceivers.add(it)
			}, IntentFilter(Notification.Name.AdsStatusChanged.toString()))

			registerReceiver(object : BroadcastReceiver() {
				override fun onReceive(context: Context?, intent: Intent?) {
					homeEntered(intent!!)
				}
			}.also {
				broadcastReceivers.add(it)
			}, IntentFilter(Notification.Name.HomeEntered.toString()))

			registerReceiver(object : BroadcastReceiver() {
				override fun onReceive(context: Context?, intent: Intent?) {
					gameEntered(intent!!)
				}
			}.also {
				broadcastReceivers.add(it)
			}, IntentFilter(Notification.Name.GameEntered.toString()))

			registerReceiver(object : BroadcastReceiver() {
				override fun onReceive(context: Context?, intent: Intent?) {
					gameFinished(intent!!)
				}
			}.also {
				broadcastReceivers.add(it)
			}, IntentFilter(Notification.Name.GameFinished.toString()))
		}

		// Allows billing to refresh purchases during onResume
		lifecycle.addObserver(GooglePayment.shared)

		googlePaymentMsgLive = GooglePayment.shared.messages.asLiveData()
		googlePaymentMsgLive.observeForever(msgObserver)
		adsStoreMsgLive = (application as MyApp).adsStore.messages.asLiveData()
		adsStoreMsgLive.observeForever(msgObserver)
//		storeMsgLive = (application as MyApp).store.messages.asLiveData()
//		storeMsgLive.observeForever(msgObserver)
		adsPurchaseStateLive =
			(application as MyApp).adsStore.isPurchased(AdsStore.adsRemoval.keys.single())
				?.asLiveData()
		adsPurchaseStateLive?.observeForever(adsPurchaseStateObserver)

		// show welcome on new version update
		val appVersion = Helper.appLongVersion
		val welcomeVersionRef = PreferenceManager.getDefaultSharedPreferences(this).let {
			if (it.contains(CommonConfig.Keys.welcomeBannerVersion))
				it.getLong(CommonConfig.Keys.welcomeBannerVersion, -1)
			else null
		}
		if (welcomeVersionRef != appVersion) {
			Helper.updatePreference(TAG, CommonConfig.Keys.welcomeBannerVersion, appVersion)

			if (intent?.getStringExtra("startingActivity") != WelcomeActivity::class.simpleName) {
				isNewUpdate = true

				val alert = PopupAlert.init(
					this,
					title = getString(R.string.your_app_has_been_updated),
					message = "${getString(R.string.new_version)}: ${Helper.appVersion}"
				)
				alert.dismissOnTouchOutside = false

				alert.addAction(getString(R.string.lets_play), style = ButtonStyle.primary1) {
					isNewUpdate = false

					GameCenterHelper.getInstance(null).signInSilently("$TAG|onCreate|welcome")
				}
				binding.root.addView(alert)
				return
			}
		}
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		Log.i(TAG, "--  onConfigurationChanged: $newConfig")
		super.onConfigurationChanged(newConfig)
	}

	override fun onResume() {
		Log.d(TAG, "--  onResume()")
		super.onResume()

		acquireWakeLock(null)

		Singletons.getInstance().reloadAll("$TAG|onResume")

		adBanner.resume("$TAG|onResume")

		if (!isNewUpdate) {
			// Since the state of the signed in user can change when the activity is not active
			// it is recommended to try and sign in silently from when the app resumes.
			if (!isResumedFromLoginIntent) {
				GameCenterHelper.getInstance(null).signInSilently("$TAG|onResume")
			}
			isResumedFromLoginIntent = false
		}
	}

	/** Called when leaving the activity  */
	public override fun onPause() {
		Log.d(TAG, "--  onPause()")

		Singletons.getInstance().releaseAll()

		adBanner.pause("$TAG|onPause")

		if (wakeLock?.isHeld == true) wakeLock!!.release()

		super.onPause()
	}

	override fun onWindowFocusChanged(hasFocus: Boolean) {
		Log.d(TAG, "--  onWindowFocusChanged: $hasFocus")
		super.onWindowFocusChanged(hasFocus)
	}

	/** Called before the activity is destroyed  */
	public override fun onDestroy() {
		adBanner.destroy("$TAG|onDestroy")

		LocalBroadcastManager.getInstance(this).apply {
			broadcastReceivers.forEach {
				unregisterReceiver(it)
			}
		}

		googlePaymentMsgLive.removeObserver(msgObserver)
		adsStoreMsgLive.removeObserver(msgObserver)
		// storeMsgLive.removeObserver(msgObserver)
		adsPurchaseStateLive?.removeObserver(adsPurchaseStateObserver)

		super.onDestroy()
	}

	private fun acquireWakeLock(wakeLockOptions: Int?) {
		if (wakeLockOptions == null) {
			window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		} else {
			val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
			wakeLock =
				powerManager.newWakeLock(wakeLockOptions or PowerManager.ON_AFTER_RELEASE, "x:game")
			try {
				wakeLock!!.acquire(10 * 60 * 1000L /*10 minutes*/)
			} catch (e: SecurityException) {
				Log.e(
					TAG,
					"!-  You have to add\n\t<uses-permission android:name=\"android.permission.WAKE_LOCK\"/>\nto your AndroidManifest.xml !",
					e
				)
			}
		}
	}

	override fun onSilentSignInFail() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext /* Activity context */)
		val googleFirstSignIn = sharedPreferences.getBoolean(CommonConfig.Keys.googleSignIn, false)

		if (!googleFirstSignIn) {
			GameCenterHelper.getInstance(null).startSignInIntent(this)
			sharedPreferences.edit().putBoolean(CommonConfig.Keys.googleSignIn, true).apply()
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		Log.d(
			TAG,
			"--  onActivityResult: $requestCode | $resultCode | $data: (${data?.action} | ${data?.`package`} | ${data?.type} | ${data?.data} | ${data?.clipData} | ${data?.dataString} | ${data?.extras})"
		)
		super.onActivityResult(requestCode, resultCode, data)

		if (requestCode == GameCenterHelper.RC_SIGN_IN) {
			isResumedFromLoginIntent = true

			val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data!!)
			if (result!!.isSuccess) {
				// The signed in account is stored in the result.
				GameCenterHelper.getInstance(null).onGoogleConnected(result.signInAccount!!)
			} else {
				var message = result.status.statusMessage
				Log.w(
					TAG,
					"!-  onActivityResult: ${result.status} | $message | ${result.status.connectionResult}"
				)
				if (message == null || message.isEmpty()) {
					message = getString(R.string.signin_other_error)
				}
				GameCenterHelper.getInstance(null).onDisconnected()
				Toast.makeText(this, message, Toast.LENGTH_LONG).show()
			}
		} else if (requestCode == GameCenterHelper.RC_LEADERBOARD_UI) {

		}
	}

	private var canShowAds = false

	private fun updateAdsBanner(tag: String, on: Boolean, position: AdBanner.Position? = null) {
		Log.d(TAG, "--  updateAdsBanner [$tag]: on: $on | position: $position")

		if (on) {
			val p = position ?: adBannerPosition ?: AdBanner.Position.BOTTOM
			val adViewContainer = if (p == AdBanner.Position.TOP) binding.adViewContainerTop else binding.adViewContainer

			if (initialLayoutComplete)
				adBanner.showIn("$TAG|updateAdsBanner|$tag", adViewContainer, p)
			else
				adViewContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
					override fun onGlobalLayout() {
						Log.i(TAG, "--  showAdBanner: adViewContainer.onGlobalLayout")
						adViewContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)

						initialLayoutComplete = true
						adBanner.showIn("$TAG|updateAdsBanner|$tag|onGlobalLayout", adViewContainer, p)
					}
				})
		} else {
			adBanner.remove("$TAG|updateAdsBanner|$tag")
		}
	}

	// check some conditions before update Ads
	private fun checkAndUpdateAdsBanner(tag: String, on: Boolean, position: AdBanner.Position? = null) {
		if (on && canShowAds) {
			updateAdsBanner("adsStatusChanged|$tag", on, position)
			return
		}
		updateAdsBanner("adsStatusChanged|$tag", false)
	}

	private fun homeEntered(intent: Intent) {
		Log.i(
			TAG,
			"--  homeEntered: intent: (${intent.action} | ${intent.`package`} | ${intent.type} | ${intent.data} | ${intent.clipData} | ${intent.dataString} | ${intent.extras})"
		)

		canShowAds = true

		if (Helper.adsRemoved()) return

		checkAndUpdateAdsBanner("homeEntered", true, AdBanner.Position.TOP)
	}

	private fun gameEntered(intent: Intent) {
		Log.i(
			TAG,
			"--  gameEntered: intent: (${intent.action} | ${intent.`package`} | ${intent.type} | ${intent.data} | ${intent.clipData} | ${intent.dataString} | ${intent.extras})"
		)

		canShowAds = false

		updateAdsBanner("gameLevelEntered", false)
	}

	private fun gameFinished(intent: Intent) {
		Log.i(
			TAG,
			"--  gameFinished: intent: (${intent.action} | ${intent.`package`} | ${intent.type} | ${intent.data} | ${intent.clipData} | ${intent.dataString} | ${intent.extras})"
		)

		canShowAds = true

		val level = intent.getIntExtra("object", 0)

		if (level >= 20 && (level + 30) % 50 == 0) {
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
			updateAdsBanner("gameFinished", true)
		}
	}

	fun showAdInterstitial(tag: String, gameNo: Int): Boolean {
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
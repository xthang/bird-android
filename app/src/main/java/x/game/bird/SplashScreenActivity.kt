package x.game.bird

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import x.common.CommonConfig


class SplashScreenActivity : Activity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_splash)
		Handler(Looper.getMainLooper()).postDelayed({
			if (PreferenceManager.getDefaultSharedPreferences(this).getLong(CommonConfig.Keys.welcomeVersion, -1L) == -1L)
				startActivity(Intent(this, WelcomeActivity::class.java))
			else
				startActivity(Intent(this, MainActivity::class.java))
			finish()
		}, 300)
	}
}
package x.game.bird

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.preference.PreferenceManager
import x.common.CommonConfig
import x.common.ui.OverlayView


class DEV(context: Context, attrs: AttributeSet) : OverlayView(context, attrs) {

	companion object {
		private const val TAG = "DEV"
	}

	init {
		// Log.d(TAG, "-------")
	}

	override fun onFinishInflate() {
		super.onFinishInflate()

		findViewById<Button>(R.id.btn_reset)?.setOnClickListener {
			reset()
		}

		findViewById<Button>(R.id.btn_reset_welcome)?.setOnClickListener {
			resetWelcome()
		}
	}

	private fun reset() {
		Log.d(TAG, "--  reset ...")

		val preferencesEditor = PreferenceManager.getDefaultSharedPreferences(context).edit()
		preferencesEditor.clear().apply()

		Toast.makeText(context, "RESET DONE", Toast.LENGTH_SHORT).show()
	}

	private fun resetWelcome() {
		Log.d(TAG, "--  resetWelcome ...")

		val preferencesEditor = PreferenceManager.getDefaultSharedPreferences(context).edit()
		preferencesEditor.remove(CommonConfig.Keys.welcomeBannerVersion).apply()

		Toast.makeText(context, "resetWelcome DONE", Toast.LENGTH_SHORT).show()
	}
}
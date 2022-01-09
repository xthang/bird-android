package x.game.bird

import android.content.Context
import android.opengl.GLES20
import android.util.AttributeSet
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
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

		findViewById<Button>(R.id.btn_set_gl_viewport_size)?.setOnClickListener {
			setGLViewportSize("", width, height)
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

	private fun setGLViewportSize(tag: String, width: Int, height: Int) {
		Log.d(TAG, "--  setGLViewportSize ...")

		val inputW = findViewById<EditText>(R.id.input_gl_viewport_width)
		val w = inputW?.text?.toString()
		val inputH = findViewById<EditText>(R.id.input_gl_viewport_height)
		val h = inputH?.text?.toString()
		if (w.isNullOrBlank() || h.isNullOrBlank()) {
			Log.w(TAG, "--  setGLViewportSize: empty input")
			Toast.makeText(context, "setGLViewportSize: empty input", Toast.LENGTH_SHORT).show()
			(if (w.isNullOrBlank()) inputW else inputH)?.let {
				it.requestFocus()
				val imm =
					context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
				imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT);
			}
			return
		}

		GLES20.glViewport(
			((width - w.toFloat()) / 2).toInt(),
			((height - h.toFloat()) / 2).toInt(),
			w.toInt(),
			h.toInt()
		)

		Toast.makeText(context, "setGLViewportSize DONE: $width x $height | $w x $h", Toast.LENGTH_SHORT).show()
	}
}
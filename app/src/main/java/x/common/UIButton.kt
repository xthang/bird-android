package x.common

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import x.common.ui.BaseUIButton
import x.common.ui.ButtonIdentifier
import x.game.bird.R

class UIButton(context: Context, attrs: AttributeSet) : BaseUIButton(context, attrs) {

	companion object {
		private val TAG = UIButton::class.simpleName!!
	}

	override fun updateView(tag: String, intent: Intent?) {
		super.updateView(tag, intent)

		if (isInEditMode) return

		when (buttonIdentifier) {
			ButtonIdentifier.Sound -> {
				text = if (Helper.soundOn(context)) context.getText(R.string.sound_is_on)
				else context.getText(R.string.sound_is_off)
			}
			else -> {}
		}
	}
}
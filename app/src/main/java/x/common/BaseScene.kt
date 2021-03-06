package x.common

import android.content.Context
import x.common.sprite.BaseSKScene
import x.common.ui.ButtonIdentifier
import x.common.ui.ButtonResponder
import x.common.ui.IButton
import x.game.bird.R
import x.game.bird.scene.GameScene
import x.game.bird.scene.HomeScene


open class BaseScene(context: Context) : BaseSKScene(context), ButtonResponder {

	companion object {
		private val TAG = BaseScene::class.simpleName!!
	}

	override fun buttonTriggered(button: IButton) {
		when (button.buttonIdentifier) {
			ButtonIdentifier.Close -> {
			}
			ButtonIdentifier.Play,
			ButtonIdentifier.NewGame -> {
				view!!.presentScene("$TAG|buttonTriggered", GameScene(context))
			}
			ButtonIdentifier.Home -> {
				view!!.presentScene("$TAG|buttonTriggered", HomeScene(context))
			}
			ButtonIdentifier.Pause -> {
				pause(button.buttonIdentifier.toString())
			}
			ButtonIdentifier.Resume -> {
				resume(button.buttonIdentifier.toString())
			}
			ButtonIdentifier.Share -> {
				takeScreenShot(context) { uri ->
					Helper.share(
						TAG,
						context.getString(R.string.share_subject, context.getString(R.string.app_name)),
						context.getString(R.string.share_text, context.getString(R.string.app_name))
								+ " " + AppConfig.shareURL,
						uri, "image/*"
					)
				}
			}
			else -> {
				throw Error("!-  Unsupported ButtonNode type ${button.buttonIdentifier} in $TAG")
			}
		}
	}
}

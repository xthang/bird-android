package x.game.bird

import android.content.Context
import x.common.ButtonNode
import x.common.sprite.SceneOverlay
import x.core.graphics.Size
import x.spritekit.*
import kotlin.math.min

class GamePauseScene(context: Context) : SceneOverlay() {

	companion object {
		private val TAG = GamePauseScene::class.simpleName!!
	}

	private val textureAtlas = SKTextureAtlas(context, R.drawable.atlas)

	private val buttons = SKNode()
	private val btnResume = ButtonNode(R.id.btn_resume, "btn_resume", textureAtlas.textureNamed("button_resume"))


	init {
		sceneDidLoad("init")
	}

	override fun sceneDidLoad(tag: String) {
		super.sceneDidLoad(tag)

		addChild(buttons)

		buttons.addChild(btnResume)

		arrayOf(btnResume).forEach {
			it.imgNode!!.texture!!.filteringMode = SKTextureFilteringMode.nearest
		}
	}

	override fun willMoveTo(tag: String, scene: SKScene) {
		super.willMoveTo(tag, scene)

		arrayOf(btnResume).forEach {
			val btnTextureSize = it.imgNode!!.texture!!.size()
			val scale = min(scene.frame.height * 0.05f / btnTextureSize.height, scene.frame.width * 0.1f / btnTextureSize.width)
			it.imgNode!!.size = Size(btnTextureSize.width * scale, btnTextureSize.height * scale)
			it.size = Size(it.imgNode!!.size.width + 6, it.imgNode!!.size.height + 6)
		}

		alpha = 0f
		run(SKAction.fadeIn(0.25))
	}
}
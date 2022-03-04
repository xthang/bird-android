package x.game.bird.level_state

import android.util.Log
import x.common.sprite.SceneOverlay
import x.game.bird.scene.GameScene
import x.spritekit.GKState

abstract class LevelSceneOverlayState(protected val levelScene: GameScene) : GKState() {

	companion object {
		private val TAG = LevelSceneOverlayState::class.simpleName
	}

	private val TAG = this::class.simpleName

	// MARK: Properties

	/// The `SceneOverlay` to display when the state is entered.
	protected abstract val overlay: SceneOverlay

	// MARK: Initializers

	// MARK: GKState Life Cycle

	override fun didEnterFrom(previousState: GKState?) {
		Log.d(TAG, "--  didEnter from: $previousState")
		super.didEnterFrom(previousState)

		// levelScene.root.isUserInteractionEnabled = false
		levelScene.root.speed = 0f
		levelScene.physicsWorld.speed = 0f

		levelScene.show(overlay)
	}

	override fun willExitTo(nextState: GKState) {
		Log.d(TAG, "--  willExit to: $nextState")
		super.willExitTo(nextState)

		//overlay.run(SKAction.fadeOut(withDuration: 0.25)) { [weak self] in
		//	self?.overlay.removeFromParent()
		//}
		overlay.removeFromParent("${this::class.simpleName}|willExitTo")
	}
}
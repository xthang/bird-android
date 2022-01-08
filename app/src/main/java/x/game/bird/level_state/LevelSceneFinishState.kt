package x.game.bird.level_state

import x.game.bird.GameFinishScene
import x.game.bird.GameScene
import x.spritekit.GKState
import kotlin.reflect.KClass

class LevelSceneFinishState(levelScene: GameScene) : LevelSceneOverlayState(levelScene) {

	override var overlay = GameFinishScene(levelScene.context)

	// MARK: GKState Life Cycle

	override fun didEnterFrom(previousState: GKState?) {
		overlay.update("didEnter", levelScene)

		super.didEnterFrom(previousState)

		// Begin preloading the next scene in preparation for the user to advance.
		// levelScene.sceneManager.prepareScene(identifier: .nextLevel)
	}

	override fun isValidNextState(stateClass: KClass<out GKState>): Boolean {
		return stateClass == LevelSceneActiveState::class
	}
}
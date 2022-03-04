package x.game.bird.level_state

import x.game.bird.scene.GamePauseScene
import x.game.bird.scene.GameScene
import x.spritekit.GKState
import kotlin.reflect.KClass

class LevelScenePauseState(levelScene: GameScene) : LevelSceneOverlayState(levelScene) {

	override var overlay = GamePauseScene(levelScene.context)

	override fun didEnterFrom(previousState: GKState?) {
		super.didEnterFrom(previousState)

		if (levelScene.gameState == GameScene.State.STARTED) {
			levelScene.gameState = GameScene.State.PAUSED
		}
	}

	override fun isValidNextState(stateClass: KClass<out GKState>): Boolean {
		return stateClass != this::class
	}
}
package x.game.bird.level_state

import android.util.Log
import x.common.TimeInterval
import x.game.bird.scene.GameScene
import x.spritekit.GKState
import kotlin.reflect.KClass

class LevelSceneActiveState(private val levelScene: GameScene) : GKState() {

	companion object {
		private val TAG = LevelSceneActiveState::class.simpleName
	}

	private var timePassed: TimeInterval = 0.0

	// The formatted string representing the time remaining.
	private val timePassedString: String
		get() {
			val timePassed = timePassed.toInt()
			val hours = timePassed / 3600;
			val minutes = (timePassed % 3600) / 60;
			val seconds = timePassed % 60;

			return String.format("%02d:%02d:%02d", hours, minutes, seconds);
		}

	// MARK: Initializers

	// MARK: GKState Life Cycle

	override fun didEnterFrom(previousState: GKState?) {
		Log.d(TAG, "--  didEnter from: $previousState")
		super.didEnterFrom(previousState)

		if (previousState is LevelSceneFinishState) {
			timePassed = 0.0
		}

		// levelScene.timerNode.text = timePassedString
		if (levelScene.gameState == GameScene.State.PAUSED) {
			levelScene.gameState = GameScene.State.STARTED
		}

		levelScene.setUserInteraction("$TAG|didEnter", true)
		// levelScene.root.isUserInteractionEnabled = true
		levelScene.root.speed = 1f
		levelScene.physicsWorld.speed = 1f
	}

	override fun update(seconds: TimeInterval) {
		super.update(seconds)

		// Subtract the elapsed time from the remaining time.
		timePassed += seconds

		// Update the displayed time remaining.
		// levelScene.timerNode.text = timePassedString
	}

	override fun isValidNextState(stateClass: KClass<out GKState>): Boolean {
		return when (stateClass) {
			LevelScenePauseState::class, LevelSceneFinishState::class -> true
			else -> false
		}
	}
}
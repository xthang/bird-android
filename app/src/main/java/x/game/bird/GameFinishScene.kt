package x.game.bird

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import x.common.ButtonNode
import x.common.CommonConfig
import x.common.Helper
import x.common.game.GameCenterHelper
import x.common.getActivity
import x.common.sprite.BaseSKScene
import x.common.sprite.SceneOverlay
import x.core.graphics.Point
import x.core.graphics.Size
import x.spritekit.*
import kotlin.math.*
import kotlin.properties.Delegates
import kotlin.random.Random

class GameFinishScene(val context: Context) : SceneOverlay() {

	companion object {
		private val TAG = GameFinishScene::class.simpleName!!
	}

	private var gameNo by Delegates.notNull<Int>()
	private var score: Int = -1
	private var newBestScore: Int = -1
	private var loadingScore: Int = -1
		set(value) {
			field = value
			scoreLabel.text = "$field"
		}

	private var isNewBest = false
	private var isNewMedal = false

	private val textureAtlas = SKTextureAtlas(context, R.drawable.atlas)

	// Nodes
	private val gameOverText = SKSpriteNode(textureAtlas.textureNamed("text_game_over"))
	private val scorePanel = SKSpriteNode(textureAtlas.textureNamed("score_panel"))

	private val scoreLabel = SKLabelNode2()
	private val bestScoreLabel = SKLabelNode2()
	private val newLabel = SKSpriteNode(textureAtlas.textureNamed("new"))
	private val medal = SKSpriteNode()
	private val twinkles = SKNode()

	private val buttons = SKNode()
	private val btnNewGame = ButtonNode(R.id.btn_play, "btn_play", textureAtlas.textureNamed("button_play"))
	private val btnLeaderboards = ButtonNode(R.id.btn_leaderboards, "btn_leaderboards", textureAtlas.textureNamed("button_score"))
	private val btnHome = ButtonNode(R.id.btn_home, "btn_home", textureAtlas.textureNamed("button_ok"))
	private val btnShare = ButtonNode(R.id.btn_share, "btn_share", textureAtlas.textureNamed("button_share"))

	private val swooshSound = SKAudioNode(context, R.raw.sfx_swooshing)


	init {
		sceneDidLoad("init")
	}

	override fun sceneDidLoad(tag: String) {
		super.sceneDidLoad(tag)

		gameOverText.texture!!.filteringMode = SKTextureFilteringMode.nearest
		addChild(gameOverText)

		scorePanel.texture!!.filteringMode = SKTextureFilteringMode.nearest
		addChild(scorePanel)

		scoreLabel.zPosition = 1f
		scoreLabel.fontTextureAtlas = textureAtlas
		scoreLabel.fontMap = { atlas, c ->
			atlas.textureNamed("number_score_0$c")
		}
		scoreLabel.horizontalAlignmentMode = SKLabelHorizontalAlignmentMode.right
		scorePanel.addChild(scoreLabel)

		bestScoreLabel.zPosition = 1f
		bestScoreLabel.fontTextureAtlas = textureAtlas
		bestScoreLabel.fontMap = { atlas, c ->
			atlas.textureNamed("number_score_0$c")
		}
		bestScoreLabel.horizontalAlignmentMode = SKLabelHorizontalAlignmentMode.right
		scorePanel.addChild(bestScoreLabel)

		newLabel.texture!!.filteringMode = SKTextureFilteringMode.nearest
		newLabel.zPosition = 1f
		scorePanel.addChild(newLabel)

		medal.zPosition = 1f
		scorePanel.addChild(medal)

		val twinkleTextures = arrayOf(0, 1, 2).map { "blink_0$it" }
		val twinkle = SKSpriteNode(textureAtlas.textureNamed(twinkleTextures.first()))
		val blinking = SKAction.animate(twinkleTextures.map {
			val t = textureAtlas.textureNamed(it)
			t.filteringMode = SKTextureFilteringMode.nearest
			t
		}, 0.16)
		val blinkingForever = SKAction.repeatForever(blinking)

		for (i in 0..7) {
			val t = twinkle.clone()
			t.run(
				SKAction.sequence(
					SKAction.wait((0..20).random().toDouble() / 40),
					blinkingForever
				), "blinking"
			)
			twinkles.addChild(t)
		}

		twinkles.zPosition = 1f
		medal.addChild(twinkles)

		// buttons
		addChild(buttons)

		arrayOf(btnNewGame, btnLeaderboards, btnHome, btnShare).forEach { b ->
			b.imgNode!!.texture!!.filteringMode = SKTextureFilteringMode.nearest
			b.isUserInteractionEnabled = false

			buttons.addChild(b)
		}

		addChild(swooshSound)
		swooshSound.autoplayLooped = false
	}

	fun update(tag: String, levelScene: GameScene) {
		this.gameNo = levelScene.gameNo
		this.score = levelScene.score

		val best = PreferenceManager.getDefaultSharedPreferences(context).getInt(CommonConfig.Keys.bestScore, 0)
		newBestScore = max(best, this.score)
		if (newBestScore != best) {
			Helper.updatePreference("$TAG|update|$tag", CommonConfig.Keys.bestScore, newBestScore)
		}

		isNewBest = best < newBestScore
		bestScoreLabel.text = "$best"

		@StringRes val achievement: Int?

		if (score >= 100) {
			achievement = R.string.achievement_gold_medal
			medal.texture = textureAtlas.textureNamed("medal_gold")
			isNewMedal = best < 100
		} else if (score >= 40) {
			achievement = R.string.achievement_silver_medal
			medal.texture = textureAtlas.textureNamed("medal_silver")
			isNewMedal = best < 40
		} else if (score >= 20) {
			achievement = R.string.achievement_bronze_medal
			medal.texture = textureAtlas.textureNamed("medal_bronze")
			isNewMedal = best < 20
		} else if (score >= 10) {
			achievement = R.string.achievement_aluminum_medal
			medal.texture = textureAtlas.textureNamed("medal_aluminum")
			isNewMedal = best < 10
		} else {
			achievement = null
			medal.texture = null
			isNewMedal = false
		}
		medal.texture?.filteringMode = SKTextureFilteringMode.nearest

		if (achievement != null) {
			GameCenterHelper.instance.reportAchievement(TAG, achievement)
		}
	}

	override fun willMoveTo(tag: String, scene: SKScene) {
		super.willMoveTo(tag, scene)

		val gameOverTextureSize = gameOverText.texture!!.size()
		val gameOverScale =
			min(scene.frame.height * 0.12f / gameOverTextureSize.height, scene.frame.width * 0.8f / gameOverTextureSize.width)
		gameOverText.size = Size(gameOverTextureSize.width * gameOverScale, gameOverTextureSize.height * gameOverScale)
		gameOverText.position.y = scene.frame.maxY + gameOverText.size.height / 2

		val panelTextureSize = scorePanel.texture!!.size()
		val panelScale = min(scene.frame.height * 0.3f / panelTextureSize.height, scene.frame.width * 0.9f / panelTextureSize.width)
		scorePanel.size = Size(panelTextureSize.width * panelScale, panelTextureSize.height * panelScale)
		scorePanel.position.y = scene.frame.maxY + scorePanel.size.height / 2

		scoreLabel.fontSize = scorePanel.size.height * 0.13f
		scoreLabel.textSpace = scoreLabel.fontSize * 0.1f
		scoreLabel.position = Point(scorePanel.size.width * 0.38f, scorePanel.size.height * 0.14f)
		bestScoreLabel.fontSize = scorePanel.size.height * 0.13f
		bestScoreLabel.textSpace = bestScoreLabel.fontSize * 0.1f
		bestScoreLabel.position = Point(scorePanel.size.width * 0.38f, -scorePanel.size.height * 0.21f)

		loadingScore = 0

		val newLabelHeight = scorePanel.size.height * 0.1f
		val newLabelTextureSize = newLabel.texture!!.size()
		newLabel.size = Size(newLabelHeight * newLabelTextureSize.width / newLabelTextureSize.height, newLabelHeight)
		newLabel.position = Point(scorePanel.size.width * 0.125f, -scorePanel.size.height * 0.036f)
		newLabel.isHidden = true

		if (medal.texture != null) {
			val medalHeight = scorePanel.size.height * 0.348f
			val medalTextureSize = medal.texture!!.size()
			medal.size = Size(medalHeight * medalTextureSize.width / medalTextureSize.height, medalHeight)
			medal.position = Point(-scorePanel.size.width * 0.273f, -scorePanel.size.height * 0.028f)
		}
		medal.isHidden = true

		val medalRadius = medal.size.height * 0.5
		val size = medal.size.width * 0.15
		twinkles.children.forEach {
			(it as SKSpriteNode).size = Size(size, size)

			val r = medalRadius * sqrt(Random.nextFloat())
			val theta = Random.nextFloat() * 2 * PI
			it.position = Point(r * cos(theta), r * sin(theta))
		}
		twinkles.isHidden = true

		buttons.children.forEach {
			val b = it as ButtonNode
			val btnTextureSize = b.imgNode!!.texture!!.size()
			val scale = min(scene.frame.height * 0.12 / btnTextureSize.height, scene.frame.width * 0.3 / btnTextureSize.width)
			b.imgNode!!.size = Size(btnTextureSize.width * scale, btnTextureSize.height * scale)
			b.size = Size(b.imgNode!!.size.width + 3, b.imgNode!!.size.height + 3)
		}
		btnNewGame.position = Point(-scene.frame.width * 0.22f, 0f)
		btnLeaderboards.position = Point(scene.frame.width * 0.22f, 0f)
		btnHome.position = Point(-scene.frame.width * 0.22f, btnNewGame.frame.minY - btnHome.size.height)
		btnShare.position = Point(scene.frame.width * 0.22f, btnHome.position.y)

		val buttonsHeight = btnNewGame.frame.height
		buttons.position.y = scene.frame.minY - buttonsHeight / 2
	}

	override fun didMoveTo(tag: String, scene: SKScene) {
		super.didMoveTo(tag, scene)

		gameOverText.run(SKAction.moveTo(Point(0f, scene.frame.height * 0.25f), 0.15)) {
			this.scorePanel.run(SKAction.moveTo(Point(0f, scene.frame.height * 0f), 0.3)) {
				this.runScore()
				this.buttons.run(SKAction.moveTo(Point(0f, -scene.frame.height * 0.22f), 0.15)) {
					this.didShow("")
				}
			}
		}
		(scene as BaseSKScene).playSound(this.swooshSound)
	}

	private fun runScore() {
		val sequence: ArrayList<SKAction> = arrayListOf(SKAction.wait(0.5))
		for (s in 0..score) {
			sequence.add(SKAction.run { this.loadingScore = s })
			sequence.add(SKAction.wait(0.1))
		}

		run(SKAction.sequence(*sequence.toTypedArray())) {
			if (this.medal.texture != null) {
				this.medal.isHidden = false
			}
			this.newLabel.isHidden = !this.isNewBest
			this.twinkles.isHidden = !this.isNewMedal
			this.bestScoreLabel.text = "$newBestScore"
		}
	}

	private fun didShow(tag: String) {
		buttons.children.forEach {
			it.isUserInteractionEnabled = true
		}

		(this.scene!!.view!!.getActivity() as MainActivity).showAdInterstitial("$TAG|didShow|$tag", this.gameNo)
	}
}
package x.game.bird

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import kotlinx.coroutines.runBlocking
import x.common.*
import x.common.data.AppDatabase
import x.common.game.GameCenterHelper
import x.common.game.data.Score
import x.common.ui.ButtonResponder
import x.common.ui.UITouch
import x.core.graphics.Point
import x.core.graphics.SKSceneScaleMode
import x.core.graphics.Size
import x.core.graphics.Vector
import x.game.bird.level_state.LevelSceneActiveState
import x.game.bird.level_state.LevelSceneFinishState
import x.game.bird.level_state.LevelScenePauseState
import x.spritekit.*
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates
import kotlin.random.Random

enum class GameLayer(val rawValue: Float) {
	background(0f),
	objects(1f),
	flash(2f),

	mainCharacter(10f),
	score(80f),
	tutorial(90f),
	navigation(99f);

	enum class ObjectLayer(val rawValue: Float) {
		sky(0f),
		obstacle(5f),
		ground(20f),
	}
}

class GameScene(context: Context) : BaseScene(context), ButtonResponder, SKPhysicsContactDelegate {

	companion object {
		private const val TAG = "♠︎"
	}

	enum class State {
		PREPARED, STARTED, PAUSED, ENDED, FINISHED
	}

	private var sceneLoaded = false

	private val textureAtlas = SKTextureAtlas(context, R.drawable.atlas)

	var gameNo by Delegates.notNull<Int>()
		private set
	var gameState: State = State.PREPARED

	private var lastUpdateTime = 0.0

	private var stateMachine = GKStateMachine(
		LevelSceneActiveState(this),
		LevelScenePauseState(this),
		LevelSceneFinishState(this)
	)

	var score: Int = -1
		set(value) {
			field = value
			scoreLabel.text = "$score"
		}

	// Calculation
	private var MAIN_CHARACTER_HEIGHT by Delegates.notNull<Float>()
	private var GROUND_HEIGHT_ON_DISPLAY by Delegates.notNull<Float>()
	private var PIPE_WIDTH by Delegates.notNull<Float>()
	private var VERTICAL_PIPE_GAP by Delegates.notNull<Float>()
	private var VELOCITY by Delegates.notNull<Float>()
	private var BG_VELOCITY by Delegates.notNull<Float>()

	// Nodes
	var root = SKNode()
	private var navigation = SKNode()
	// lazy var timerNode = root.childNode(withName: "Time") as! SKLabelNode

	private var scoreLabel = SKLabelNode2()
	private var tutorial = SKSpriteNode()

	private var mainCharacter = SKSpriteNode()

	private var movingObjects = SKNode()
	private var movingObstacles = SKNode()

	private var flash = SKSpriteNode(Color.WHITE, Size.zero)

	// Node templates
	private val groundObstacleTemp1 = SKSpriteNode(textureAtlas.textureNamed("pipe_up"))
	private val groundObstacleTemp2 = SKSpriteNode(textureAtlas.textureNamed("pipe2_up"))
	private val skyObstacleTemp1 = SKSpriteNode(textureAtlas.textureNamed("pipe_down"))
	private val skyObstacleTemp2 = SKSpriteNode(textureAtlas.textureNamed("pipe2_down"))

	// Physics
	private val birdCategory = 1
	private val groundCategory = 1 shl 1
	private val pipeCategory = 1 shl 2
	private val scoreCategory = 1 shl 3

	// Actions
	private lateinit var movePipesAndRemove: SKAction

	// FX - sounds
	private val flapSound = SKAudioNode(context, R.raw.sfx_wing)
	private val hitSound = SKAudioNode(context, R.raw.sfx_hit)
	private val dieSound = SKAudioNode(context, R.raw.sfx_die)
	private val scoringSound = SKAudioNode(context, R.raw.sfx_point)

	// FX - vibration
	//private val notiFeedbackGenerator = UINotificationFeedbackGenerator()
	//private var impactFeedbackGenerator: UIImpactFeedbackGenerator!

	private var endGameAction: ((Int) -> Void)? = null


	init {
		sceneDidLoad("")
	}

	override fun sceneDidLoad(tag: String) {
		super.sceneDidLoad(tag)

		initObjects("sceneDidLoad|$tag")
		initFX("sceneDidLoad|$tag")

		loadGame("sceneDidLoad|$tag")
	}

	override fun didMoveTo(tag: String, view: SKView) {
		super.didMoveTo(tag, view)

		val viewSizeAspect = view.height.toFloat() / view.width
		if (1.3 < viewSizeAspect && viewSizeAspect < 2.2) {
			scaleMode = SKSceneScaleMode.resizeFill
		}

		scaleMode = SKSceneScaleMode.aspectFit

		resizeScene("didMove|$tag")
		// sceneStartEffect("didMove")

		// Move to the active state, starting the level timer.
		stateMachine.enter(LevelSceneActiveState::class)

		sceneLoaded = true

		LocalBroadcastManager.getInstance(context)
			.sendBroadcast(Intent(Notification.Name.GameEntered.toString()).apply {
				putExtra("object", gameNo)
				putExtra("tag", TAG)
			})
	}

	override fun didChangeSize(tag: String, oldSize: Size) {
		super.didChangeSize(tag, oldSize)

		if (sceneLoaded) {
			resizeScene("didChangeSize|$tag")
		}
	}

	override fun update(secondsPassed: TimeInterval) {
		// Called before each frame is rendered

		// Initialize _lastUpdateTime if it has not already been
		if (this.lastUpdateTime == 0.0) {
			this.lastUpdateTime = secondsPassed
		}

		// Calculate time since last update
		val dt = secondsPassed - this.lastUpdateTime

		// Update entities
		//for (entity in this.entities) {
		//	entity.update(dt)
		//}
		if (gameState == State.STARTED || gameState == State.ENDED) {
			// update bird rotation
			val verticalVelocity = mainCharacter.physicsBody!!.velocity.dy
			val rotation =
				(verticalVelocity + MAIN_CHARACTER_HEIGHT * 12f) * (if ((verticalVelocity + MAIN_CHARACTER_HEIGHT * 12f) > 0f) 0.01f else 0.0025f)
			mainCharacter.zRotation = min(max(-1.5f, rotation), 0.35f)
		}

		// Update the level's state machine.
		if (gameState == State.STARTED) {
			stateMachine.update(dt)
		}

		this.lastUpdateTime = secondsPassed
	}

	private fun initObjects(tag: String) {
		// Log.d("--  \(TAG) | initObjects [\(tag)]")

		// Physics
		physicsWorld.contactDelegate = this

		addChild(root)

		// Pipes
		movingObjects.zPosition = GameLayer.objects.rawValue
		root.addChild(movingObjects)

		val background = SKNode()
		background.name = "background"
		movingObjects.addChild(background)

		val ground = SKNode()
		ground.name = "ground"
		movingObjects.addChild(ground)

		movingObstacles.speed = 0f
		movingObjects.addChild(movingObstacles)

		flash.zPosition = GameLayer.flash.rawValue
		flash.alpha = 0.7f
		flash.isHidden = true
		root.addChild(flash)

		// Bird
		mainCharacter.name = "bird"
		mainCharacter.zPosition = GameLayer.mainCharacter.rawValue
		val mainCharTextureAtlas = "bird${(0..2).random()}"
		val mainCharTextures = arrayOf(0, 1, 2).map { mainCharTextureAtlas + "_" + it }
		mainCharacter.texture = textureAtlas.textureNamed(mainCharTextures.first())
		val anim = SKAction.animate(mainCharTextures.map {
			val t = textureAtlas.textureNamed(it)
			t.filteringMode = SKTextureFilteringMode.nearest
			t
		}, 0.08)
		val flap = SKAction.repeatForever(anim)
		mainCharacter.run(flap, "flap")
		root.addChild(mainCharacter)

		scoreLabel.fontTextureAtlas = textureAtlas
		scoreLabel.fontMap = { atlas, c ->
			atlas.textureNamed("number_score_0$c")
		}
		scoreLabel.zPosition = GameLayer.score.rawValue
		root.addChild(scoreLabel)

		// Buttons
		val btnPause = ButtonNode(R.id.btn_pause, "Pause", textureAtlas.textureNamed("button_pause"))
		navigation.addChild(btnPause)

		navigation.zPosition = GameLayer.navigation.rawValue
		root.addChild(navigation)

		val tutorialTap = SKSpriteNode(textureAtlas.textureNamed("tutorial"))
		tutorialTap.name = "tap-tap"
		tutorialTap.texture!!.filteringMode = SKTextureFilteringMode.nearest
		tutorial.addChild(tutorialTap)

		val tutorialReady = SKSpriteNode(textureAtlas.textureNamed("text_ready"))
		tutorialReady.name = "get-ready"
		tutorialReady.texture!!.filteringMode = SKTextureFilteringMode.nearest
		tutorial.addChild(tutorialReady)

		tutorial.zPosition = GameLayer.tutorial.rawValue
		root.addChild(tutorial)

		groundObstacleTemp1.name = "ground-pipe-1"
		groundObstacleTemp1.texture!!.filteringMode = SKTextureFilteringMode.nearest
		groundObstacleTemp2.name = "ground-pipe-2"
		groundObstacleTemp2.texture!!.filteringMode = SKTextureFilteringMode.nearest
		skyObstacleTemp1.name = "sky-pipe-1"
		skyObstacleTemp1.texture!!.filteringMode = SKTextureFilteringMode.nearest
		skyObstacleTemp2.name = "sky-pipe-2"
		skyObstacleTemp2.texture!!.filteringMode = SKTextureFilteringMode.nearest
	}

	private fun initFX(tag: String) {
		if (sounds.isNotEmpty()) {
			throw Exception("!-  already init FX")
		}

		sounds.addAll(arrayOf(flapSound, hitSound, dieSound, scoringSound))

		val vol = Helper.soundVolume
		sounds.forEach {
			it.autoplayLooped = false
			it.soundVol = vol
			addChild(it)
		}
	}

	private fun loadGame(tag: String) {
		gameNo = PreferenceManager.getDefaultSharedPreferences(context).getInt(CommonConfig.Keys.gamesCount, 0) + 1

		Log.i(TAG, "--  loadGame [$tag]: $gameNo")

		Helper.updatePreference("$TAG|loadGame", CommonConfig.Keys.gamesCount, gameNo)

		score = 0
	}

	private fun resizeScene(tag: String) {
		Log.i(TAG, "--  resizeScene [$tag]: $frame")

		// Bird
		val mainCharTextureSize = mainCharacter.texture!!.size()
		mainCharacter.setScale(
			min(
				frame.width * 0.12f / mainCharTextureSize.width,
				frame.height * 0.06f / mainCharTextureSize.height
			)
		)
		mainCharacter.position = Point(this.frame.width * -0.2f, this.frame.height * 0f)

		mainCharacter.physicsBody = SKPhysicsBody(mainCharacter.size.height / 2f)
		mainCharacter.physicsBody!!.isDynamic = false
		mainCharacter.physicsBody!!.allowsRotation = false
		mainCharacter.physicsBody!!.mass = 1f

		mainCharacter.physicsBody!!.categoryBitMask = birdCategory
		mainCharacter.physicsBody!!.collisionBitMask = groundCategory or pipeCategory
		mainCharacter.physicsBody!!.contactTestBitMask = groundCategory or pipeCategory

		if (gameState == State.PREPARED) {
			val hop = SKAction.moveBy(0f, mainCharacter.size.height * 0.5f, 0.3)
			mainCharacter.run(SKAction.repeatForever(SKAction.sequence(hop, hop.reversed())), "hop")
		}

		MAIN_CHARACTER_HEIGHT = mainCharacter.size.height
		val MAIN_CHARACTER_WIDTH = mainCharacter.size.width

		physicsWorld.gravity = Vector(0f, -MAIN_CHARACTER_HEIGHT * 0.35f)

		PIPE_WIDTH = MAIN_CHARACTER_WIDTH * 52f / 34f
		VERTICAL_PIPE_GAP = MAIN_CHARACTER_HEIGHT * 4.295f
		VELOCITY = MAIN_CHARACTER_WIDTH * 4f
		BG_VELOCITY = MAIN_CHARACTER_WIDTH * 0.2f

		// background
		val background = movingObjects.childNode("background")!!
		background.removeAllChildren()
		background.zPosition = GameLayer.ObjectLayer.sky.rawValue

		val hour = Utils.getCurrentHour()
		val bgTexture = textureAtlas.textureNamed(if (hour >= 23 || hour <= 2) "bg_night" else "bg_day")
		bgTexture.filteringMode = SKTextureFilteringMode.nearest

		val bgTextureSize = bgTexture.size()
		val bgWidth = max(frame.width, frame.height * bgTextureSize.width / bgTextureSize.height)
		val bgHeight = max(frame.height, frame.width * bgTextureSize.height / bgTextureSize.width)

		val moveBg = SKAction.moveBy(-bgWidth, 0f, (bgWidth / BG_VELOCITY).toDouble())
		val resetBg = SKAction.moveBy(bgWidth, 0f, 0.0)
		val moveBgsForever = SKAction.repeatForever(SKAction.sequence(moveBg, resetBg))

		for (i in 0..1 + (this.frame.width / bgWidth).toInt()) {
			val node = SKSpriteNode(bgTexture)

			node.size = Size(bgWidth + 1, bgHeight)
			node.position = Point(i * bgWidth, 0f)

			node.run(moveBgsForever)

			background.addChild(node)
		}

		flash.size = frame.size

		// ground
		val grounds = movingObjects.childNode("ground")!!
		grounds.zPosition = GameLayer.ObjectLayer.ground.rawValue
		grounds.removeAllChildren()

		val groundTexture = textureAtlas.textureNamed("land")
		groundTexture.filteringMode = SKTextureFilteringMode.nearest

		val groundTextureSize = groundTexture.size()
		val groundWidth = frame.width
		val groundHeight = frame.width * groundTextureSize.height / groundTextureSize.width
		GROUND_HEIGHT_ON_DISPLAY = min(groundHeight, frame.height * 0.2f)

		val moveGround = SKAction.moveBy(-groundWidth, 0f, ((groundWidth / VELOCITY).toDouble()))
		val resetGround = SKAction.moveBy(groundWidth, 0f, 0.0)
		val moveGroundsForever = SKAction.repeatForever(SKAction.sequence(moveGround, resetGround))

		for (i in 0..1 + (this.frame.width / groundWidth).toInt()) {
			val ground = SKSpriteNode(groundTexture)
			ground.name = "ground-$i"
			ground.size = Size(groundWidth + 1, groundHeight)
			ground.position = Point(i * groundWidth, -frame.height / 2 - groundHeight / 2 + GROUND_HEIGHT_ON_DISPLAY)

			ground.physicsBody = SKPhysicsBody(ground.size)
			ground.physicsBody!!.isDynamic = false
			ground.physicsBody!!.categoryBitMask = groundCategory
			ground.physicsBody!!.collisionBitMask = 0 // todo: ios does not need this
			//ground.physicsBody!!.contactTestBitMask = collisionCategory

			ground.run(moveGroundsForever)

			grounds.addChild(ground)
		}

		// Sky contact
		val sky = SKNode()
		sky.name = "sky"
		sky.position = Point(0f, frame.height * 0.5f + MAIN_CHARACTER_HEIGHT)
		sky.physicsBody = SKPhysicsBody(Size(frame.width, 1f))
		sky.physicsBody!!.isDynamic = false
		sky.physicsBody!!.categoryBitMask = pipeCategory
		sky.physicsBody!!.collisionBitMask = 0 // todo: ios does not need this
		addChild(sky)

		// Pipes
		val groundPipeTexture1Size = groundObstacleTemp1.texture!!.size()
		groundObstacleTemp1.size = Size(PIPE_WIDTH, PIPE_WIDTH * groundPipeTexture1Size.height / groundPipeTexture1Size.width)
		val groundPipeTexture2Size = groundObstacleTemp2.texture!!.size()
		groundObstacleTemp2.size = Size(PIPE_WIDTH, PIPE_WIDTH * groundPipeTexture2Size.height / groundPipeTexture2Size.width)
		val skyPipeTexture1Size = skyObstacleTemp1.texture!!.size()
		skyObstacleTemp1.size = Size(PIPE_WIDTH, PIPE_WIDTH * skyPipeTexture1Size.height / skyPipeTexture1Size.width)
		val skyPipeTexture2Size = skyObstacleTemp2.texture!!.size()
		skyObstacleTemp2.size = Size(PIPE_WIDTH, PIPE_WIDTH * skyPipeTexture2Size.height / skyPipeTexture2Size.width)

		val spawnThenDelayForever = SKAction.repeatForever(
			SKAction.sequence(
				SKAction.run { this.spawnObstacles("") },
				SKAction.wait(PIPE_WIDTH * 3.0 / VELOCITY)
			)
		)
		movingObstacles.run(
			SKAction.sequence(
				SKAction.wait(2.0),
				spawnThenDelayForever
			), "spawn"
		)

		// create the pipes movement actions
		val distanceToMove = frame.width + PIPE_WIDTH
		val movePipes = SKAction.moveBy(-distanceToMove, 0f, (distanceToMove / VELOCITY).toDouble())
		val removePipes = SKAction.removeFromParent()
		movePipesAndRemove = SKAction.sequence(movePipes, removePipes)

		scoreLabel.fontSize = min(frame.width * 0.1f, frame.height * 0.04f)
		scoreLabel.textSpace = scoreLabel.fontSize * 0.1f
		scoreLabel.position = Point(0f, frame.height * 0.35f)

		val tutorialTap = tutorial.childNode("tap-tap") as SKSpriteNode
		tutorialTap.setScale(mainCharacter.xScale)

		val tutorialReady = tutorial.childNode("get-ready") as SKSpriteNode
		tutorialReady.setScale(mainCharacter.xScale)
		tutorialReady.position.y = tutorialTap.frame.maxY + frame.height * 0.04f + tutorialReady.size.height / 2

		tutorial.position.y = frame.height * -0.02f

		val navHeight = min(frame.height * 0.03f, frame.width * 0.09f)
		navigation.position = Point(0f, frame.height * 0.45f)
		val pauseBtn = navigation.childNode("Pause") as ButtonNode
		val pauseImg = pauseBtn.imgNode!!
		pauseImg.size =
			Size(navHeight * 1.6f * pauseImg.texture!!.size().width / pauseImg.texture!!.size().height, navHeight * 1.6f)
		pauseBtn.size = Size(navHeight * 1.7f, navHeight * 1.7f)
		pauseBtn.position = Point(-frame.width * 0.4f, 0f)

		// updateScene("resizeScene|\(tag)")
	}

	private var pipeNo = 0
	private fun spawnObstacles(tag: String) {
		pipeNo++

		val pipePair = SKNode()
		pipePair.position = Point((frame.width + PIPE_WIDTH) / 2, 0f)
		pipePair.zPosition = GameLayer.ObjectLayer.obstacle.rawValue

		val height = MAIN_CHARACTER_HEIGHT * 4.0
		val y: Float = Random.nextDouble(-height, height).toFloat()

		val pipeDown = skyObstacleTemp1.clone()
		pipeDown.name = "pipe-down-$pipeNo"
		pipeDown.position = Point(0f, (GROUND_HEIGHT_ON_DISPLAY + VERTICAL_PIPE_GAP + pipeDown.size.height) / 2 + y)

		pipeDown.physicsBody = SKPhysicsBody(pipeDown.size)
		pipeDown.physicsBody!!.isDynamic = false
		pipeDown.physicsBody!!.categoryBitMask = pipeCategory
		pipeDown.physicsBody!!.collisionBitMask = 0 // todo: ios does not need this
		pipeDown.physicsBody!!.contactTestBitMask = birdCategory
		pipePair.addChild(pipeDown)

		val pipeUp = groundObstacleTemp1.clone()
		pipeUp.name = "pipe-up-$pipeNo"
		pipeUp.position = Point(0f, (GROUND_HEIGHT_ON_DISPLAY - VERTICAL_PIPE_GAP - pipeUp.size.height) / 2 + y)

		pipeUp.physicsBody = SKPhysicsBody(pipeUp.size)
		pipeUp.physicsBody!!.isDynamic = false
		pipeUp.physicsBody!!.categoryBitMask = pipeCategory
		pipeUp.physicsBody!!.collisionBitMask = 0 // todo: ios does not need this
		pipeUp.physicsBody!!.contactTestBitMask = birdCategory
		pipePair.addChild(pipeUp)

		val contactNode = SKNode()
		contactNode.name = "score-$pipeNo"
		contactNode.position = Point(pipeDown.size.width * 0.5f, this.frame.midY)
		contactNode.physicsBody = SKPhysicsBody(Size(1f, this.frame.height))
		contactNode.physicsBody!!.isDynamic = false
		contactNode.physicsBody!!.categoryBitMask = scoreCategory
		contactNode.physicsBody!!.collisionBitMask = 0 // todo: ios does not need this
		contactNode.physicsBody!!.contactTestBitMask = birdCategory
		pipePair.addChild(contactNode)

		pipePair.run(movePipesAndRemove)
		movingObstacles.addChild(pipePair)

		// Log.d(TAG, "--  spawnObstacles [$tag]")
	}

	private fun startGame(tag: String) {
		movingObstacles.speed = 1f

		backgroundSoundPlayer?.stop()
		// playSound(gameStartSound)

		tutorial.run(
			SKAction.sequence(
				SKAction.fadeOut(0.3),
				SKAction.removeFromParent()
			)
		)

		mainCharacter.removeAction("hop")
		mainCharacter.physicsBody!!.isDynamic = true

		gameState = State.STARTED

		Log.i(TAG, "--  Game started")
	}

	override fun pause(tag: String) {
		super.pause(tag)

		stateMachine.enter(LevelScenePauseState::class)
	}

	override fun resume(tag: String) {
		super.resume(tag)

		stateMachine.enter(LevelSceneActiveState::class)
	}

	fun onEndGame(action: (Int) -> Void) {
		endGameAction = action
	}

	override fun touchesBegan(touches: Set<UITouch>, event: MotionEvent) {
		super.touchesBegan(touches, event)

		processInput("touch")
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
		// Log.i("--  \(TAG) | pressesDidBegin: \(presses.map { p in p.key?.charactersIgnoringModifiers })")

		processInput("press")
		return super.onKeyUp(keyCode, event)
	}

	private fun processInput(tag: String) {
		if (!isUserInteractionEnabled) {
			Log.i(TAG, "!-  processInput: ${isUserInteractionEnabled}")
			return
		}

		if (gameState == State.PREPARED) {
			startGame("input|$tag")
		} else if (gameState == State.PAUSED) {
			resume("input|$tag")
		}

		if (gameState == State.STARTED) {
			mainCharacter.physicsBody!!.velocity = Vector.zero
			mainCharacter.physicsBody!!.applyImpulse(Vector(0f, mainCharacter.physicsBody!!.mass * MAIN_CHARACTER_HEIGHT * 17))
			flapSound.stop()
			playSound(flapSound)
		}
	}

	override fun didBegin(contact: SKPhysicsContact) {
		Log.d(TAG, "--  physics contact: ${contact.bodyA.node!!.name!!} >< ${contact.bodyB.node!!.name!!} | $gameState")

		if (gameState != State.ENDED && gameState != State.FINISHED) {
			if ((contact.bodyA.categoryBitMask and scoreCategory) == scoreCategory
				|| (contact.bodyB.categoryBitMask and scoreCategory) == scoreCategory
			) {
				score += 1
				playSound(scoringSound)
			} else if ((contact.bodyA.categoryBitMask and pipeCategory) == pipeCategory
				|| (contact.bodyB.categoryBitMask and pipeCategory) == pipeCategory
			) {
				endGame("PhysicsContact|1")
			}
		}
		if (gameState != State.FINISHED
			&& ((contact.bodyA.categoryBitMask and groundCategory) == groundCategory
					|| (contact.bodyB.categoryBitMask and groundCategory) == groundCategory)
		) {
			endGame("PhysicsContact|2")
			finishScene("PhysicsContact")
		}
	}

	private fun endGame(tag: String) {
		if (gameState == State.ENDED) {
			return
		}
		Log.i(TAG, "--  endGame [$tag]")

		gameState = State.ENDED
		setUserInteraction("endGame|$tag", false)
		root.isUserInteractionEnabled = false

		vibrate(Vibration.Effect1)
		playSound(hitSound)

		movingObjects.speed = 0f

		mainCharacter.physicsBody!!.collisionBitMask = groundCategory // in case bird lands on top of pipe
		mainCharacter.physicsBody!!.velocity.dx = 0f

		flash.run(
			SKAction.repeat(
				SKAction.sequence(
					SKAction.unhide(),
					SKAction.wait(0.1),
					SKAction.hide(),
					SKAction.wait(0.1)
				), 2
			)
		)

		playSound(dieSound, 0.3)
	}

	private fun finishScene(tag: String) {
		if (gameState == State.FINISHED) {
			throw Error("!-  game already finished")
		}
		Log.i(TAG, "--  finishScene [$tag]")

		gameState = State.FINISHED

		mainCharacter.speed = 0f
		physicsWorld.speed = 0f

		dieSound.stop()

		run(SKAction.wait(0.8)) {
			this.root.speed = 0f
			this.scoreLabel.isHidden = true

			// GameCenterHelper.getInstance().reportAchievement(TAG, "game_$gameNo")
			// GameCenterHelper.getInstance().reportAchievement(TAG, "score_$score")
			val isSummitedToGC = GameCenterHelper.getInstance().submitScore(TAG, this.score)
			saveScoreLocally(this.score, isSummitedToGC)

			this.stateMachine.enter(LevelSceneFinishState::class)

			LocalBroadcastManager.getInstance(context)
				.sendBroadcast(Intent(Notification.Name.GameFinished.toString()).apply {
					putExtra("object", gameNo)
					putExtra("tag", TAG)
				})

			this.endGameAction?.invoke(this.score)
		}
	}

	private fun saveScoreLocally(score: Int, isSummitedToGC: Boolean) {
		val s = Score(score)
		val gameCenterHelper = GameCenterHelper.getInstance()
		s.accountId = gameCenterHelper.getLastSignedInAccount()?.id
		s.accountName = gameCenterHelper.getLastSignedInAccount()?.displayName
		s.playerId = gameCenterHelper.mCurrentPlayer?.playerId
		s.playerName = gameCenterHelper.mCurrentPlayer?.displayName
		s.isSummitedGC = isSummitedToGC
		runBlocking { AppDatabase.instance.scoreDao().insert(s) }
	}
}

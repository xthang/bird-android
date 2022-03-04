package x.game.bird.scene

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import x.common.BaseScene
import x.common.ButtonNode
import x.common.Notification
import x.core.graphics.Point
import x.core.graphics.SKSceneScaleMode
import x.core.graphics.Size
import x.game.bird.R
import x.spritekit.*
import kotlin.math.max
import kotlin.math.min

class HomeScene(context: Context) : BaseScene(context) {

	companion object {
		private const val TAG = "Home"
	}

	private var sceneLoaded = false

	// Calculation

	// Nodes
	private val textureAtlas = SKTextureAtlas(context, R.drawable.atlas)

	private var root = SKNode()

	private val movingObjects = SKNode()

	private var banner = SKNode()
	private var title = SKSpriteNode(textureAtlas.textureNamed("title"))
	private var mainCharacter = SKSpriteNode()

	private var copyright = SKLabelNode("© XT 2022").apply {
		typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.resources.getFont(R.font.main)
		} else {
			ResourcesCompat.getFont(context, R.font.main)!!
		}
	}

	private var buttons = SKNode()
	private var btnPlay = ButtonNode(R.id.btn_play, "btn_play", textureAtlas.textureNamed("button_play"))
	private var btnLeaderboards = ButtonNode(R.id.btn_leaderboards, "btn_leaderboards", textureAtlas.textureNamed("button_score"))
	private var btnRate = ButtonNode(R.id.btn_rate, "btn_rate", textureAtlas.textureNamed("button_rate"))
	private var btnAds = ButtonNode(R.id.btn_ads, "btn_ads", context.resources, R.drawable.button_ads)


	init {
		sceneDidLoad("")

		setUserInteraction("", true)
	}

	override fun sceneDidLoad(tag: String) {
		super.sceneDidLoad(tag)

		scaleMode = SKSceneScaleMode.resizeFill

		initObjects("sceneDidLoad")
	}

	override fun didMoveTo(tag: String, view: SKView) {
		// Log.i("--  \(TAG) | didMove to view | \(size)")
		super.didMoveTo(tag, view)

		resizeScene("didMove")

		sceneLoaded = true

		LocalBroadcastManager.getInstance(context)
			.sendBroadcast(Intent(Notification.Name.HomeEntered.toString()).apply {
				putExtra("tag", TAG)
			})

		// SceneManager.prepareScene(. game)
	}

	override fun didChangeSize(tag: String, oldSize: Size) {
		super.didChangeSize(tag, oldSize)

		if (sceneLoaded) {
			resizeScene("didChangeSize")
		}
	}

	private fun initObjects(tag: String) {
		addChild(root)

		// MovingObjects
		movingObjects.zPosition = GameLayer.objects.rawValue
		root.addChild(movingObjects)

		val background = SKNode()
		background.name = "background"
		movingObjects.addChild(background)

		val ground = SKNode()
		ground.name = "ground"
		movingObjects.addChild(ground)

		banner.zPosition = GameLayer.mainCharacter.rawValue
		root.addChild(banner)

		title.texture!!.filteringMode = SKTextureFilteringMode.nearest
		banner.addChild(title)

		val mainCharTextures = linkedSetOf("bird0_0", "bird0_1", "bird0_2")
		mainCharacter.texture = textureAtlas.textureNamed(mainCharTextures.first())
		val anim = SKAction.animate(mainCharTextures.map {
			val t = textureAtlas.textureNamed(it)
			t.filteringMode = SKTextureFilteringMode.nearest
			t
		}, 0.08)
		val flap = SKAction.repeatForever(anim)
		mainCharacter.run(flap, "flap")
		banner.addChild(mainCharacter)

		buttons.zPosition = GameLayer.navigation.rawValue
		buttons.addChild(btnPlay)
		buttons.addChild(btnLeaderboards)
		buttons.addChild(btnRate)
		buttons.addChild(btnAds)
		buttons.children.forEach {
			val b = it as ButtonNode
			b.imgNode!!.texture!!.filteringMode = SKTextureFilteringMode.nearest
		}
		root.addChild(buttons)

		root.addChild(copyright)
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

		title.setScale(mainCharacter.xScale)
		title.position.x = -title.size.width * 0.17f
		mainCharacter.position.x = title.size.width * 0.57f

		banner.position.y = frame.height * 0.27f

		val hop = SKAction.moveBy(0f, mainCharacter.size.height * 0.5f, 0.3)
		banner.run(SKAction.repeatForever(SKAction.sequence(hop, hop.reversed())), "hop")

		val MAIN_CHARACTER_WIDTH = mainCharacter.size.width

		val VELOCITY = MAIN_CHARACTER_WIDTH * 4f
		val BG_VELOCITY = MAIN_CHARACTER_WIDTH * 0.2f

		// background
		val background = movingObjects.childNode("background")!!
		background.removeAllChildren()
		background.zPosition = GameLayer.ObjectLayer.sky.rawValue

		val bgTexture = textureAtlas.textureNamed("bg_day")
		bgTexture.filteringMode = SKTextureFilteringMode.nearest

		val bgTextureSize = bgTexture.size()
		val bgWidth = max(frame.width, frame.height * bgTextureSize.width / bgTextureSize.height)
		val bgHeight = max(frame.height, frame.width * bgTextureSize.height / bgTextureSize.width)

		val moveBg = SKAction.moveBy(-bgWidth, 0f, ((bgWidth / BG_VELOCITY).toDouble()))
		val resetBg = SKAction.moveBy(bgWidth, 0f, 0.0)
		val moveBgsForever = SKAction.repeatForever(SKAction.sequence(moveBg, resetBg))

		for (i in 0..1 + (frame.width / bgWidth).toInt()) {
			val node = SKSpriteNode(bgTexture)

			node.size = Size(bgWidth + 1, bgHeight)
			node.position = Point(i * bgWidth, 0f)

			node.run(moveBgsForever)

			background.addChild(node)
		}

		// ground
		val grounds = movingObjects.childNode("ground")!!
		grounds.zPosition = GameLayer.ObjectLayer.ground.rawValue
		grounds.removeAllChildren()

		val groundTexture = textureAtlas.textureNamed("land")
		groundTexture.filteringMode = SKTextureFilteringMode.nearest

		val groundTextureSize = groundTexture.size()
		val groundWidth = frame.width
		val groundHeight = frame.width * groundTextureSize.height / groundTextureSize.width
		val GROUND_HEIGHT_ON_DISPLAY = min(groundHeight, frame.height * 0.2f)

		val moveGround = SKAction.moveBy(-groundWidth, 0f, ((groundWidth / VELOCITY).toDouble()))
		val resetGround = SKAction.moveBy(groundWidth, 0f, 0.0)
		val moveGroundsForever = SKAction.repeatForever(SKAction.sequence(moveGround, resetGround))

		for (i in 0..1 + (frame.width / groundWidth).toInt()) {
			val ground = SKSpriteNode(groundTexture)
			ground.name = "ground"
			ground.size = Size(groundWidth + 1, groundHeight)
			ground.position = Point(i * groundWidth, -frame.height / 2 - groundHeight / 2 + GROUND_HEIGHT_ON_DISPLAY)

			ground.run(moveGroundsForever)

			grounds.addChild(ground)
		}

		copyright.fontSize = min(frame.height * 0.025f, frame.width * 0.04f)
		copyright.position.y = grounds.children.first().frame.maxY - grounds.children.first().frame.height * 0.35f
		copyright.zPosition = GameLayer.navigation.rawValue

		arrayListOf(btnPlay, btnLeaderboards).forEach { b ->
			val btnTextureSize = b.imgNode!!.texture!!.size()
			val scale = min(frame.height * 0.12f / btnTextureSize.height, frame.width * 0.3f / btnTextureSize.width)
			b.imgNode!!.setScale(scale)
		}
		arrayListOf(btnRate, btnAds).forEach { b ->
			val scale = btnPlay.imgNode!!.xScale * 1f
			b.imgNode!!.setScale(scale)
		}
		buttons.children.forEach {
			val b = it as ButtonNode
			b.size = Size(b.imgNode!!.size.width + 3, b.imgNode!!.size.height + 3)
		}
		btnPlay.position = Point(-frame.width * 0.22f, 0f)
		btnLeaderboards.position = Point(frame.width * 0.22f, 0f)
		btnRate.position = Point(0f, btnPlay.size.height * 0.9f)
		btnAds.position = Point(0f, -btnPlay.size.height * 0.9f)

		buttons.position.y = -frame.height * 0.15f
	}
}
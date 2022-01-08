package x.common

import android.content.res.Resources
import android.view.MotionEvent
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import x.common.sprite.BaseButtonNode
import x.common.ui.IButton
import x.common.ui.UITouch
import x.spritekit.SKSpriteNode
import x.spritekit.SKTexture

open class ButtonNode : BaseButtonNode, IButton {

	companion object {
		private val TAG = ButtonNode::class.simpleName!!
	}

	constructor(@IdRes id: Int) : super(id)
	constructor(@IdRes id: Int, name: String, resources: Resources, @DrawableRes textureId: Int) : this(id) {
		this.name = name
		this.imgNode = SKSpriteNode(resources, textureId)
	}
	constructor(@IdRes id: Int, name: String, texture: SKTexture) : this(id) {
		this.name = name
		this.imgNode = SKSpriteNode(texture)
	}

	override fun touchesBegan(touches: Set<UITouch>, event: MotionEvent) {
		super.touchesBegan(touches, event)

		isFocused = true
	}

	override fun touchesEnded(touches: Set<UITouch>, event: MotionEvent) {
		super.touchesEnded(touches, event)

		isFocused = false
	}

	override fun touchesCancelled(touches: Set<UITouch>, event: MotionEvent) {
		super.touchesCancelled(touches, event)

		isFocused = false
	}
}
package x.game.bird

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import x.common.CommonConfig
import x.common.Helper
import x.game.bird.MainActivity


class WelcomeActivity : ComponentActivity() {

	companion object {
		private val TAG = WelcomeActivity::class.simpleName!!
	}

	private lateinit var mLayouts: Array<ViewGroup>

	private lateinit var mViewPager: ViewPager2
	private lateinit var mDotsLayout: LinearLayout


	override fun onCreate(savedInstanceState: Bundle?) {
		Log.d(TAG, "--  onCreate")
		super.onCreate(savedInstanceState)

		setContentView(R.layout.welcome_view)

		mViewPager = findViewById(R.id.pager)

		val mInflater = LayoutInflater.from(this)
		mLayouts = arrayOf(
			mInflater.inflate(R.layout.welcome_slide, mViewPager, false) as ViewGroup,
		)
		mLayouts.forEach {
			val mBtnOK = it.findViewById<Button>(R.id.btn_lets_play)
			mBtnOK?.setOnClickListener {
				launchHomeScreen()
			}
		}

		mDotsLayout = findViewById(R.id.layoutDots)
		setBottomDots(0)

		mViewPager.adapter = IntroViewPagerAdapter()
		mViewPager.registerOnPageChangeCallback(mViewPagerChangeListener)
	}

	private val mViewPagerChangeListener: ViewPager2.OnPageChangeCallback =
		object : ViewPager2.OnPageChangeCallback() {
			override fun onPageScrolled(
				position: Int, positionOffset: Float, positionOffsetPixels: Int
			) {
				// Log.d(TAG, "--  onPageScrolled: $position - $positionOffset - $positionOffsetPixels")
			}

			override fun onPageSelected(position: Int) {
				Log.d(TAG, "--  onPageSelected: $position")

				setBottomDots(position)
			}

			override fun onPageScrollStateChanged(state: Int) {
				// Log.d(TAG, "--  onPageScrollStateChanged: $state")
			}
		}

	private fun setBottomDots(currentPage: Int) {
		val mDots: Array<TextView?> = arrayOfNulls(mLayouts.size)
		val colorsActive: IntArray = resources.getIntArray(R.array.array_dot_active)
		val colorsInActive: IntArray = resources.getIntArray(R.array.array_dot_inactive)
		mDotsLayout.removeAllViews()
		for (i in mDots.indices) {
			mDots[i] = TextView(this)
			mDots[i]!!.text = Html.fromHtml("•")
			mDots[i]!!.textSize = 35f
			mDots[i]!!.setTextColor(colorsInActive[currentPage])
			mDotsLayout.addView(mDots[i])
		}
		if (mDots.isNotEmpty()) {
			mDots[currentPage]!!.setTextColor(colorsActive[currentPage])
		}
	}

	private fun launchHomeScreen() {
		val intent = Intent(this, MainActivity::class.java)
		intent.putExtra("startingActivity", WelcomeActivity::class.simpleName)
		startActivity(intent)
		finish()
		// Helper.updatePreference(TAG, CommonConfig.Keys.welcomeVersion, Helper.appLongVersion)
	}

	inner class IntroViewPagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			Log.d(TAG, "--  onCreateViewHolder: viewType: $viewType")

			val view = FrameLayout(parent.context)
			view.layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
			)
			return object : RecyclerView.ViewHolder(view) {}
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			Log.d(TAG, "--  onBindViewHolder: position: $position")

			(holder.itemView as ViewGroup).addView(mLayouts[position])
		}

		override fun getItemCount(): Int {
			return mLayouts.size
		}
	}
}
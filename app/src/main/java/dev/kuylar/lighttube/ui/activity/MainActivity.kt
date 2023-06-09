package dev.kuylar.lighttube.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.SearchAutoComplete
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.get
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.elevation.SurfaceColors
import dev.kuylar.lighttube.R
import dev.kuylar.lighttube.Utils
import dev.kuylar.lighttube.api.LightTubeApi
import dev.kuylar.lighttube.databinding.ActivityMainBinding
import dev.kuylar.lighttube.ui.VideoPlayerManager
import dev.kuylar.lighttube.ui.fragment.UpdateFragment
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min


class MainActivity : AppCompatActivity() {

	private lateinit var navController: NavController
	private lateinit var binding: ActivityMainBinding
	lateinit var miniplayer: BottomSheetBehavior<View>
	private lateinit var miniplayerScene: MotionLayout
	lateinit var player: VideoPlayerManager
	lateinit var api: LightTubeApi
	private var loadingSuggestions = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// set status bar & bottom nav bar colors
		val color = SurfaceColors.SURFACE_2.getColor(this)
		window.statusBarColor = color
		window.navigationBarColor = color

		val sp = getSharedPreferences("main", MODE_PRIVATE)
		if (!sp.contains("instanceHost")) {
			startActivity(Intent(this, SetupActivity::class.java))
			finish()
			return
		}

		api = LightTubeApi(this)

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		val navView: BottomNavigationView = binding.navView
		navController = findNavController(R.id.nav_host_fragment_activity_main)
		// Passing each menu ID as a set of Ids because each
		// menu should be considered as top level destinations.
		val appBarConfiguration = AppBarConfiguration(
			setOf(
				R.id.navigation_home, R.id.navigation_subscriptions, R.id.navigation_library
			)
		)

		setSupportActionBar(binding.toolbar)
		setupActionBarWithNavController(navController, appBarConfiguration)
		navView.setupWithNavController(navController)

		onBackPressedDispatcher.addCallback(this) {
//			if (player.closeSheets())
//				Toast.makeText(this@MainActivity, "closed player sheets", Toast.LENGTH_LONG).show()
//			else if (player.exitFullscreen())
//				Toast.makeText(this@MainActivity, "exited fullscreen", Toast.LENGTH_LONG).show()
//			else if (minimizePlayer())
//				Toast.makeText(this@MainActivity, "minimized player", Toast.LENGTH_LONG).show()
//			else if (navController.popBackStack())
//				Toast.makeText(this@MainActivity, "pop back stack", Toast.LENGTH_LONG).show()
//			else
//				Toast.makeText(this@MainActivity, "nothing else to do", Toast.LENGTH_LONG).show()
			if (!player.closeSheets()) // attempt to close details/comments
				if (!player.exitFullscreen()) // attempt to exit fullscreen
					if (!minimizePlayer()) // attempt to minimize the player sheet
						if (!navController.popBackStack()) // attempt to go back on the fragment history
							finish() // close the app
		}

		val miniplayerView: View = findViewById(R.id.miniplayer)
		miniplayerScene = miniplayerView.findViewById(R.id.miniplayer_motion)
		miniplayer = BottomSheetBehavior.from(miniplayerView)
		miniplayer.state = BottomSheetBehavior.STATE_HIDDEN

		player = VideoPlayerManager(this)

		miniplayer.addBottomSheetCallback(object :
			BottomSheetBehavior.BottomSheetCallback() {
			@SuppressLint("SwitchIntDef")
			override fun onStateChanged(bottomSheet: View, newState: Int) {
				when (newState) {
					BottomSheetBehavior.STATE_HIDDEN -> player.stop()

					BottomSheetBehavior.STATE_DRAGGING -> {
						supportActionBar?.show()
					}

					BottomSheetBehavior.STATE_EXPANDED -> {
						supportActionBar?.hide()
					}
				}
			}

			override fun onSlide(bottomSheet: View, slideOffset: Float) {
				if (slideOffset < 0)
					player.setVolume(max(0.1f, 1 + slideOffset * 3))
				else
					player.setVolume(1f)
				miniplayerScene.progress = max(0f, min(1f, slideOffset * 5))

				if (slideOffset > .3)
					binding.navView.visibility = View.GONE
				else
					binding.navView.visibility = View.VISIBLE

				player.toggleControls(slideOffset * 5 > 0.9)
			}
		})

		// check for updates
		thread {
			val update = Utils.checkForUpdates(this)
			if (update != null) {
				val skippedUpdate = sp.getString("skippedUpdate", null)
				if (skippedUpdate == update.latestVersion) {
					Log.i("UpdateChecker", "User skipped update $skippedUpdate. Not showing the update dialog.")
				} else {
					runOnUiThread {
						UpdateFragment(update).show(supportFragmentManager, null)
					}
				}
			}
		}
	}

	private fun minimizePlayer(): Boolean {
		return if (miniplayer.state == BottomSheetBehavior.STATE_EXPANDED) {
			miniplayer.state = BottomSheetBehavior.STATE_COLLAPSED
			true
		} else false
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.top_app_bar, menu)

		val searchView = menu[0].actionView as SearchView
		val searchAutoComplete: SearchAutoComplete =
			searchView.findViewById(androidx.appcompat.R.id.search_src_text) as SearchAutoComplete

		searchAutoComplete.onItemClickListener =
			OnItemClickListener { adapterView, view, itemIndex, id ->
				val queryString = adapterView.getItemAtPosition(itemIndex) as String
				searchAutoComplete.setText(queryString)
				searchAutoComplete.setSelection(queryString.length)
			}

		searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
			override fun onQueryTextSubmit(query: String): Boolean {
				val bundle = Bundle()
				bundle.putString("query", query)
				navController.navigate(R.id.navigation_search, bundle)
				searchView.isSelected = false
				searchView.clearFocus()
				searchAutoComplete.dismissDropDown()
				return false
			}

			override fun onQueryTextChange(newText: String): Boolean {
				return updateSuggestions(newText, searchAutoComplete)
			}
		})

		return true
	}

	private fun updateSuggestions(
		newText: String,
		searchAutoComplete: SearchAutoComplete
	): Boolean {
		if (loadingSuggestions) return false
		if (newText.isEmpty()) return false
		loadingSuggestions = true
		thread {
			try {
				val suggestions = api.searchSuggestions(newText)
				runOnUiThread {
					val newsAdapter = ArrayAdapter(
						this@MainActivity,
						android.R.layout.simple_dropdown_item_1line,
						suggestions.data!!.autocomplete
					)
					searchAutoComplete.setAdapter(newsAdapter)
					if (searchAutoComplete.isSelected)
						searchAutoComplete.showDropDown()
					loadingSuggestions = false
				}
			} catch (e: Exception) {
				loadingSuggestions = false
			}
		}
		return true
	}

	fun setLoading(loading: Boolean) {
		try {
			binding.loadingBar.visibility = if (loading) View.VISIBLE else View.GONE
		} catch (_: Exception) {
		}
	}
}
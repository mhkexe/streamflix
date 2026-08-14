package com.nextservices.nextvision.activities.main

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.NavController
import androidx.navigation.navOptions
import androidx.navigation.ui.onNavDestinationSelected
import com.tanasi.navigation.widget.setupWithNavController
import com.nextservices.nextvision.BuildConfig
import com.nextservices.nextvision.R
import com.nextservices.nextvision.NextVisionApp
import com.nextservices.nextvision.database.AppDatabase
import com.nextservices.nextvision.databinding.ActivityMainTvBinding
import com.nextservices.nextvision.databinding.ContentHeaderMenuMainTvBinding
import com.nextservices.nextvision.fragments.player.PlayerTvFragment
import com.nextservices.nextvision.fragments.home.HomeTvFragment
import com.nextservices.nextvision.fragments.movies.MoviesTvFragment
import com.nextservices.nextvision.fragments.favorites.FavoritesTvFragment
import com.nextservices.nextvision.fragments.tv_shows.TvShowsTvFragment
import com.nextservices.nextvision.ui.UpdateAppTvDialog
import com.nextservices.nextvision.providers.IptvProvider
import com.nextservices.nextvision.providers.Provider
import com.nextservices.nextvision.providers.TmdbProvider
import com.nextservices.nextvision.utils.AppLanguageManager
import com.nextservices.nextvision.utils.ThemeManager
import com.nextservices.nextvision.utils.UserPreferences
import com.nextservices.nextvision.utils.StartupTrace
import com.nextservices.nextvision.utils.getCurrentFragment
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainTvActivity : FragmentActivity() {

    private companion object {
        const val STARTUP_TIMEOUT_MS = 15_000L

    }

    private var _binding: ActivityMainTvBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<MainViewModel>()

    private var isStartupCompleted = false
    private var startupLoadingShown = false
    private var updateCheckStarted = false

    private lateinit var updateAppDialog: UpdateAppTvDialog
    private lateinit var navController: NavController

    override fun attachBaseContext(newBase: android.content.Context) {
        StartupTrace.mark("MainTvActivity.attachBaseContext.begin")
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
        StartupTrace.mark("MainTvActivity.attachBaseContext.end")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark("MainTvActivity.onCreate.begin")
        setTheme(ThemeManager.tvThemeRes(UserPreferences.selectedTheme))
        super.onCreate(savedInstanceState)
        StartupTrace.mark("MainTvActivity.super.onCreate.end")

        initializeMainContent(savedInstanceState)
        StartupTrace.mark("MainTvActivity.onCreate.end")
    }

    private fun initializeMainContent(savedInstanceState: Bundle?) {
        StartupTrace.mark("MainTvActivity.content_setup.begin")
        _binding = ActivityMainTvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        StartupTrace.mark("MainTvActivity.content_view_ready")
        applyThemeNavigationChrome()

        val navHostFragment = this.supportFragmentManager
            .findFragmentById(binding.navMainFragment.id) as NavHostFragment
        navController = navHostFragment.navController

        if (UserPreferences.currentProvider == null) {
            UserPreferences.currentProvider = TmdbProvider("en")
        }

        navController.graph = navController.navInflater
            .inflate(R.navigation.nav_main_graph_tv)
            .apply {
                setStartDestination(R.id.home)
            }
        StartupTrace.mark("MainTvActivity.graph_setup.end")

        adjustLayoutDelta(null, null)

        if (BuildConfig.APP_LAYOUT == "mobile" || (BuildConfig.APP_LAYOUT != "tv" && !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))) {
            finish()
            startActivity(Intent(this, MainMobileActivity::class.java))
            return
        }

        binding.navMain.setupWithNavController(navController)
        binding.navMain.setOnItemSelectedListener { item ->
            item.onNavDestinationSelected(navController)
            navController.popBackStack(item.itemId, inclusive = false)
            true
        }
        binding.navMain.setContentFocusTarget(binding.navMainFragment.id)
        updateNavigationVisibility()

        setupStartupOverlay(savedInstanceState != null)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.home, R.id.movies, R.id.tv_shows, R.id.favorites -> {
                    if (!isStartupCompleted) {
                        binding.navMain.visibility = View.GONE
                        return@addOnDestinationChangedListener
                    }
                    binding.navMain.visibility = View.VISIBLE
                    updateNavigationVisibility()
                    binding.navMain.post {
                        requestCurrentMenuFocus()
                    }
                }
                else -> binding.navMain.visibility = View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    is MainViewModel.State.SuccessCheckingUpdate -> {
                        updateAppDialog = UpdateAppTvDialog(this@MainTvActivity, state.newReleases).also {
                            it.setOnUpdateClickListener { _ ->
                                if (!it.isLoading) viewModel.downloadUpdate(this@MainTvActivity, state.asset)
                            }
                            it.show()
                        }
                    }
                    MainViewModel.State.DownloadingUpdate -> if (::updateAppDialog.isInitialized) updateAppDialog.isLoading = true
                    is MainViewModel.State.SuccessDownloadingUpdate -> {
                        viewModel.installUpdate(this@MainTvActivity, state.apk)
                        if (::updateAppDialog.isInitialized) updateAppDialog.hide()
                    }
                    MainViewModel.State.InstallingUpdate -> if (::updateAppDialog.isInitialized) updateAppDialog.isLoading = true
                    is MainViewModel.State.FailedUpdate -> {
                        Toast.makeText(this@MainTvActivity, state.error.message ?: "Update failed", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.startupOverlay.visibility == View.VISIBLE && !isStartupCompleted) {
                    finish()
                    return
                }
                when (navController.currentDestination?.id) {
                    R.id.home -> if (binding.navMain.hasFocus()) finish() else requestCurrentMenuFocus()
                    else -> {
                        val handled = (getCurrentFragment() as? PlayerTvFragment)?.onBackPressed() ?: false
                        if (handled) return

                        if (!navController.popBackStack()) {
                            finish()
                        }
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
    }

    private fun setupStartupOverlay(isRestored: Boolean) {
        if (isRestored || intent.getBooleanExtra(StartupSplashActivity.EXTRA_HOME_PRELOADED, false)) {
            binding.startupOverlay.visibility = View.GONE
            completeStartup()
            return
        }

        binding.navMain.visibility = View.GONE
        binding.navMainFragment.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        binding.startupOverlay.visibility = View.VISIBLE

        showStartupLoading()
    }

    private fun showStartupLoading() {
        if (startupLoadingShown || isStartupCompleted) return
        startupLoadingShown = true
        binding.startupProgress.alpha = 0f
        binding.startupProgress.visibility = View.VISIBLE
        binding.startupProgress.animate().alpha(1f).setDuration(250).start()

        lifecycleScope.launch {
            withTimeoutOrNull(STARTUP_TIMEOUT_MS) { NextVisionApp.preloadReady.await() }
            hideStartupOverlay()
        }
    }

    private fun hideStartupOverlay() {
        binding.startupOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                _binding?.startupOverlay?.visibility = View.GONE
                completeStartup()
            }
            .start()
    }

    private fun completeStartup() {
        if (isStartupCompleted) return
        isStartupCompleted = true

        binding.navMainFragment.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.navMain.isFocusedByDefault = true
        }

        val destinationId = navController.currentDestination?.id
        if (destinationId in setOf(
                R.id.home, R.id.movies, R.id.tv_shows, R.id.favorites
            )
        ) {
            binding.navMain.visibility = View.VISIBLE
            updateNavigationVisibility()
            binding.navMain.post {
                if (binding.navMain.visibility == View.VISIBLE) {
                    binding.navMain.requestHomeFocus(R.id.home)
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (binding.navMain.hasFocus()) {
                return when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> binding.navMain.moveFocusLeft()
                    KeyEvent.KEYCODE_DPAD_RIGHT -> binding.navMain.moveFocusRight()
                    KeyEvent.KEYCODE_DPAD_UP -> true
                    KeyEvent.KEYCODE_DPAD_DOWN -> binding.navMainFragment.requestFocus()
                    else -> super.dispatchKeyEvent(event)
                }
            }

            if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && canOpenNavigationMenu()) {
                if (isAtTopContentFocus()) {
                    return requestCurrentMenuFocus()
                }
                val currentFocus = currentFocus
                val upwardTarget = currentFocus?.focusSearch(View.FOCUS_UP)
                if (upwardTarget == null || upwardTarget === currentFocus || isDescendantOfNav(upwardTarget)) {
                    return requestCurrentMenuFocus()
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun canOpenNavigationMenu(): Boolean = when (getCurrentFragment()) {
        is HomeTvFragment, is MoviesTvFragment, is TvShowsTvFragment,
        is FavoritesTvFragment -> true
        else -> false
    }

    private fun isDescendantOfNav(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === binding.navMain) return true
            current = current.parent as? View
        }
        return false
    }

    private fun isAtTopContentFocus(): Boolean {
        return when (val fragment = getCurrentFragment()) {
            is HomeTvFragment -> fragment.isAtTopContentFocus()
            is MoviesTvFragment -> fragment.isAtTopContentFocus()
            is TvShowsTvFragment -> fragment.isAtTopContentFocus()
            else -> false
        }
    }

    private fun requestCurrentMenuFocus(): Boolean {
        val destinationId = navController.currentDestination?.id ?: return false
        val menuItem = binding.navMain.menu.findItem(destinationId)

        if (menuItem?.isVisible == true) {
            menuItem.isChecked = true
            return binding.navMain.requestHomeFocus(destinationId)
        }

        return binding.navMain.requestSelectedMenuFocus()
    }

    private fun applyThemeNavigationChrome() {
        val palette = ThemeManager.palette(UserPreferences.selectedTheme)
        window.statusBarColor = palette.systemBar
        window.navigationBarColor = palette.systemBar
        binding.navMain.setBackgroundResource(R.drawable.bg_tv_navigation_overlay)
        binding.navMain.headerView?.let { headerView ->
            headerView.setBackgroundColor(Color.TRANSPARENT)
            val header = ContentHeaderMenuMainTvBinding.bind(headerView)
            header.tvNavigationHeaderTitle.setTextColor(palette.tvHeaderPrimary)
            header.tvNavigationHeaderSubtitle.setTextColor(palette.tvHeaderSecondary)
        }
    }
    
    private fun updateNavigationVisibility() {
        UserPreferences.currentProvider?.let { provider ->
            binding.navMain.menu.findItem(R.id.movies)?.isVisible = Provider.supportsMovies(provider)
            val tvShowsItem = binding.navMain.menu.findItem(R.id.tv_shows)
            tvShowsItem?.isVisible = Provider.supportsTvShows(provider)
            tvShowsItem?.title = if (provider is IptvProvider)
                getString(R.string.main_menu_all_channels) else getString(R.string.main_menu_tv_shows)
        }
    }

    fun adjustLayoutDelta(deltaX: Int?, deltaY: Int?) {
        val uDeltaX = deltaX ?: UserPreferences.paddingX
        val uDeltaY = deltaY ?: UserPreferences.paddingY
        binding.root.setPadding(uDeltaX, uDeltaY, uDeltaX, uDeltaY)
    }

    private fun navigateToProviderHome(navController: androidx.navigation.NavController) {
        if (!navController.popBackStack(R.id.home, false)) {
            navController.navigate(
                R.id.home,
                null,
                navOptions {
                    launchSingleTop = true
                    popUpTo(R.id.providers) {
                        inclusive = true
                    }
                }
            )
        }
    }
}

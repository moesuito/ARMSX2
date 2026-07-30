package com.armsx2.runtime

import android.app.Presentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import com.armsx2.EmuState
import kr.co.iefriends.pcsx2.NativeApp

/**
 * Owns Android's secondary-display Presentation and hands the single renderer
 * between it and the Activity surface. Desktop/DeX mode always wins.
 */
object DedicatedExternalDisplay : DisplayManager.DisplayListener {
    val enabled = mutableStateOf(false)
    val active = mutableStateOf(false)
    val blockedByDesktopMode = mutableStateOf(false)

    private var activity: MainActivityRuntime? = null
    private var primarySurface: EmulationSurface? = null
    private var displayManager: DisplayManager? = null
    private var presentation: ExternalPresentation? = null
    private var gameActive = false
    private var hostResumed = false

    fun attach(host: MainActivityRuntime, primary: EmulationSurface) {
        if (activity === host && primarySurface === primary) return
        detach(activity)
        activity = host
        primarySurface = primary
        displayManager = host.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        enabled.value = host.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY, false)
        displayManager?.registerDisplayListener(this, null)
        refresh()
    }

    fun detach(host: MainActivityRuntime?) {
        if (host == null || activity !== host) return
        releasePresentation()
        displayManager?.unregisterDisplayListener(this)
        displayManager = null
        primarySurface = null
        activity = null
        gameActive = false
        hostResumed = false
        blockedByDesktopMode.value = false
    }

    fun setEnabled(value: Boolean) {
        enabled.value = value
        activity?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(PREF_KEY, value)
            ?.apply()
        refresh()
    }

    fun setGameState(state: EmuState) {
        gameActive = state == EmuState.RUNNING || state == EmuState.PAUSED
        primarySurface?.setGameActive(gameActive)
        presentation?.surface?.setGameActive(gameActive)
        refresh()
    }

    fun setHostResumed(resumed: Boolean) {
        hostResumed = resumed
        refresh()
    }

    fun onHostConfigurationChanged() {
        refresh()
    }

    override fun onDisplayAdded(displayId: Int) = refresh()

    override fun onDisplayRemoved(displayId: Int) = refresh()

    override fun onDisplayChanged(displayId: Int) = refresh()

    internal fun shouldPresent(
        enabled: Boolean,
        gameActive: Boolean,
        hostResumed: Boolean,
        desktopMode: Boolean,
        hasExternalDisplay: Boolean,
    ): Boolean = enabled && gameActive && hostResumed && !desktopMode && hasExternalDisplay

    private fun refresh() {
        val host = activity ?: return
        val manager = displayManager ?: return
        val desktopMode = isDesktopModeActive(host, manager)
        blockedByDesktopMode.value = enabled.value && desktopMode
        val target = if (desktopMode) null else findPresentationDisplay(host, manager)

        if (!shouldPresent(
                enabled.value,
                gameActive,
                hostResumed,
                desktopMode,
                target != null,
            )
        ) {
            releasePresentation()
            return
        }

        if (presentation?.display?.displayId == target?.displayId) return
        releasePresentation()

        val next = ExternalPresentation(host, target!!) { owner, available ->
            if (presentation === owner) {
                if (available) activatePresentation(owner) else restorePrimarySurface()
            }
        }
        presentation = next
        runCatching {
            next.show()
            next.surface?.setGameActive(gameActive)
        }.onFailure {
            if (presentation === next) releasePresentation()
        }
    }

    private fun activatePresentation(owner: ExternalPresentation) {
        if (presentation !== owner || isDesktopModeActive(
                activity ?: return,
                displayManager ?: return,
            )
        ) {
            refresh()
            return
        }
        primarySurface?.setRenderTargetActive(false)
        if (owner.surface?.setRenderTargetActive(true) == true) {
            active.value = true
        } else {
            restorePrimarySurface(owner.surface)
        }
    }

    private fun restorePrimarySurface(
        externalSurface: EmulationSurface? = presentation?.surface,
    ) {
        externalSurface?.setRenderTargetActive(false)
        if (primarySurface?.setRenderTargetActive(true) != true)
            NativeApp.onNativeSurfaceDestroyed()
        active.value = false
    }

    private fun releasePresentation() {
        val old = presentation ?: run {
            active.value = false
            return
        }
        presentation = null
        restorePrimarySurface(old.surface)
        runCatching { old.dismiss() }
    }

    private fun findPresentationDisplay(
        host: MainActivityRuntime,
        manager: DisplayManager,
    ): Display? {
        @Suppress("DEPRECATION")
        val hostDisplayId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            host.display?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            host.windowManager.defaultDisplay.displayId
        }
        return manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.isValid && it.displayId != hostDisplayId }
    }

    private fun isDesktopModeActive(
        host: MainActivityRuntime,
        manager: DisplayManager,
    ): Boolean {
        if (isDesktopUiMode(host.resources.configuration)) return true
        if (manager.displays.any { display ->
                runCatching {
                    isDesktopUiMode(host.createDisplayContext(display).resources.configuration)
                }.getOrDefault(false)
            }
        ) return true

        // Samsung publishes active DeX displays under this public category.
        return runCatching { manager.getDisplays(SAMSUNG_DESKTOP_CATEGORY).isNotEmpty() }
            .getOrDefault(false)
    }

    internal fun isDesktopUiMode(configuration: Configuration): Boolean =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_DESK

    private class ExternalPresentation(
        context: Context,
        display: Display,
        private val availabilityChanged: (ExternalPresentation, Boolean) -> Unit,
    ) : Presentation(context, display, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
        var surface: EmulationSurface? = null
            private set

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window?.apply {
                addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                )
                setBackgroundDrawable(ColorDrawable(Color.BLACK))
            }
            surface = EmulationSurface(context, dedicatedOutput = true).also {
                it.setBackgroundColor(Color.BLACK)
                it.onSurfaceAvailabilityChanged = { available ->
                    availabilityChanged(this, available)
                }
                setContentView(it)
            }
        }
    }

    private const val PREFS_NAME = "ARMSX2"
    private const val PREF_KEY = "display.dedicatedExternal"
    private const val SAMSUNG_DESKTOP_CATEGORY =
        "com.samsung.android.hardware.display.category.DESKTOP"
}

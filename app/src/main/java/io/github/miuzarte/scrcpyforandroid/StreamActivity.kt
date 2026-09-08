package io.github.miuzarte.scrcpyforandroid

import android.R.drawable
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.miuzarte.scrcpyforandroid.pages.StreamScreen
import io.github.miuzarte.scrcpyforandroid.services.AppScreenOn
import io.github.miuzarte.scrcpyforandroid.services.PictureInPictureActionReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

class StreamActivity: FragmentActivity() {
    // legacy port: androidx.core:core-pip (minSdk 24) replaced with platform APIs + guards.
    // Picture-in-picture exists only on API 26+; every entry point below is guarded.
    private val pipSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    private val pipActionReceiver = PictureInPictureActionReceiver()
    private var isPipActionReceiverRegistered = false

    // 是否处于 pip
    // 回到全屏时会因重建而变回初始值
    private val _pipModeState = MutableStateFlow(false)
    val pipModeState: StateFlow<Boolean> = _pipModeState

    val pipStopAction: RemoteAction by lazy {
        RemoteAction(
            Icon.createWithResource(this, drawable.ic_menu_close_clear_cancel),
            getString(R.string.password_stop_mirroring),
            getString(R.string.password_stop_mirroring),
            PictureInPictureActionReceiver.createPendingIntent(this),
        )
    }

    // 最近一次配置的 PiP 参数, API 26~30 上按 Home 时手动进入画中画用
    private var currentPipParams: PictureInPictureParams? = null

    // 每次 进出全屏/进出画中画
    // 都会重建 activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentActivityRef = WeakReference(this)
        AppScreenOn.register(window)

        registerPipActionReceiver()

        // 声明要画中画 (API 26+)
        if (pipSupported) {
            setPictureInPictureParams(PictureInPictureParams.Builder().build())
        }

        setContent {
            StreamScreen(activity = this)
        }
    }

    // 对应原 androidx.core.pip 的 basicPip.setEnabled(true):
    // API 26~30 在 onUserLeaveHint 中手动进入, API 31+ 由 setAutoEnterEnabled 自动进入
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
            val params = currentPipParams ?: return
            if (!isInPictureInPictureMode) {
                enterPictureInPictureMode(params)
            }
        }
    }

    /**
     * 平台 API 版的 PiP 参数配置入口, 兼容原 androidx PictureInPictureParamsCompat 的方法名。
     * API 26 以下为 no-op, lambda 不会执行 (其中引用了 API 24+ 的 RemoteAction)。
     */
    fun configurePip(block: PipParamsCompat.() -> Unit) {
        if (!pipSupported) return
        val builder = PictureInPictureParams.Builder()
        block(PipParamsCompat(builder))
        val params = builder.build()
        currentPipParams = params
        setPictureInPictureParams(params)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    class PipParamsCompat(private val builder: PictureInPictureParams.Builder) {
        fun setEnabled(enabled: Boolean) {
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
            }
        }

        fun setAspectRatio(aspectRatio: Rational) {
            builder.setAspectRatio(aspectRatio)
        }

        fun setSourceRectHint(sourceRectHint: Rect?) {
            if (sourceRectHint != null) {
                builder.setSourceRectHint(sourceRectHint)
            }
        }

        fun setSeamlessResizeEnabled(enabled: Boolean) {
            builder.setSeamlessResizeEnabled(enabled)
        }

        fun setCloseAction(action: RemoteAction) {
            // RemoteAction 版 close action 是 API 33+ 的能力, 低版本用系统自带关闭按钮
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.setCloseAction(action)
            }
        }
    }

    override fun onDestroy() {
        currentActivityRef?.get()
            ?.takeIf { it === this }
            ?.let { currentActivityRef = null }
        AppScreenOn.unregister(window)
        unregisterPipActionReceiver()
        super.onDestroy()
    }

    //- onPictureInPictureModeChanged
    //+ onPictureInPictureUiStateChanged
    //- onUserLeaveHint

    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)

        _pipModeState.value = true

        /*
        when {
            // 进入画中画
            pipState.isTransitioningToPip -> {}
            // 收进边缘
            pipState.isStashed -> {}
        }
         */
    }

    private fun registerPipActionReceiver() {
        if (isPipActionReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            PictureInPictureActionReceiver.createIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isPipActionReceiverRegistered = true
    }

    private fun unregisterPipActionReceiver() {
        if (!isPipActionReceiverRegistered) return
        unregisterReceiver(pipActionReceiver)
        isPipActionReceiverRegistered = false
    }

    companion object {
        private var currentActivityRef: WeakReference<StreamActivity>? = null

        fun createIntent(context: Context): Intent {
            return Intent(context, StreamActivity::class.java)
        }

        fun dismissActivePictureInPicture() {
            // legacy port: Activity.isInPictureInPictureMode 需要 API 24+,
            // 且 API 26 以下根本不可能处于画中画模式
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            currentActivityRef?.get()
                ?.takeIf { it.isInPictureInPictureMode }
                ?.finish()
        }
    }
}

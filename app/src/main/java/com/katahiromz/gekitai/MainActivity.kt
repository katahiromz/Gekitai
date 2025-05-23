// Gekitaiのメインアクティビティ。
// Copyright (c) 2025 Katayama Hirofumi MZ. All Rights Reserved.

package com.katahiromz.gekitai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibrationEffect.DEFAULT_AMPLITUDE
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber
import java.util.*

// 複数の翻訳版を有効にするために、任意の翻訳版のコンテキストを作成できるようにする。
// https://qiita.com/tarumzu/items/b076c4635b38366cddee
fun Context.createLocalizedContext(locale: Locale): Context {
    val res = resources
    val config = Configuration(res.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}

/////////////////////////////////////////////////////////////////////
// 定数。

// トーストの種類 (showToast用)
const val SHORT_TOAST = 0
const val LONG_TOAST = 1

// スナックの種類 (showSnackbar用)
const val SHORT_SNACK = 0
const val LONG_SNACK = 1
const val ACTION_SNACK_OK = 2
// TODO: Add more snack

class MainActivity : AppCompatActivity(), ValueCallback<String>, TextToSpeech.OnInitListener {
    /////////////////////////////////////////////////////////////////////
    // 共通

    // デバッグログにTimberを使用する。
    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    // Toast を表示する。
    fun showToast(text: String, typeOfToast: Int) {
        when (typeOfToast) {
            SHORT_TOAST -> {
                lastToast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
                lastToast?.show()
            }
            LONG_TOAST -> {
                lastToast = Toast.makeText(this, text, Toast.LENGTH_LONG)
                lastToast?.show()
            }
            else -> {
                require(false) { "typeOfToast: $typeOfToast" }
            }
        }
    }
    private var lastToast : Toast? = null
    // Toastをキャンセルする。
    fun cancelToast() {
        if (lastToast != null) {
            lastToast?.cancel()
            lastToast = null
        }
    }

    // Snackbar を表示する。
    fun showSnackbar(text: String, typeOfSnack: Int) {
        val view = findViewById<View>(android.R.id.content)
        when (typeOfSnack) {
            SHORT_SNACK -> {
                lastSnackbar = Snackbar.make(view, text, Snackbar.LENGTH_SHORT)
                lastSnackbar?.show()
            }
            LONG_SNACK -> {
                lastSnackbar = Snackbar.make(view, text, Snackbar.LENGTH_LONG)
                lastSnackbar?.show()
            }
            ACTION_SNACK_OK -> {
                lastSnackbar = Snackbar.make(view, text, Snackbar.LENGTH_INDEFINITE)
                val buttonText = getLocString(R.string.ok)
                lastSnackbar?.setAction(buttonText) {
                    // TODO: Add action
                }
                lastSnackbar?.show()
            }
            // TODO: Add more Snack
            else -> {
                require(false) { "typeOfSnack: $typeOfSnack" }
            }
        }
    }
    private var lastSnackbar : Snackbar? = null
    // Snackbarをキャンセルする。
    fun cancelSnackbar() {
        if (lastSnackbar != null) {
            lastSnackbar?.dismiss()
            lastSnackbar = null
        }
    }

    // 画面の明るさを調整する。
    private var screenBrightness: String = "normal"
    fun setBrightness(value: String) {
        Timber.i("setBrightness")
        runOnUiThread {
            val params: WindowManager.LayoutParams = window.attributes
            if (value == "brighter") {
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            } else {
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = params
        }

        screenBrightness = value
    }

    // 警報中かどうかをセットする。
    private var alerming: String = "no"
    fun setAlerming(value: String) {
        alerming = value
    }

    // 音声を最大化。
    private var oldVolume: Int = -1
    fun volumeMaximize() {
        Timber.i("volumeMaximize")
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager;
        if (oldVolume == -1)
            oldVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val musicMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, musicMaxVolume, 0)
    }
    // 音量を元に戻す。
    fun volumeRestore() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager;
        if (oldVolume != -1)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, oldVolume, 0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        Timber.i("onWindowFocusChanged")
        super.onWindowFocusChanged(hasFocus)
    }

    /////////////////////////////////////////////////////////////////////
    // パーミッション関連
    // 参考：https://qiita.com/sokume2106/items/46bd286569a6e7fac43d

    private val vibePermissionChecker =
        PermissionChecker(
            this,
            Manifest.permission.VIBRATE,
            onDenied = {
                showToast(getLocString(R.string.cant_use_vibe), LONG_TOAST)
                requestVibe()
            },
            onShowRationale = { onRequest ->
                val title = getLocString(R.string.app_name)
                val message = getLocString(R.string.needs_vibe)
                MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(getLocString(R.string.ok)) { _, _ -> onRequest() }
                    .setCancelable(false)
                    .show()
            }
        )

    private fun requestVibe() {
        val vibeCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE)
        if (vibeCheck != PackageManager.PERMISSION_GRANTED) {
            vibePermissionChecker.runWithPermission {}
        }
    }

    /////////////////////////////////////////////////////////////////////
    // イベントハンドラ関連

    // アクティビティの作成時。
    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("onCreate")

        // 最初の画面を表示する。
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // レイアウト ビューを指定する。
        setContentView(R.layout.activity_main)

        // アクションバーを隠す。
        supportActionBar?.hide()

        // ロケールをセットする。
        setCurLocale(Locale.getDefault())

        // 振動の権限を取得する。
        val vibeCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE)
        if (vibeCheck != PackageManager.PERMISSION_GRANTED) {
            requestVibe()
        }

        // WebViewを初期化。
        initWebView(savedInstanceState)

        // TextToSpeechを初期化。
        initTextToSpeech()

        // Timberを初期化。
        initTimber()
    }

    // アクティビティの開始時。
    override fun onStart() {
        Timber.i("onStart")
        super.onStart() // 親にも伝える。
    }

    private var speechVoiceVolume: Float = 1.0f

    // アクティビティの復帰時。
    override fun onResume() {
        Timber.i("onResume")
        super.onResume() // 親にも伝える。

        // ウェブビューを復帰。
        webView?.onResume()

        // テキストがあればスピーチを再開。
        if (theText != "") {
            speechText(theText, speechVoiceVolume.toString())
        }

        // 明るさを復帰。
        setBrightness(screenBrightness)

        // 音量を最大化。
        if (alerming == "yes")
            volumeMaximize();

        // 振動を再開。
        if (hasVibrator == 1)
            startVibrator(-1)

        // JavaScript側に再開を伝える。
        webView?.evaluateJavascript("GEKI_resume()") {}
    }

    // アクティビティの一時停止時。
    override fun onPause() {
        Timber.i("onPause")
        super.onPause() // 親にも伝える。

        // ウェブビューも一時停止。
        webView?.onPause()

        // スピーチを停止する。
        stopSpeech()

        // 振動していたら振動を停止。
        if (hasVibrator == 1 && oldVibratorLength > 0)
            stopVibrator()

        // 音量を元に戻す。
        volumeRestore()

        // JavaScript側に一時停止を伝える。
        webView?.evaluateJavascript("GEKI_pause()") {}
    }

    // アクティビティの停止時。
    override fun onStop() {
        Timber.i("onStop")
        super.onStop() // 親にも伝える。

        // ウェブビューを一時停止。
        webView?.onPause()

        // スピーチを停止する。
        stopSpeech()

        // 振動していたら振動を停止。
        if (hasVibrator == 1 && oldVibratorLength > 0)
            stopVibrator()

        // 音量を元に戻す。
        volumeRestore()
    }

    // アクティビティの破棄時。
    override fun onDestroy() {
        Timber.i("onDestroy")

        // ウェブビューを破棄。
        webView?.destroy()

        // TextToSpeechを破棄。
        tts?.shutdown()

        super.onDestroy() // 親にも伝える。
    }

    // 値を受け取るのに使う。ValueCallback<String>より継承。
    override fun onReceiveValue(value: String) {
        resultString = value
    }
    private var resultString = ""

    /////////////////////////////////////////////////////////////////////
    // WebView関連

    // ウェブビュー オブジェクト。
    private var webView: WebView? = null

    // クロームクライアント。
    private var chromeClient: MyWebChromeClient? = null

    // ウェブビューを初期化する。
    private fun initWebView(savedInstanceState: Bundle?) {
        // 以前の状態を復元する。
        // SEE ALSO: https://twigstechtips.blogspot.com/2013/08/android-retain-instance-of-webview.html
        if (webView != null && savedInstanceState != null) {
            webView?.restoreState(savedInstanceState)
            return
        }

        // ウェブビューのビューを取得する。
        webView = findViewById(R.id.web_view)

        // この処理は別スレッドかもしれないので、postを活用。
        webView?.post {
            initWebSettings()
        }
        webView?.post {
            initWebViewClient()
        }
        webView?.post {
            initChromeClient()
        }
    }

    // ウェブビュー クライアントを初期化する。
    private fun initWebViewClient() {
        webView?.webViewClient = MyWebViewClient(object : MyWebViewClient.Listener {
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?,
                                         error: WebResourceError?) {
                Timber.i("onReceivedError")
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?,
                                             errorResponse: WebResourceResponse?) {
                Timber.i("onReceivedHttpError")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Timber.i("onPageFinished")
                findViewById<TextView>(R.id.loading).visibility = View.GONE
            }
        })
    }

    // クロームクライアントを初期化する。
    private fun initChromeClient() {
        // まず、クロームクライアントを作成する。
        chromeClient = MyWebChromeClient(this, object : MyWebChromeClient.Listener {
            override fun onSpeech(text: String, volume: String) {
                Timber.i("onSpeech")
                theText = text // スピーチテキストをセットする。
                speechText(text, volume) // スピーチを開始する。
            }

            override fun onShowToast(text: String, typeOfToast: Int) {
                showToast(text, typeOfToast) // Toastを表示する。
            }

            override fun onShowSnackbar(text: String, typeOfSnack: Int) {
                showSnackbar(text, typeOfSnack) // Snackbarを表示する。
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val bar: ProgressBar = findViewById(R.id.progressBar)
                bar.progress = newProgress
                if (newProgress == 100)
                    bar.visibility = View.INVISIBLE // 進捗が完了したらプログレスを非表示にする。
            }

            override fun onBrightness(value: String) {
                setBrightness(value) // 明るさを指定する。
            }

            override fun onVolumeMaximize() {
                volumeMaximize(); // 音声を最大化。
            }

            override fun onAlerming(value: String) {
                setAlerming(value); // 警報中かどうかをセット。
            }

            override fun onStartVibrator(length: Int) {
                startVibrator(length)
            }

            override fun onStopVibrator() {
                stopVibrator()
            }
        })
        webView?.webChromeClient = chromeClient

        // JavaScript側からメソッドを呼び出せるインターフェイスを提供する。
        webView?.addJavascriptInterface(chromeClient!!, "android")

        // URLを指定してウェブページを読み込む。
        webView?.loadUrl(getLocString(R.string.url))
    }

    // ウェブ設定を初期化する。
    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebSettings() {
        // 背景色は黒。
        webView?.setBackgroundColor(0)

        // 設定を取得する。
        val settings = webView?.settings ?: return

        settings.javaScriptEnabled = true // JavaScriptを有効化。
        settings.domStorageEnabled = true // localStorageを有効化。
        settings.mediaPlaybackRequiresUserGesture = false // ジェスチャーなくてもメディア反応可。
        if (BuildConfig.DEBUG) {
            settings.cacheMode = WebSettings.LOAD_NO_CACHE // デバッグ中はキャッシュしない。
            WebView.setWebContentsDebuggingEnabled(true) // デバッギングを有効にする。
        }

        // JavaScript側からGekitaiのバージョン情報を取得できるようにする。
        val versionName = getVersionName()
        settings.userAgentString += "/gekitai-android-app/$versionName/"
    }

    // バージョン名を取得する。
    private fun getVersionName(): String {
        val appName: String = this.packageName
        val pm: PackageManager = this.packageManager
        val pi: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(appName, PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            pm.getPackageInfo(appName, PackageManager.GET_META_DATA)
        }
        return pi.versionName
    }

    /////////////////////////////////////////////////////////////////////
    // ロケール関連
    //
    private var currLocale: Locale = Locale.ENGLISH
    var currLocaleContext: Context? = null

    // 現在のロケールをセットする。
    fun setCurLocale(locale: Locale) {
        currLocale = locale
        currLocaleContext = null

        // TextToSpeechにもロケールをセットする。
        if (tts != null) {
            if (tts!!.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                tts!!.language = locale
            }
        }
    }

    // ローカライズされた文字列を取得する。複数ロケール対応のため、特殊な実装が必要。
    private fun getLocString(id: Int, locale: Locale): String {
        if (currLocaleContext == null) {
            currLocaleContext = applicationContext.createLocalizedContext(locale)
        }
        return currLocaleContext!!.getString(id)
    }
    fun getLocString(id: Int): String {
        return getLocString(id, currLocale)
    }

    /////////////////////////////////////////////////////////////////////
    // TextToSpeech関連
    //
    private var tts: TextToSpeech? = null // TextToSpeechオブジェクト。
    private var isSpeechReady = false // スピーチの準備が完了したか？
    private var theText = "" // スピーチテキスト。

    // TextToSpeechを初期化する。
    private fun initTextToSpeech() {
        run {
            tts = TextToSpeech(this, this)
            val locale = currLocale
            if (tts!!.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                tts!!.language = locale
            }
        }
    }

    // TextToSpeechのために用意された初期化完了ルーチン。
    // TextToSpeech.OnInitListener
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isSpeechReady = true
        }
    }

    // スピーチを開始する。
    fun speechText(text: String, volume: String) {
        Timber.i("speechText: $text, $volume")
        if (isSpeechReady) {
            val params = Bundle()
            speechVoiceVolume = volume.toFloat()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, speechVoiceVolume)
            tts?.setPitch(1.0f) // 音声の高さ。
            tts?.setSpeechRate(1.8f) // 音声の速さ。
            // {{LANGUAGE_SPECIFIC}}
            tts?.setLanguage(Locale.JAPANESE)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utteranceId")
        }
    }

    // スピーチを停止する。
    private fun stopSpeech() {
        if (isSpeechReady) {
            val params = Bundle()
            tts?.speak("", TextToSpeech.QUEUE_FLUSH, params, "utteranceId")
        }
    }

    /////////////////////////////////////////////////////////////////////
    // 振動関連

    private var hasVibrator: Int = -1
    private lateinit var vibratorManager: VibratorManager
    private lateinit var vibrator: Vibrator
    private var oldVibratorLength : Int = 0

    // 振動を開始する。
    @Suppress("DEPRECATION")
    fun startVibrator(length: Int) {
        Timber.i("startVibrator")
        // 振動が使えるかどうか判定する。
        if (hasVibrator == -1) {
            // 振動を初期化。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE)
                if (vm == null) {
                    Timber.i("startVibrator: vm == null")
                    hasVibrator = 0
                    return
                }
                vibratorManager = vm as VibratorManager
                vibrator = vibratorManager.defaultVibrator
            } else {
                val v = getSystemService(VIBRATOR_SERVICE)
                if (v == null) {
                    Timber.i("startVibrator: v == null")
                    hasVibrator = 0
                    return
                }
                vibrator = v as Vibrator
            }
            if (!vibrator.hasVibrator()) {
                hasVibrator = 0 // 振動が使えない場合。
                Timber.i("hasVibrator = 0")
                return
            }
            hasVibrator = 1 // 振動が使える場合。
            Timber.i("hasVibrator = 1")
        }

        if (hasVibrator == 0)
            return // 振動が使えない場合。

        // いったん、振動を止める。
        vibrator.cancel()

        // -1だったら古い値を使う。
        var len = length
        if (len == -1)
            len = oldVibratorLength

        val timeout: Long = len.toLong()
        if (vibrator.hasAmplitudeControl()) {
            val effect = VibrationEffect.createOneShot(timeout, 255)
            vibrator.vibrate(effect)
        } else {
            val effect = VibrationEffect.createOneShot(timeout, DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        }

        // 値を覚えておく。
        oldVibratorLength = len
    }

    // 振動を停止する。
    fun stopVibrator() {
        Timber.i("stopVibrator")
        if (hasVibrator != 1)
            return
        vibrator.cancel()
        oldVibratorLength = 0
    }
}
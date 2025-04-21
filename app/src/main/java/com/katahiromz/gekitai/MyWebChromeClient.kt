// Gekitaiのクロームクライアント。
// Copyright (c) 2025 Katayama Hirofumi MZ. All Rights Reserved.

package com.katahiromz.gekitai

import android.text.InputType
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import timber.log.Timber
import java.util.Locale

class MyWebChromeClient(private var activity: MainActivity?, private val listener: Listener) :
    WebChromeClient() {

    // リスナ。
    interface Listener {
        fun onSpeech(text: String, volume: String)
        fun onShowToast(text: String, typeOfToast: Int)
        fun onShowSnackbar(text: String, typeOfSnack: Int)
        fun onProgressChanged(view: WebView?, newProgress: Int)
        fun onBrightness(value: String)
        fun onVolumeMaximize()
        fun onAlerming(value: String);
        fun onStartVibrator(length: Int)
        fun onStopVibrator()
    }

    // ローカライズされた文字列を取得する。
    // 複数の翻訳版に対応するため、特別に処理を用意した。
    private fun getLocString(resId: Int): String {
        return activity!!.getLocString(resId)
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        listener.onProgressChanged(view, newProgress)
    }

    /////////////////////////////////////////////////////////////////////
    // パーミッション関連。
    override fun onPermissionRequest(request: PermissionRequest) {
        for (res in request.resources) {
            if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE || res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                request.grant(request.resources)
                return
            }
        }
        super.onPermissionRequest(request)
    }

    /////////////////////////////////////////////////////////////////////
    // JavaScript interface-related
    // これらの関数はJavaScriptからアクセスできる。

    // 画面の明るさを調整する。
    @JavascriptInterface
    fun setBrightness(brightness: String) {
        listener.onBrightness(brightness)
    }

    // 音声を最大化する。
    @JavascriptInterface
    fun volumeMaximize() {
        listener.onVolumeMaximize();
    }

    // 警報中かどうかをセットする。
    @JavascriptInterface
    fun setAlerming(value: String) {
        listener.onAlerming(value);
    }

    // スピーチをキャンセルする。
    @JavascriptInterface
    fun cancelSpeech() {
        listener.onSpeech("", "1.0")
    }

    // スピーチをループする。32回まで。
    @JavascriptInterface
    fun speechLoop(msg: String, volume: String) {
        listener.onSpeech(msg.repeat(32), volume)
    }

    // Gekitaiの設定をクリアする。
    @JavascriptInterface
    fun clearSettings() {
        MainRepository.clearMessageList(activity!!)
    }

    // 振動を開始する。
    @JavascriptInterface
    fun startVibrator(length: String) {
        listener.onStartVibrator(length.toInt())
    }

    // 振動を停止する。
    @JavascriptInterface
    fun stopVibrator() {
        listener.onStopVibrator()
    }

    // 現在の言語をセットする。
    @JavascriptInterface
    fun setLanguage(lang: String) {
        // {{LANGUAGE_SPECIFIC}}
        val locale : Locale
        when (lang) {
            "ja", "jp", "ja-JP" -> { // Japanese
                locale = Locale.JAPANESE
            }
            else -> { // English is default
                locale = Locale.ENGLISH
            }
        }
        Locale.setDefault(locale)
        activity!!.setCurLocale(locale)
    }

    private var modalDialog: AlertDialog? = null

    // JavaScriptのalert関数をフックする。
    override fun onJsAlert(
        view: WebView?,
        url: String?,
        message: String?,
        result: JsResult?
    ): Boolean {
        // MaterialAlertDialogを使用して普通に実装する。
        val title = getLocString(R.string.app_name)
        val okText = getLocString(R.string.ok)
        modalDialog = MaterialAlertDialogBuilder(activity!!, R.style.AlertDialogTheme)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(okText) { _, _ ->
                result?.confirm()
                modalDialog = null
            }
            .setCancelable(false)
            .create()
        modalDialog?.show()
        return true
    }

    // JavaScriptのconfirm関数をフックする。
    override fun onJsConfirm(
        view: WebView?,
        url: String?,
        message: String?,
        result: JsResult?
    ): Boolean {
        // MaterialAlertDialogを使用して普通に実装する。
        val title = getLocString(R.string.app_name)
        val okText = getLocString(R.string.ok)
        val cancelText = getLocString(R.string.cancel)
        modalDialog = MaterialAlertDialogBuilder(activity!!, R.style.AlertDialogTheme)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(okText) { _, _ ->
                result?.confirm()
                modalDialog = null
            }
            .setNegativeButton(cancelText) { _, _ ->
                result?.cancel()
                modalDialog = null
            }
            .setCancelable(false)
            .create()
        modalDialog?.show()
        return true
    }

    // JavaScriptのprompt関数をフックする。
    override fun onJsPrompt(
        view: WebView?,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult?
    ): Boolean {
        activity!!.currLocaleContext = null
        val title = getLocString(R.string.app_name)

        // MaterialAlertDialogを使用して普通に実装する。
        val okText = getLocString(R.string.ok)
        val cancelText = getLocString(R.string.cancel)
        val input = EditText(activity!!)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(if (defaultValue != null) defaultValue else "")
        modalDialog = MaterialAlertDialogBuilder(activity!!, R.style.AlertDialogTheme)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton(okText) { _, _ ->
                result?.confirm(input.text.toString())
                modalDialog = null
            }
            .setNegativeButton(cancelText) { _, _ ->
                result?.cancel()
                modalDialog = null
            }
            .setCancelable(false)
            .create()
        modalDialog?.show()
        return true
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        if (BuildConfig.DEBUG) {
            if (consoleMessage != null) {
                val msg = consoleMessage.message()
                val line = consoleMessage.lineNumber()
                val src = consoleMessage.sourceId()
                Timber.d("console: $msg at Line $line of $src")
            }
        }
        return super.onConsoleMessage(consoleMessage)
    }
}

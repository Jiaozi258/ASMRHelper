package com.asmrhelper.data.music.netease

import com.asmrhelper.domain.model.music.QrLoginState

/** 网易云扫码登录 `check` 返回的 code → `QrLoginState` 纯映射。 */
object NeteaseQr {

    fun stateFor(code: Int, key: String = "", username: String = ""): QrLoginState = when (code) {
        800 -> QrLoginState.Expired(key)
        801 -> QrLoginState.Waiting(key, null)
        802 -> QrLoginState.Scanned(key)
        803 -> QrLoginState.Confirmed(username)
        else -> QrLoginState.Failed("未知登录状态 code=$code")
    }
}

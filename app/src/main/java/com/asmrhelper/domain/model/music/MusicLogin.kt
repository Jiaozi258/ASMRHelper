package com.asmrhelper.domain.model.music

/** 某在线平台的登录态（cookie + 昵称），持久化到 SharedPreferences。 */
data class MusicLogin(
    val cookie: String = "",
    val nickname: String = ""
)

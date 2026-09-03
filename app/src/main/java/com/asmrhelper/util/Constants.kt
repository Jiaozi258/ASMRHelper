package com.asmrhelper.util

object Constants {
    const val NOTIFICATION_CHANNEL_ID = "asmr_playback"
    const val NOTIFICATION_CHANNEL_NAME = "ASMR 播放"
    const val NOTIFICATION_ID = 1001

    const val PLAYER_MAIN = "main_player"
    const val PLAYER_BACKGROUND = "background_player"

    /** 通知栏"关闭"按钮 / 应用内"彻底停止"触发的动作：停止播放并关闭前台服务。 */
    const val ACTION_STOP_SERVICE = "com.asmrhelper.action.STOP_SERVICE"
}

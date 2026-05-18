package com.asmrhelper

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter

object CrashHandler {

    private const val CRASH_FILE = "asmr_last_crash.txt"

    fun setup(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        val crashFile = java.io.File(context.filesDir, CRASH_FILE)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 把崩溃信息写入文件
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                crashFile.writeText(sw.toString())
            } catch (_: Exception) {}

            // 交给系统默认处理
            defaultHandler?.uncaughtException(thread, throwable)
            Process.killProcess(Process.myPid())
        }
    }

    fun showLastCrash(activity: Activity) {
        val crashFile = java.io.File(activity.filesDir, CRASH_FILE)
        if (crashFile.exists()) {
            val crashText = crashFile.readText()
            crashFile.delete()
            AlertDialog.Builder(activity)
                .setTitle("上次崩溃日志")
                .setMessage(crashText.take(2000))
                .setPositiveButton("关闭", null)
                .show()
        }
    }
}

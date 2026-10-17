package com.asmrhelper.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用内语言 override：把持久化的 `app_language`（0=跟随系统、1=中文、2=英文）
 * 映射为 Locale，并用 `createConfigurationContext` 让资源按目标 locale 解析。
 */
object AppLocale {

    /** 语言 int → Locale；0（跟随系统）或未知值返回 null。 */
    fun localeFor(language: Int): Locale? = when (language) {
        1 -> Locale("zh")
        2 -> Locale("en")
        else -> null
    }

    /** 按语言包装 context；跟随系统时原样返回。 */
    fun wrap(context: Context, language: Int): Context {
        val locale = localeFor(language) ?: return context
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

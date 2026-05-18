package com.asmrhelper.util

/** 隐私模式：除首尾字符外替换为 * */
fun String.maskPrivacy(): String {
    if (length <= 2) return this
    return first() + "*".repeat(length - 2) + last()
}

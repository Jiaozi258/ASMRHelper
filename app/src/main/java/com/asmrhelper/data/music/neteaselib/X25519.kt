package com.asmrhelper.data.music.neteaselib

import java.math.BigInteger
import kotlin.random.Random

/**
 * RFC 7748 X25519 点乘（纯 Kotlin / BigInteger 实现）。
 *
 * minSdk 26 无 BouncyCastle，Android 内置 XDH 需 API 33+，故按 RFC 7748
 * 蒙哥马利阶梯手写。仅用于网易云 xeapi 的 X25519 ECDH 密钥协商（每次请求
 * 生成一次性临时密钥对，规模小，BigInteger 性能足够）。
 */
object X25519 {

    private val P = BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
    private val A24 = BigInteger("121665")
    private val BASE_POINT = BigInteger("9") // 基点 u 坐标

    /** 规范标量：清低位 3 位、高位 1 位，并置第 254 位（RFC 7748 decodeScalar25519）。 */
    fun clamp(scalar: ByteArray): ByteArray {
        val k = scalar.copyOf()
        k[0] = (k[0].toInt() and 0xf8).toByte()
        k[31] = (k[31].toInt() and 0x7f).toByte()
        k[31] = (k[31].toInt() or 0x40).toByte()
        return k
    }

    /** 生成一次性密钥对：私钥为 32 字节原始随机数（未 clamp），公钥为基点标量乘。 */
    fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val priv = Random.nextBytes(32)
        val pub = scalarMultBase(priv)
        return priv to pub
    }

    /** 私钥 × 基点(9)，返回 32 字节小端公钥。 */
    fun scalarMultBase(scalar: ByteArray): ByteArray = scalarMult(scalar, BASE_POINT)

    /** 私钥 × 对端 u 坐标（32 字节原始公钥），返回 32 字节小端共享密钥。 */
    fun scalarMult(scalar: ByteArray, uCoordinate: ByteArray): ByteArray =
        scalarMult(scalar, BigInteger(1, uCoordinate.reversedArray()))

    /** 蒙哥马利阶梯（RFC 7748 §5）。 */
    fun scalarMult(scalar: ByteArray, u: BigInteger): ByteArray {
        val k = clamp(scalar)
        val x1 = u.mod(P)
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = 0

        for (t in 254 downTo 0) {
            val kt = (k[t / 8].toInt() ushr (t % 8)) and 1
            swap = swap xor kt
            if (swap == 1) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = kt

            val a = (x2 + z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = (x2 - z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = (aa - bb).mod(P)
            val c = (x3 + z3).mod(P)
            val d = (x3 - z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)
            x3 = (da + cb).mod(P).let { it.multiply(it).mod(P) }
            z3 = x1.multiply((da - cb).mod(P).let { it.multiply(it).mod(P) }).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply((aa + A24.multiply(e)).mod(P)).mod(P)
        }

        if (swap == 1) {
            val tx = x2; x2 = x3; x3 = tx
            val tz = z2; z2 = z3; z3 = tz
        }

        val result = x2.multiply(z2.modPow(P.subtract(BigInteger.TWO), P)).mod(P)
        return toLittleEndian32(result)
    }

    /** 无符号大整数 → 32 字节小端（剥掉 toByteArray 的符号前导 0x00 后反转）。 */
    private fun toLittleEndian32(value: BigInteger): ByteArray {
        val bigEndian = value.toByteArray()
        var offset = 0
        while (offset < bigEndian.size - 1 && bigEndian[offset] == 0.toByte()) offset++
        val magnitude = bigEndian.copyOfRange(offset, bigEndian.size)
        val out = ByteArray(32)
        for (i in magnitude.indices) out[i] = magnitude[magnitude.size - 1 - i]
        return out
    }
}

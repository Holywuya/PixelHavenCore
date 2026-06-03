package com.pixlehavencore.util



/**
 * FastNoiseLite 噪声生成器
 * 移植自 RealLife 插件，简化公共 API，保留完整 OpenSimplex2 实现
 */
class FastNoiseLite(private var seed: Int = 1337) {

    private var frequency: Float = 0.01f

    fun setSeed(seed: Int) {
        this.seed = seed
    }

    fun setFrequency(frequency: Float) {
        this.frequency = frequency
    }

    /**
     * 获取 2D 噪声值
     * @return 噪声值范围 [-1, 1]
     */
    fun getNoise(x: Float, y: Float): Float {
        return singleOpenSimplex2(seed, x * frequency, y * frequency)
    }

    /**
     * 获取 3D 噪声值
     * @return 噪声值范围 [-1, 1]
     */
    fun getNoise(x: Float, y: Float, z: Float): Float {
        return singleOpenSimplex2(seed, x * frequency, y * frequency, z * frequency)
    }

    private fun singleOpenSimplex2(seed: Int, x: Float, y: Float): Float {
        val f1 = 1.7320508f
        val f2 = 0.21132487f

        var xi = fastFloor(x)
        var yi = fastFloor(y)
        val xf0 = x - xi
        val yf0 = y - yi

        val t = (xf0 + yf0) * 0.21132487f
        val xf1 = xf0 - t
        val yf1 = yf0 - t

        xi *= PRIME_X
        yi *= PRIME_Y

        var f11 = 0.5f - xf1 * xf1 - yf1 * yf1
        val n0: Float
        if (f11 <= 0.0f) {
            n0 = 0.0f
        } else {
            n0 = f11 * f11 * f11 * f11 * gradCoord(seed, xi, yi, xf1, yf1)
        }

        val f12 = 3.1547005f * t + -0.6666666f + f11
        val n2: Float
        if (f12 <= 0.0f) {
            n2 = 0.0f
        } else {
            val xf3 = xf1 + -0.57735026f
            val yf3 = yf1 + -0.57735026f
            n2 = f12 * f12 * f12 * f12 * gradCoord(seed, xi + PRIME_X, yi + PRIME_Y, xf3, yf3)
        }

        val n1: Float
        if (yf1 > xf1) {
            val xf3 = xf1 + 0.21132487f
            val yf3 = yf1 + -0.7886751f
            val f15 = 0.5f - xf3 * xf3 - yf3 * yf3
            n1 = if (f15 <= 0.0f) {
                0.0f
            } else {
                f15 * f15 * f15 * f15 * gradCoord(seed, xi, yi + PRIME_Y, xf3, yf3)
            }
        } else {
            val xf3 = xf1 + -0.7886751f
            val yf3 = yf1 + 0.21132487f
            val f15 = 0.5f - xf3 * xf3 - yf3 * yf3
            n1 = if (f15 <= 0.0f) {
                0.0f
            } else {
                f15 * f15 * f15 * f15 * gradCoord(seed, xi + PRIME_X, yi, xf3, yf3)
            }
        }

        return (n0 + n1 + n2) * 99.83685f
    }

    private fun singleOpenSimplex2(seed: Int, x: Float, y: Float, z: Float): Float {
        var currentSeed = seed

        var i = fastRound(x)
        var j = fastRound(y)
        var k = fastRound(z)
        var xf0 = x - i
        var yf0 = y - j
        var zf0 = z - k

        var xri = ((-1.0f - xf0).toInt()) or 1
        var yri = ((-1.0f - yf0).toInt()) or 1
        var zri = ((-1.0f - zf0).toInt()) or 1

        var xd0 = xri * -xf0
        var yd0 = yri * -yf0
        var zd0 = zri * -zf0

        i *= PRIME_X
        j *= PRIME_Y
        k *= PRIME_Z

        var value = 0.0f
        var attn0 = 0.6f - xf0 * xf0 - yf0 * yf0 - zf0 * zf0

        for (c in 0..1) {
            if (attn0 > 0.0f) {
                value += attn0 * attn0 * attn0 * attn0 * gradCoord(currentSeed, i, j, k, xf0, yf0, zf0)
            }

            if (xd0 >= yd0 && xd0 >= zd0) {
                val attn = attn0 + xd0 + xd0
                if (attn > 1.0f) {
                    val attn1 = attn - 1
                    value += attn1 * attn1 * attn1 * attn1 * gradCoord(currentSeed, i - xri * PRIME_X, j, k, xf0 + xri, yf0, zf0)
                }
            } else if (yd0 > xd0 && yd0 >= zd0) {
                val attn = attn0 + yd0 + yd0
                if (attn > 1.0f) {
                    val attn1 = attn - 1
                    value += attn1 * attn1 * attn1 * attn1 * gradCoord(currentSeed, i, j - yri * PRIME_Y, k, xf0, yf0 + yri, zf0)
                }
            } else {
                val attn = attn0 + zd0 + zd0
                if (attn > 1.0f) {
                    val attn1 = attn - 1
                    value += attn1 * attn1 * attn1 * attn1 * gradCoord(currentSeed, i, j, k - zri * PRIME_Z, xf0, yf0, zf0 + zri)
                }
            }

            if (c == 1) break

            xd0 = 0.5f - xd0
            yd0 = 0.5f - yd0
            zd0 = 0.5f - zd0

            xf0 = xri * xd0
            yf0 = yri * yd0
            zf0 = zri * zd0

            attn0 += 0.75f - xd0 - yd0 + zd0

            i += (xri shr 1) and 0x1DDE90C9
            j += (yri shr 1) and 0x43C42E4D
            k += (zri shr 1) and 0x668B6E2F

            xri = -xri
            yri = -yri
            zri = -zri

            currentSeed = currentSeed xor -1
        }

        return value * 32.694283f
    }

    private fun gradCoord(seed: Int, xPrimed: Int, yPrimed: Int, xd: Float, yd: Float): Float {
        var hash = seed xor xPrimed xor yPrimed
        hash *= 668265261
        hash = hash xor (hash shr 15)
        hash = hash and 0xFE
        return xd * GRAD_2D[hash] + yd * GRAD_2D[hash or 1]
    }

    private fun gradCoord(seed: Int, xPrimed: Int, yPrimed: Int, zPrimed: Int, xd: Float, yd: Float, zd: Float): Float {
        var hash = seed xor xPrimed xor yPrimed xor zPrimed
        hash *= 0x27D4EB2D
        hash = hash xor (hash shr 15)
        val index = (hash and (63 shl 2)) shr 1
        return xd * GRAD_3D[index] + yd * GRAD_3D[index + 1] + zd * GRAD_3D[index + 2]
    }

    companion object {
        private const val PRIME_X = 501125321
        private const val PRIME_Y = 1136930381
        private const val PRIME_Z = 1720413743

        private val GRAD_2D = floatArrayOf(
            0.13052619f, 0.9914449f, 0.38268343f, 0.9238795f,
            0.6087614f, 0.7933533f, 0.7933533f, 0.6087614f,
            0.9238795f, 0.38268343f, 0.9914449f, 0.13052619f,
            0.9914449f, -0.13052619f, 0.9238795f, -0.38268343f,
            0.7933533f, -0.6087614f, 0.6087614f, -0.7933533f,
            0.38268343f, -0.9238795f, 0.13052619f, -0.9914449f,
            -0.13052619f, -0.9914449f, -0.38268343f, -0.9238795f,
            -0.6087614f, -0.7933533f, -0.7933533f, -0.6087614f,
            -0.9238795f, -0.38268343f, -0.9914449f, -0.13052619f,
            -0.9914449f, 0.13052619f, -0.9238795f, 0.38268343f,
            -0.7933533f, 0.6087614f, -0.6087614f, 0.7933533f,
            -0.38268343f, 0.9238795f, -0.13052619f, 0.9914449f,
            0.13052619f, 0.9914449f, 0.38268343f, 0.9238795f,
            0.6087614f, 0.7933533f, 0.7933533f, 0.6087614f,
            0.9238795f, 0.38268343f, 0.9914449f, 0.13052619f,
            0.9914449f, -0.13052619f, 0.9238795f, -0.38268343f,
            0.7933533f, -0.6087614f, 0.6087614f, -0.7933533f,
            0.38268343f, -0.9238795f, 0.13052619f, -0.9914449f,
            -0.13052619f, -0.9914449f, -0.38268343f, -0.9238795f,
            -0.6087614f, -0.7933533f, -0.7933533f, -0.6087614f,
            -0.9238795f, -0.38268343f, -0.9914449f, -0.13052619f,
            -0.9914449f, 0.13052619f, -0.9238795f, 0.38268343f,
            -0.7933533f, 0.6087614f, -0.6087614f, 0.7933533f,
            -0.38268343f, 0.9238795f, -0.13052619f, 0.9914449f,
            0.13052619f, 0.9914449f, 0.38268343f, 0.9238795f,
            0.6087614f, 0.7933533f, 0.7933533f, 0.6087614f,
            0.9238795f, 0.38268343f, 0.9914449f, 0.13052619f,
            0.9914449f, -0.13052619f, 0.9238795f, -0.38268343f,
            0.7933533f, -0.6087614f, 0.6087614f, -0.7933533f,
            0.38268343f, -0.9238795f, 0.13052619f, -0.9914449f,
            -0.13052619f, -0.9914449f, -0.38268343f, -0.9238795f,
            -0.6087614f, -0.7933533f, -0.7933533f, -0.6087614f,
            -0.9238795f, -0.38268343f, -0.9914449f, -0.13052619f,
            -0.9914449f, 0.13052619f, -0.9238795f, 0.38268343f,
            -0.7933533f, 0.6087614f, -0.6087614f, 0.7933533f,
            -0.38268343f, 0.9238795f, -0.13052619f, 0.9914449f,
            0.13052619f, 0.9914449f, 0.38268343f, 0.9238795f,
            0.6087614f, 0.7933533f, 0.7933533f, 0.6087614f,
            0.9238795f, 0.38268343f, 0.9914449f, 0.13052619f,
            0.9914449f, -0.13052619f, 0.9238795f, -0.38268343f,
            0.7933533f, -0.6087614f, 0.6087614f, -0.7933533f,
            0.38268343f, -0.9238795f, 0.13052619f, -0.9914449f,
            -0.13052619f, -0.9914449f, -0.38268343f, -0.9238795f,
            -0.6087614f, -0.7933533f, -0.7933533f, -0.6087614f,
            -0.9238795f, -0.38268343f, -0.9914449f, -0.13052619f,
            -0.9914449f, 0.13052619f, -0.9238795f, 0.38268343f,
            -0.7933533f, 0.6087614f, -0.6087614f, 0.7933533f,
            -0.38268343f, 0.9238795f, -0.13052619f, 0.9914449f,
            0.13052619f, 0.9914449f, 0.38268343f, 0.9238795f,
            0.6087614f, 0.7933533f, 0.7933533f, 0.6087614f,
            0.9238795f, 0.38268343f, 0.9914449f, 0.13052619f,
            0.9914449f, -0.13052619f, 0.9238795f, -0.38268343f,
            0.7933533f, -0.6087614f, 0.6087614f, -0.7933533f,
            0.38268343f, -0.9238795f, 0.13052619f, -0.9914449f,
            -0.13052619f, -0.9914449f, -0.38268343f, -0.9238795f,
            -0.6087614f, -0.7933533f, -0.7933533f, -0.6087614f,
            -0.9238795f, -0.38268343f, -0.9914449f, -0.13052619f,
            -0.9914449f, 0.13052619f, -0.9238795f, 0.38268343f,
            -0.7933533f, 0.6087614f, -0.6087614f, 0.7933533f,
            -0.38268343f, 0.9238795f, -0.13052619f, 0.9914449f,
            0.38268343f, 0.9238795f, 0.9238795f, 0.38268343f,
            0.9238795f, -0.38268343f, 0.38268343f, -0.9238795f,
            -0.38268343f, -0.9238795f, -0.9238795f, -0.38268343f,
            -0.9238795f, 0.38268343f, -0.38268343f, 0.9238795f
        )

        private val GRAD_3D = floatArrayOf(
            0.0f, 1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f,
            0.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f,
            1.0f, 0.0f, 1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f,
            0.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f,
            1.0f, 0.0f, 1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f,
            0.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f,
            1.0f, 0.0f, 1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f,
            0.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f,
            1.0f, 0.0f, 1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f,
            0.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f,
            1.0f, 0.0f, 1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f,
            0.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f,
            1.0f, 0.0f, 1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f,
            0.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f,
            1.0f, 0.0f, 1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f,
            0.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f,
            1.0f, 0.0f, 1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f,
            -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f
        )

        private fun fastFloor(x: Float): Int {
            val xi = x.toInt()
            return if (x < xi) xi - 1 else xi
        }

        private fun fastRound(x: Float): Int {
            return if (x >= 0.0f) (x + 0.5f).toInt() else (x - 0.5f).toInt()
        }
    }
}

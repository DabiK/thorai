package app.regie.game.engine.village

/**
 * Hachage stable d'une machine et d'une version à l'autre (FNV-1a 64 bits, puis le mélange de
 * SplitMix64) : seule source de « hasard » du hameau, toujours tirée d'une graine.
 */
internal object StableHash {
    fun of(vararg parts: Any): Long {
        var hash = OFFSET
        parts.joinToString(SEPARATOR).encodeToByteArray().forEach { byte ->
            hash = (hash xor (byte.toLong() and 0xFF)) * PRIME
        }
        return mix(hash)
    }

    /** Un entier dans `0 until bound`. */
    fun pick(bound: Int, vararg parts: Any): Int {
        require(bound > 0)
        return Math.floorMod(of(*parts), bound.toLong()).toInt()
    }

    private fun mix(value: Long): Long {
        var z = value
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private const val OFFSET = -0x340d631b7bdddcdbL
    private const val PRIME = 0x100000001b3L
    private const val SEPARATOR = "\u001F"
}

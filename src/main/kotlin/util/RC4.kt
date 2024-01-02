package util

class RC4 {
    private val box = IntArray(256)

    private fun ksa(key: ByteArray) {
        val len = key.size
        for (i in 0..255) {
            box[i] = i
        }
        var i = 0
        var j = 0
        while (i < 256) {
            j = (j + box[i] + key[i % len]) and 0xff
            val swap = box[i]
            box[i] = box[j]
            box[j] = swap
            i++
        }
    }

    private fun prga(data: ByteArray): ByteArray {
        val dataCopy = data.copyOf()
        var k = 0
        var i: Int
        var j: Int
        while (k < dataCopy.size) {
            i = (k + 1) and 0xff
            j = (box[i] + i) and 0xff
            dataCopy[k] = (dataCopy[k].toInt() xor box[box[i] + box[j] and 0xff]).toByte()
            k++
        }
        return dataCopy
    }

    fun doApply(key: ByteArray, data: ByteArray): ByteArray {
        ksa(key)
        return prga(data)
    }
}

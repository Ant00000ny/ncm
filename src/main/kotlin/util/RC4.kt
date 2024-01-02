package util

class RC4 {
    private val box = IntArray(256)

    fun KSA(key: ByteArray) {
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

    fun PRGA(data: ByteArray) {
        var k = 0
        var i: Int
        var j: Int
        while (k < data.size) {
            i = (k + 1) and 0xff
            j = (box[i] + i) and 0xff
            data[k] = (data[k].toInt() xor box[box[i] + box[j] and 0xff]).toByte()
            k++
        }
    }
}

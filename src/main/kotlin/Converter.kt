import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import entity.MetaInfo
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import util.Constants
import util.RC4
import java.io.File
import java.math.BigInteger
import java.nio.file.Path
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.imageio.ImageIO
import kotlin.experimental.xor

fun convertNcm(ncmFile: File, outputDir: Path = ncmFile.parentFile.toPath()): File {
    ncmFile.inputStream().use { fis ->
        val magicHeader = ByteArray(10)
            .also { fis.read(it) }

        val keyLength = ByteArray(4)
            .also { fis.read(it) }
            .let { getLength(it) }

        val musicDataRc4Key = ByteArray(keyLength)
            .also { fis.read(it) }
            .map { b -> b xor 0x64 }
            .toByteArray()
            .let { aesDecrypt(it, Constants.RC4_KEY) }
            .let { String(it) }
            // neteasecloudmusic105733196515115683019687409E7fT49x7dof9OKCgg9cdvhEuezy3iZCL1nFvBFd1T4uSktAJKmwZXsijPbijliionVUXXg9plTbXEclAE9Lb
            .let { Regex("""neteasecloudmusic(?<key>.+)""").find(it)!!.groups["key"]!!.value }
            .toByteArray()

        val metaLength = ByteArray(4)
            .also { fis.read(it) }
            .let { getLength(it) }

        val metaInfo = ByteArray(metaLength)
            .also { fis.read(it) }
            .map { b -> b xor 0x63 }
            .toByteArray()
            .let { String(it) }
            // 163 key(Don't modify):L64FU3W4YxX3ZFTmbZ+8/WPa9h447Lk+0ZkYlIN/z18mfGC2BIsRn8F/nsLf6eY5A+FLyyQUZ6qSivtNM1ngMyVWMqgLvGGQjJXdVIJtvWSBr51TXTgBobeSiC7HIHJbAsEuPHzai5D691LSkZCa8F6TESXdxaeNbsr++qJCn2yybciCt+7xuNg1ZLfI3Oqar1cii9alX1zr54wHypJ2BXt66ycbiLHksnOlACsJaB3aIJLt/ZOLKeUhlvYGxTtNg/fIp4fn783dic35xpTNYVGDqBu9VZ7ZPKo2ofJ0cLb7vUStxPqtMW2EGaODC4szqtESUD5RKon85Utj4/VRBqDjOj2XkmnI+H8OuH+XkHCaOaZl5s8xDX7I2LioxKYu5pbAQ7ucdSBOHQQIZRzW3EAqN6aEsiF11D1wTCH+idUdDN4HfUZGXjBy+BeVaPEOJDAq7xr6HKSm+jyF4C5LZCgGIIXBfWYpED/vMyygnrSzlKuTiwN+A6cHnzFQJ5yPvRR671801ZbGFzfHlKYVSwfAtppH4kyz0zBiDapIQz/HWIPUbmhW/QWFFFDZix9BetHrsS6g1sQKMAr5Yw6XmEUxLtW3EkxEA+XnyAWcX5uTJF7M5+1FRz3Ze4DyQ0P6
            .let { Regex("""163 key\(Don't modify\):(?<key>.+)""").find(it)!!.groups["key"]!!.value }
            .let { Base64.getDecoder().decode(it) }
            .let { aesDecrypt(it, Constants.META_KEY) }
            // music:{"musicId":1399788799,"musicName":"脳裏上のクラッカー","artist":[["ずっと真夜中でいいのに。",15021101]],"albumId":82815921,"album":"潜潜話","albumPicDocId":"109951165005758203","albumPic":"https://p4.music.126.net/jWFFWk-XoYFTVh09nWjBGg==/109951165005758203.jpg","bitrate":1811812,"mp3DocId":"2117b4ebd550a245c514a626ea81a4a8","duration":270000,"mvId":0,"alias":["脑海中的烟花"],"transNames":["脑海中的礼花"],"format":"flac"}
            .let { String(it) }
            .let { Regex("""music:(?<json>.+)""").find(it)!!.groups["json"]!!.value }
            .let { jacksonObjectMapper().readValue<MetaInfo>(it) }


        val crc = ByteArray(4)
            .also { fis.read(it) }

        val gap = ByteArray(5)
            .also { fis.read(it) }

        val imageSize = ByteArray(4)
            .also { fis.read(it) }
            .let { getLength(it) }

        val albumCover = ByteArray(imageSize)
            .also { fis.read(it) }

        val outputFile = outputDir.resolve("${ncmFile.nameWithoutExtension}.${metaInfo.format}")
            .toFile()
            .also { it.parentFile.mkdirs() }
            .also { it.createNewFile() }

        outputFile.outputStream().use { fos ->
            RC4().doApply(musicDataRc4Key, fis.readBytes())
                .let { fos.write(it) }
        }

        addMetaInfo(metaInfo, outputFile, albumCover)

        return outputFile
    }
}

private fun addMetaInfo(metaInfo: MetaInfo, outputFile: File, albumCover: ByteArray) {
    val audioFile = AudioFileIO.read(outputFile)
    val tag = audioFile.tag
    tag.setField(FieldKey.ALBUM, metaInfo.album)
    tag.setField(FieldKey.TITLE, metaInfo.musicName)
    tag.setField(FieldKey.ARTIST, metaInfo.artist[0][0])
    val albumCoverBufferedImage = albumCover.inputStream().use {
        ImageIO.read(it)
    }
    if (albumCoverBufferedImage != null) {
        val coverArt = MetadataBlockDataPicture(
            albumCover,
            0,
            resolveAlbumImageMimeType(albumCover),
            "",
            albumCoverBufferedImage.width,
            albumCoverBufferedImage.height,
            if (albumCoverBufferedImage.colorModel.hasAlpha()) 32 else 24,
            0
        )
        val artwork = ArtworkFactory.createArtworkFromMetadataBlockDataPicture(coverArt)
        tag.setField(tag.createField(artwork))
    }
    AudioFileIO.write(audioFile)
}

private fun aesDecrypt(it: ByteArray, key: ByteArray): ByteArray {
    return Cipher.getInstance("AES/ECB/PKCS5Padding")
        .apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES")) }
        .doFinal(it)
}

private fun getLength(bytes: ByteArray): Int {
    return Integer.reverseBytes(BigInteger(bytes).intValueExact())
}

private fun resolveAlbumImageMimeType(albumImage: ByteArray): String {
    val mPNG: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    if (albumImage.size > 8) {
        for (i in 0..7) {
            if (albumImage[i] != mPNG[i]) {
                return "image/jpg"
            }
        }
    }
    return "image/png"
}

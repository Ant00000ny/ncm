package entity

data class MetaInfo(
    var musicId: Int?,
    var musicName: String?,
    var artist: List<List<String>>?,
    var albumId: Int?,
    var album: String?,
    var albumPicDocId: String?,
    var albumPic: String?,
    var bitrate: Int?,
    var mp3DocId: String?,
    var duration: Int?,
    var mvId: Int?,
    var alias: ArrayList<String>?,
    var transNames: ArrayList<String>?,
    var format: String?,
)

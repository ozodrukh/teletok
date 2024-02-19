package com.ozodrukh.teletok.extractor

import com.google.gson.annotations.SerializedName
import java.io.File

data class ExtractedMetaVideo(
    val videoFile: File,
    val caption: String,
    val metadata: PostMetadata,
    val sourceService: String,
    val sourceUrl: String,
)

data class PostMetadata(
    val width: Int,
    val height: Int,
    val duration: Int,
)

data class VideoPostMetadata(
    val creator: String?,
    val uploader: String,

    val description: String,

    @SerializedName("like_count")
    val likesCount: Long = 0,
    @SerializedName("comment_count")
    val commentsCount: Long = 0,
    @SerializedName("view_count")
    val viewsCount: Long = 0,

    val artist: String?,
    val track: String?,
)
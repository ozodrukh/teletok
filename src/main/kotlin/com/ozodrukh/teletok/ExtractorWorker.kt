package com.ozodrukh.teletok

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentLinkedQueue

class ExtractorWorker {
    private val queue = ConcurrentLinkedQueue<HttpUrl>()
    private val scope = CoroutineScope(Dispatchers.IO + CoroutineName("extractor-worker"))

    fun download(url: HttpUrl): MutableStateFlow<ExtractorState> {
        val flow = MutableStateFlow<ExtractorState>(ExtractorState.Extracting)
        scope.launch {
            val extractor = VideoExtractor(url)
            val info = extractor.extract()

            if (info == null) {
//               flow.
            }
        }
        return flow
    }

    sealed class ExtractorState {
        data object Extracting : ExtractorState()
        class MetadataExtracted(val videoInfo: ExtractedInfo) : ExtractorState()
        data object VideoDownloaded : ExtractorState()
    }
}
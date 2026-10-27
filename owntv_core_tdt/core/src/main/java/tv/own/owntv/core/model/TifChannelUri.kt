package tv.own.owntv.core.model

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** References a channel owned by Android's TV Provider; never a network stream. */
data class TifChannelUri(val inputId: String, val channelId: Long, val videoHeight: Int? = null) {
    fun encode(): String = "owntv-tif://channel?input=${URLEncoder.encode(inputId, "UTF-8")}&id=$channelId" + (videoHeight?.let { "&height=$it" } ?: "")
    companion object {
        fun parse(value: String): TifChannelUri? = runCatching {
            val uri = URI(value)
            if (uri.scheme != "owntv-tif" || uri.host != "channel" || uri.fragment != null) return null
            val pairs = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
            if (pairs.any { it.size != 2 } || pairs.map { it[0] }.distinct().size != pairs.size) return null
            val fields = pairs.associate { it[0] to URLDecoder.decode(it[1], "UTF-8") }
            val input = fields["input"] ?: return null
            val id = fields["id"]?.toLongOrNull() ?: return null
            if (id < 0 || input.length !in 3..512 || !input.contains('/') || input.any { it.isISOControl() }) return null
            val height = fields["height"]?.toIntOrNull()?.takeIf { it in setOf(480, 576, 720, 1080, 2160, 4320) }
            TifChannelUri(input, id, height)
        }.getOrNull()
    }
}

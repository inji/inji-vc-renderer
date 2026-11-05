package io.mosip.injivcrenderer.templateEngine.svg

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import io.mosip.injivcrenderer.constants.Constants.QR_CODE_FALLBACK_IMAGE_ID
import io.mosip.injivcrenderer.constants.Constants.QR_CODE_IMAGE_ID
import io.mosip.injivcrenderer.constants.Constants.QR_CODE_PLACEHOLDER
import io.mosip.injivcrenderer.constants.Constants.QR_IMAGE_PREFIX
import io.mosip.injivcrenderer.constants.Constants.RENDER_PROPERTY
import io.mosip.injivcrenderer.constants.Constants.TEMPLATE
import io.mosip.injivcrenderer.constants.VcRendererErrorCodes.MISSING_JSON_PATH
import io.mosip.injivcrenderer.qrCode.QrCodeGenerator
import io.mosip.injivcrenderer.qrCode.QrCodeGenerator.Companion.DEFAULT_FALLBACK_QR_BASE64
import java.io.ByteArrayOutputStream
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class JsonPointerResolver(private val traceabilityId: String) {
    private val className = JsonPointerResolver::class.simpleName

    fun replaceSvgPlaceholders(
        svg: String,
        vcJsonNode: JsonNode,
        renderMethodElement: JsonNode,
        vcJsonString: String,
        qrCodeData: String?
    ): String {
        val svgWithQrCodeReplaced = replaceQrCodePlaceholder(svg, vcJsonString, qrCodeData)
        val svgWithValues = replaceVcPlaceholders(svgWithQrCodeReplaced, vcJsonNode, renderMethodElement)
        return replaceInvalidImageHrefs(svgWithValues)
    }

    /**
     * The old flow deleted an image whose href was "-". Keep the tag and show the
     * dummy portrait instead. That picture already includes the "No Image Available" label.
     * A normal base64 photo is left unchanged. Text that resolved to "-" is left unchanged.
     */
    private fun replaceInvalidImageHrefs(svg: String): String {
        return IMAGE_TAG_REGEX.replace(svg) { imageMatch ->
            HREF_ATTR_REGEX.replace(imageMatch.value) { hrefMatch ->
                val value = hrefMatch.groupValues[3]
                if (isRenderableImageHref(value)) {
                    hrefMatch.value
                } else {
                    val prefix = hrefMatch.groupValues[1]
                    val quote = hrefMatch.groupValues[2]
                    "${prefix}href=$quote${NoImagePlaceholder.DATA_URI}$quote"
                }
            }
        }
    }

    private fun isRenderableImageHref(href: String): Boolean {
        val value = href.trim()
        return when {
            HTTP_URL_REGEX.containsMatchIn(value) -> true
            value.startsWith("data:", ignoreCase = true) -> isValidImageDataUri(value)
            else -> false
        }
    }

    private fun isValidImageDataUri(dataUri: String): Boolean {
        val content = dataUri.substring("data:".length)
        val commaIndex = content.indexOf(',')
        if (commaIndex == -1) return false

        val metadata = content.substring(0, commaIndex).split(';').map { it.trim().lowercase() }
        val mediaType = metadata.first()
        if (!mediaType.startsWith("image/")) return false

        val payload = content.substring(commaIndex + 1).filterNot { it.isWhitespace() }
        val bytes = if ("base64" in metadata.drop(1)) decodeBase64(payload) else decodePercentEncoded(payload)
        return bytes != null && isImageContent(bytes, mediaType)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64(value: String): ByteArray? {
        return if (isBase64(value)) Base64.decode(value) else null
    }

    private fun decodePercentEncoded(value: String): ByteArray? {
        if (!isPercentEncoded(value)) return null
        val bytes = ByteArrayOutputStream()
        var index = 0
        while (index < value.length) {
            val percentIndex = value.indexOf('%', index)
            if (percentIndex == -1) {
                bytes.write(value.substring(index).encodeToByteArray())
                break
            }
            bytes.write(value.substring(index, percentIndex).encodeToByteArray())
            bytes.write(value.substring(percentIndex + 1, percentIndex + 3).toInt(16))
            index = percentIndex + 3
        }
        return bytes.toByteArray()
    }

    private fun isImageContent(bytes: ByteArray, mediaType: String): Boolean {
        if (mediaType == "image/svg+xml") return bytes.decodeToString().contains("<svg", ignoreCase = true)
        return IMAGE_SIGNATURES.any { signature ->
            bytes.size >= signature.size && signature.indices.all { bytes[it] == signature[it] }
        }
    }

    private fun isPercentEncoded(value: String): Boolean {
        return value.isNotEmpty() && !MALFORMED_PERCENT_ESCAPE_REGEX.containsMatchIn(value)
    }

    private fun isBase64(value: String): Boolean {
        val paddingCount = value.length - value.trimEnd('=').length
        return value.isNotEmpty() &&
            value.length % 4 == 0 &&
            paddingCount <= 2 &&
            value.dropLast(paddingCount).all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' }
    }

    private fun replaceVcPlaceholders(svg: String, vcJsonNode: JsonNode, element: JsonNode): String {
        val renderProperties =
            element.path(TEMPLATE).path(RENDER_PROPERTY)
                .takeIf { it.isArray }
                ?.map { it.asText() }

        return JsonPointerResolver(traceabilityId).replacePlaceholders(
            svgTemplate = svg,
            jsonNode = vcJsonNode,
            renderProperties = renderProperties
        )
    }

    private fun replaceQrCodePlaceholder(svg: String, vcJsonString: String, qrCodeData: String?): String {
        if (!svg.contains(QR_CODE_PLACEHOLDER)) {
            return svg
        }

        val qrBase64 = try {
            if (!qrCodeData.isNullOrEmpty()) {
                QrCodeGenerator(traceabilityId)
                    .generateFromQrData(qrCodeData)
            } else {
                QrCodeGenerator(traceabilityId)
                    .generateFromVcJson(vcJsonString)
            }
        } catch (e: Exception) {
            println("[$traceabilityId] QR generation failed: ${e.message}")
            null
        }

        val finalQrBase64 = qrBase64.takeUnless { it.isNullOrEmpty() } ?: DEFAULT_FALLBACK_QR_BASE64
        val qrImageTag = "$QR_IMAGE_PREFIX,$finalQrBase64"

        val imageId = if (qrBase64.isNullOrEmpty()) QR_CODE_FALLBACK_IMAGE_ID else QR_CODE_IMAGE_ID

        return svg
            .replace(QR_CODE_PLACEHOLDER, qrImageTag)
            .replace(QR_CODE_IMAGE_ID, imageId)
    }

    /**
     * Replaces placeholders in an SVG template using a Verifiable Credential JSON for values.
     * @param svgTemplate The SVG template containing placeholders in the format {{/json/pointer}} or {{}}
     * @param jsonNode The root JsonNode of the Verifiable Credential or WellKnown Json
     * @param renderProperties Optional list of allowed JSON pointer paths; others will be replaced with "-"
     */
    fun replacePlaceholders(
        svgTemplate: String,
        jsonNode: JsonNode,
        renderProperties: List<String>? = null
    ): String {
        return PLACEHOLDER_REGEX.replace(svgTemplate) { match ->
            val pointerPath = match.groups[1]?.value ?: ""
            if (renderProperties != null && pointerPath !in renderProperties) return@replace "-"

            val valueNode: JsonNode? = try {
                if (pointerPath.isEmpty()) jsonNode
                else jsonNode.at(JsonPointer.compile(pointerPath)).takeIf { !it.isMissingNode }
            } catch (e: Exception) {
                Logger.getLogger(className).log(
                    Level.SEVERE,
                    "ERROR [$MISSING_JSON_PATH] - Missing: $pointerPath | Class: $className | TraceabilityId: $traceabilityId"
                )
                null
            }

            when {
                valueNode == null || valueNode.isNull -> "-"
                valueNode.isValueNode -> valueNode.asText()
                else -> valueNode.toString()
            }
        }
    }

    companion object {
        private val PLACEHOLDER_REGEX = Regex("\\{\\{(/[^}]*)\\}\\}|\\{\\{\\}\\}")
        private val IMAGE_TAG_REGEX = Regex(
            """<(?:[\w.-]+:)?image\b[^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val HREF_ATTR_REGEX = Regex(
            """(?<![\w.:-])((?:[\w.-]+:)?)href\s*=\s*(["'])(.*?)\2""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val MALFORMED_PERCENT_ESCAPE_REGEX = Regex("%(?![0-9A-Fa-f]{2})")
        private val HTTP_URL_REGEX = Regex(
            """^https?://(?:[^/?#@\s]*@)?[^/?#@:\s][^/?#@\s]*(?:[/?#]|$)""",
            RegexOption.IGNORE_CASE
        )
        private val IMAGE_SIGNATURES = listOf(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
            "GIF87a".encodeToByteArray(),
            "GIF89a".encodeToByteArray(),
            byteArrayOf(0x42, 0x4D),
            byteArrayOf(0x49, 0x49, 0x2A, 0x00),
            byteArrayOf(0x4D, 0x4D, 0x00, 0x2A)
        )
    }
}

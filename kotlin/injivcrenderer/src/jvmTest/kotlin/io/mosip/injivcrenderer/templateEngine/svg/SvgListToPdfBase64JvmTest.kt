package io.mosip.injivcrenderer.templateEngine.svg


import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.pdfbox.pdmodel.PDDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import java.util.Base64

class SvgListToPdfBase64JvmTest {

    private val svg1 = """
        <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
            <rect width="100" height="100" fill="red"/>
        </svg>
    """.trimIndent()

    private val svg2 = """
        <svg xmlns="http://www.w3.org/2000/svg" width="200" height="50">
            <circle cx="25" cy="25" r="20" fill="blue"/>
        </svg>
    """.trimIndent()

    @Test
    fun `test multiple SVGs produce multipage PDF`() {
        val base64Pdf = svgListToPdfBase64(listOf(svg1, svg2))
        val pdfBytes = Base64.getDecoder().decode(base64Pdf)
        assertTrue(pdfBytes.isNotEmpty(), "PDF bytes should not be empty")
        PDDocument.load(pdfBytes.inputStream()).use { doc ->
            assertEquals(2, doc.numberOfPages, "PDF should contain 2 pages")
            doc.pages.forEachIndexed { idx, page ->
                val mediaBox = page.mediaBox
                assertTrue(
                    mediaBox.width > 0 && mediaBox.height > 0,
                    "Page $idx should have non-zero dimensions"
                )
            }
        }
    }

    @Test
    fun `test invalid SVG throws exception`() {
        val invalidSvg = "<svg><broken></svg"

        try {
            svgListToPdfBase64(listOf(invalidSvg))
            kotlin.test.fail("Expected exception for invalid SVG")
        } catch (e: Exception) {
            assertTrue(e is org.apache.batik.transcoder.TranscoderException || e is RuntimeException)
        }
    }

    @Test
    fun `test single SVG produces single page PDF`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="120" height="80">
        <ellipse cx="60" cy="40" rx="50" ry="30" fill="green"/>
    </svg>""".trimIndent()

        val base64Pdf = svgListToPdfBase64(listOf(svg))
        val pdfBytes = Base64.getDecoder().decode(base64Pdf)

        PDDocument.load(pdfBytes.inputStream()).use { doc ->
            assertEquals(1, doc.numberOfPages, "Single SVG should create a PDF with exactly 1 page")
        }
    }

    @Test
    fun `test missing or invalid image href still produces PDF`() {
        val mapper = ObjectMapper()
        val renderMethod = mapper.readTree("""{"type":"TemplateRenderMethod"}""")
        val tinyPng = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        val portrait = """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="200" height="200">""" +
            """<image width="80" height="100" xlink:href="{{/credentialSubject/face}}"/></svg>"""
        val prefixed = portrait.replace("{{/credentialSubject/face}}", "data:image/png;base64,{{/credentialSubject/face}}")
        val withDataHref = portrait.replace("xlink:href=", "data-href=\"-\" xlink:href=")
        val withTitle = portrait.replace("/></svg>", "><title>Portrait</title></image></svg>")

        val cases = listOf(
            portrait to null,
            portrait to "",
            portrait to "   ",
            prefixed to null,
            portrait to "data:image/png;base64,abc",
            portrait to "data:image/png;base64,iVBO=Rw0",
            portrait to "data:image/png;base64,%%garbage%%",
            portrait to "abc",
            portrait to "null",
            portrait to "data:text/plain;base64,aGVsbG8=",
            portrait to "data:image/png,%GG",
            portrait to "data:image/svg+xml,%3Csvg%",
            portrait to "logo.png",
            portrait to "#photo",
            portrait to "https://",
            withTitle to null,
            withDataHref to tinyPng
        )

        for ((template, face) in cases) {
            val credentialSubject = if (face == null) emptyMap() else mapOf("face" to face)
            val vc = mapper.valueToTree<JsonNode>(mapOf("credentialSubject" to credentialSubject))
            val svg = JsonPointerResolver("test-trace-id").replaceSvgPlaceholders(template, vc, renderMethod, "{}", null)

            val base64Pdf = runCatching { svgListToPdfBase64(listOf(svg)) }
                .getOrElse { fail("PDF generation failed for face: $face", it) }

            PDDocument.load(Base64.getDecoder().decode(base64Pdf).inputStream()).use { doc ->
                assertEquals(1, doc.numberOfPages, "face: $face")
            }
        }
    }

}
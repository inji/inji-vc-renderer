package io.mosip.injivcrenderer.templateEngine.svg

import io.mosip.injivcrenderer.common.encodeToBase64
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.fop.configuration.DefaultConfigurationBuilder
import org.apache.fop.svg.PDFTranscoder
import org.apache.pdfbox.multipdf.PDFMergerUtility
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

private object FontLoader

private data class FontEntry(val resource: String, val name: String, val weight: String)

private val FONTS = listOf(
    FontEntry("/fonts/NotoSans-Regular.ttf",           "Noto Sans",            "normal"),
    FontEntry("/fonts/NotoSans-Bold.ttf",              "Noto Sans",            "bold"),
    FontEntry("/fonts/NotoSansDevanagari-Regular.ttf", "Noto Sans Devanagari", "normal"),
    FontEntry("/fonts/NotoSansTamil-Regular.ttf",      "Noto Sans Tamil",      "normal"),
    FontEntry("/fonts/NotoSansKannada-Regular.ttf",    "Noto Sans Kannada",    "normal"),
    FontEntry("/fonts/NotoSansArabic-Regular.ttf",     "Noto Sans Arabic",     "normal"),
)

// Extract bundled TTF files from the jar to temp files once.
// FOP requires file:// URIs to load font data; classpath streams are not sufficient.
private val extractedFonts: Map<String, File> by lazy {
    FONTS.associate { entry ->
        val stream = FontLoader::class.java.getResourceAsStream(entry.resource)
            ?: error("Font not found in classpath: ${entry.resource}")
        val tmp = Files.createTempFile("inji-noto-", ".ttf").toFile().also { it.deleteOnExit() }
        stream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        entry.resource to tmp
    }
}

// Register fonts with AWT so Batik can use them for text metrics and layout.
private val fontsRegistered: Unit by lazy {
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
    extractedFonts.values.forEach { fontFile ->
        ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, fontFile))
    }
}

// FOP config with <fonts> as a direct child of <fop>.
// PDFDocumentGraphics2DConfigurator.createFontInfo() calls cfg.getChild("fonts", false)
// on the root config object, so fonts must NOT be nested under <renderers>/<renderer>.
private val fopConfig by lazy {
    val xml = buildString {
        appendLine("""<fop version="1.0">""")
        appendLine("  <fonts>")
        for (entry in FONTS) {
            val fontFile = extractedFonts[entry.resource] ?: continue
            // toURI().toURL().toString() produces file:///C:/... on Windows (correct 3-slash form)
            val uri = fontFile.toURI().toURL().toString()
            appendLine("""    <font embed-url="$uri" kerning="yes" embedding-mode="subset">""")
            appendLine("""      <font-triplet name="${entry.name}" style="normal" weight="${entry.weight}"/>""")
            appendLine("    </font>")
        }
        appendLine("  </fonts>")
        append("</fop>")
    }
    DefaultConfigurationBuilder().build(ByteArrayInputStream(xml.toByteArray()))
}

private const val NOTO_FALLBACKS = "'Noto Sans', 'Noto Sans Tamil', 'Noto Sans Devanagari', 'Noto Sans Kannada', 'Noto Sans Arabic'"
private val FONT_FAMILY_CSS = Regex("""(font-family\s*:[^;]*?)sans-serif""")

// Injects Noto fonts before the sans-serif fallback in every CSS font-family declaration.
// Batik will then select the appropriate Noto variant per character (e.g. Noto Sans Tamil
// for Tamil codepoints) matching how a browser falls back through the font-family chain.
private fun injectNotoFallbacks(svg: String): String =
    FONT_FAMILY_CSS.replace(svg) { match ->
        val before = match.groupValues[1]
        if (before.contains("Noto Sans")) match.value
        else "${before}$NOTO_FALLBACKS, sans-serif"
    }

actual fun svgListToPdfBase64(svgList: List<String>): String {
    fontsRegistered
    val mergedPdf = PDFMergerUtility()
    val tempPdfStreams = mutableListOf<ByteArrayInputStream>()

    try {
        svgList.forEach { svg ->
            val transcoder = PDFTranscoder()
            transcoder.configure(fopConfig)
            val outputStream = ByteArrayOutputStream()
            transcoder.transcode(
                TranscoderInput(ByteArrayInputStream(injectNotoFallbacks(svg).toByteArray())),
                TranscoderOutput(outputStream)
            )
            outputStream.flush()

            tempPdfStreams.add(ByteArrayInputStream(outputStream.toByteArray()))
            outputStream.close()
        }

        val finalOutput = ByteArrayOutputStream()
        mergedPdf.destinationStream = finalOutput
        tempPdfStreams.forEach { mergedPdf.addSource(it) }
        mergedPdf.mergeDocuments(null)

        return encodeToBase64(finalOutput.toByteArray())
    } finally {
        tempPdfStreams.forEach { it.close() }
    }
}
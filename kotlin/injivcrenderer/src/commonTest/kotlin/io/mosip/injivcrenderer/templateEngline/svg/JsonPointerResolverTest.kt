package io.mosip.injivcrenderer.templateEngline.svg

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.mosip.injivcrenderer.templateEngine.svg.JsonPointerResolver
import io.mosip.injivcrenderer.templateEngine.svg.NoImagePlaceholder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class JsonPointerResolverTest {
    private val mapper = ObjectMapper()


    @Test
    fun `replace simple object field`() {

        val svgTemplateWithLocale = "<svg >{{/credentialSubject/gender/0/value}}##{{/credentialSubject/fullName}}</svg>"

        val processedJson = mapper.readTree("""{
            "credentialSubject": {
                "gender":[
                    {
                        "language": "eng",
                        "value": "English Male"
                    }
                ],
                "fullName": "John"
            }
        }""")

        val expected = "<svg >English Male##John</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplateWithLocale, processedJson);
        assertEquals(expected, result)
    }

    @Test
    fun `replace array fields`() {

        val svgTemplateWithLocale = "<svg >{{/credentialSubject/benefits/0}}, {{/credentialSubject/benefits/1}}</svg>"

        val processedJson = mapper.readTree("""{
            "credentialSubject": {
                "benefits":[
                    "Item 1 is on the list",
                    "Item 2 is on the list",
                    "Item 3 is on the list"
                ],
                "fullName": "John"
            }
        }""")

        val expected = "<svg >Item 1 is on the list, Item 2 is on the list</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplateWithLocale, processedJson);
        assertEquals(expected, result)
    }

    @Test
    fun `replace missing pointer returns dash`() {

        val svgTemplateWithLocale = "<svg >{{/credentialSubject/email}}##{{/credentialSubject/middleName}}</svg>"

        val processedJson = mapper.readTree("""{
            "credentialSubject": {
                "gender":[
                    {
                        "language": "eng",
                        "value": "English Male"
                    }
                ],
                "fullName": "John"
            }
        }""")

        val expected = "<svg >-##-</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplateWithLocale, processedJson);
        assertEquals(expected, result)
    }

    @Test
    fun `replace Fields with locale as objects`() { //take

        val svgTemplateWithLocale = "<svg>Gender: {{/credentialSubject/gender/eng}}, பாலினம் : {{/credentialSubject/gender/tam}} </svg>"

        val processedJson = mapper.readTree("""{
            "credentialSubject": {
                "gender": {
                    "eng": "Male",
                    "tam": "ஆண்"
                }
            }
        }""")

        val expected = "<svg>Gender: Male, பாலினம் : ஆண் </svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplateWithLocale, processedJson);
        assertEquals(expected, result)
    }

    @Test
    fun `replace Fields with locale as array of objects`() {

        val svgTemplateWithLocale = "<svg>Gender: {{/credentialSubject/gender/0/value}}, பாலினம் : {{/credentialSubject/gender/1/value}} </svg>"

        val processedJson = mapper.readTree("""{
            "credentialSubject": {
                "gender": [
                    {
                        "language": "eng",
                        "value": "Male" 
                    },
                    {
                        "language": "tam",
                        "value": "ஆண்"
                    }
                ]
            }
        }""")

        val expected = "<svg>Gender: Male, பாலினம் : ஆண் </svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplateWithLocale, processedJson);
        assertEquals(expected, result)
    }

    @Test
    fun `replace nested address fields`() {

        val svgTemplateWithLocale = "<svg>{{/credentialSubject/address/addressLine1/0/value}}</svg>"

        val processedJson = mapper.readTree("""{
            "credentialSubject": {
                "address": {
                    "addressLine1": [
                        {
                            "language": "eng",
                            "value": "TEST_ADDRESS_LINE_1eng"
                        },
                       {
                            "language": "fr",
                            "value": "TEST_ADDRESS_LINE_1fr"
                        }
                    ],
                    "city": [
                        {
                            "language": "eng",
                            "value": "TEST_CITYeng"
                        }
                    ]
                    
                }
            }
        }""")

        val expected = "<svg>TEST_ADDRESS_LINE_1eng</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplateWithLocale, processedJson);
        assertEquals(expected, result)
    }

    @Test
    fun `replace field with slash`() {

        val svgTemplateWithLocale = "<svg >{{/credentialSubject/ac~1dc}}</svg>"

        val processedJson = mapper.readTree("""{
            "credentialSubject": {
                "ac/dc": "current unit"
            }
        }""")

        val expected = "<svg >current unit</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplateWithLocale, processedJson);
        assertEquals(expected, result)
    }

    @Test
    fun `replace field with tilde`() {

        val svgTemplateWithLocale = "<svg >{{/credentialSubject/a~0b}}</svg>"

        val processedJson = mapper.readTree("""{
            "credentialSubject": {
                "a~b": "test"
            }
        }""")

        val expected = "<svg >test</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplateWithLocale, processedJson);
        assertEquals(expected, result)
    }

    @Test
    fun `pointer to root returns full document`() {
        val svgTemplate = "<svg>{{}}</svg>"
        val json = mapper.readTree("""{"a":1,"b":2}""")
        val expected = "<svg>${json.toString()}</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplate, json)
        assertEquals(expected, result)
    }

    @Test
    fun `empty array and object pointers`() {
        val svgTemplate = "<svg>{{/emptyArray}},{{/emptyObject}}</svg>"
        val json = mapper.readTree("""{"emptyArray": [], "emptyObject": {}}""")
        val expected = "<svg>[],{}</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplate, json)
        assertEquals(expected, result)
    }

    @Test
    fun `array index out of bounds returns dash`() {
        val svgTemplate = "<svg>{{/items/99}}</svg>"
        val json = mapper.readTree("""{"items": ["one","two"]}""")
        val expected = "<svg>-</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplate, json)
        assertEquals(expected, result)
    }

    @Test
    fun `multiple tildes and slashes in key`() {
        val svgTemplate = "<svg>{{/a~0b~1c}}</svg>"
        val json = mapper.readTree("""{"a~b/c": "value"}""")
        val expected = "<svg>value</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplate, json)
        assertEquals(expected, result)
    }

    @Test
    fun `unicode characters in keys`() {
        val svgTemplate = "<svg>{{/ключ}}</svg>"
        val json = mapper.readTree("""{"ключ": "значение"}""")
        val expected = "<svg>значение</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplate, json)
        assertEquals(expected, result)
    }


    @Test
    fun `replace simple object field with renderProperty`() {

        val svgTemplateWithLocale = "<svg >{{/issuer}}##{{/credentialSubject/fullName}}</svg>"

        val processedJson = mapper.readTree("""{
            "issuer": "did:mosip:123456789",
            "credentialSubject": {
                "gender":[
                    {
                        "language": "eng",
                        "value": "English Male"
                    }
                ],
                "fullName": "John"
            }
        }""")

        val expected = "<svg >did:mosip:123456789##-</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplateWithLocale, processedJson, listOf("/issuer"));
        assertEquals(expected, result)
    }

    @Test
    fun `replace simple object field with renderProperty as array index`() {

        val svgTemplateWithLocale = "<svg >{{/credentialSubject/gender/0/value}}##{{/credentialSubject/fullName}}</svg>"

        val processedJson = mapper.readTree("""{
            "issuer": "did:mosip:123456789",
            "credentialSubject": {
                "gender":[
                    {
                        "language": "eng",
                        "value": "English Male"
                    }
                ],
                "fullName": "John"
            }
        }""")

        val expected = "<svg >English Male##-</svg>"

        val result = JsonPointerResolver("test-trace-id").replacePlaceholders(svgTemplateWithLocale, processedJson, listOf("/credentialSubject/gender/0/value"));
        assertEquals(expected, result)
    }

    @Test
    fun `missing face image uses placeholder and text dash is kept`() {
        val svg = "<svg><text>{{/credentialSubject/email}}</text>" +
            "<image id=\"portrait\" x=\"10\" y=\"12\" width=\"80\" height=\"100\" xlink:href=\"{{/credentialSubject/face}}\"/>" +
            "<image id=\"logo\" xlink:href=\"$TINY_PNG_DATA_URI\"/></svg>"
        val vc = mapper.readTree("""{"credentialSubject":{"fullName":"John"}}""")
        val renderMethod = mapper.readTree("""{"type":"TemplateRenderMethod"}""")

        val result = JsonPointerResolver("test-trace-id").replaceSvgPlaceholders(svg, vc, renderMethod, "{}", null)

        assertEquals(
            "<svg><text>-</text>" +
                "<image id=\"portrait\" x=\"10\" y=\"12\" width=\"80\" height=\"100\" xlink:href=\"${NoImagePlaceholder.DATA_URI}\"/>" +
                "<image id=\"logo\" xlink:href=\"$TINY_PNG_DATA_URI\"/></svg>",
            result
        )
    }

    @Test
    fun `static dash image href uses placeholder`() {
        val svg = "<svg><image id=\"portrait\" xlink:href=\"-\"/></svg>"
        val vc = mapper.readTree("""{"credentialSubject":{}}""")
        val renderMethod = mapper.readTree("""{"type":"TemplateRenderMethod"}""")

        val result = JsonPointerResolver("test-trace-id").replaceSvgPlaceholders(svg, vc, renderMethod, "{}", null)

        assertEquals(
            "<svg><image id=\"portrait\" xlink:href=\"${NoImagePlaceholder.DATA_URI}\"/></svg>",
            result
        )
    }

    @Test
    fun `valid face data uri image is kept`() {
        val svg = "<svg><image id=\"portrait\" xlink:href=\"{{/credentialSubject/face}}\"/></svg>"
        val vc = mapper.readTree("""{"credentialSubject":{"face":"$TINY_PNG_DATA_URI"}}""")
        val renderMethod = mapper.readTree("""{"type":"TemplateRenderMethod"}""")

        val result = JsonPointerResolver("test-trace-id").replaceSvgPlaceholders(svg, vc, renderMethod, "{}", null)

        assertEquals(
            "<svg><image id=\"portrait\" xlink:href=\"$TINY_PNG_DATA_URI\"/></svg>",
            result
        )
    }

    @Test
    fun `empty or null face image uses placeholder`() {
        for (face in listOf("", "   ", null)) {
            val result = renderSvg(PORTRAIT_TEMPLATE, mapOf("face" to face))

            assertEquals(portraitSvg(NoImagePlaceholder.DATA_URI), result, "face: $face")
        }
    }

    @Test
    fun `face image outside render property uses placeholder`() {
        val result = renderSvg(
            PORTRAIT_TEMPLATE,
            mapOf("fullName" to "John", "face" to TINY_PNG_DATA_URI),
            """{"type":"TemplateRenderMethod","template":{"renderProperty":["/credentialSubject/fullName"]}}"""
        )

        assertEquals(portraitSvg(NoImagePlaceholder.DATA_URI), result)
    }

    @Test
    fun `missing face after data uri prefix uses placeholder`() {
        val svg = "<svg><image id=\"portrait\" xlink:href=\"data:image/png;base64,{{/credentialSubject/face}}\"/></svg>"

        val result = renderSvg(svg, emptyMap())

        assertEquals(portraitSvg(NoImagePlaceholder.DATA_URI), result)
    }

    @Test
    fun `malformed face image uses placeholder`() {
        val malformedFaces = listOf(
            "abc",
            "null",
            "data:image/png;base64,abc",
            "data:image/png;base64,%%garbage%%",
            "data:image/png;base64,iVBO=Rw0",
            "data:image/png;base64,",
            "data:image/png;base64",
            "data:text/plain;base64,aGVsbG8=",
            "data:image/png,",
            "data:image/png,%GG",
            "data:image/svg+xml,%3Csvg%",
            "data:image/png;\nbase64,abc"
        )

        for (face in malformedFaces) {
            val result = renderSvg(PORTRAIT_TEMPLATE, mapOf("face" to face))

            assertEquals(portraitSvg(NoImagePlaceholder.DATA_URI), result, "face: $face")
        }
    }

    @Test
    fun `relative path and fragment face image use placeholder`() {
        val unloadableFaces = listOf("logo.png", "./logo.png", "../images/logo.png", "#photo", "file:///tmp/logo.png")

        for (face in unloadableFaces) {
            val result = renderSvg(PORTRAIT_TEMPLATE, mapOf("face" to face))

            assertEquals(portraitSvg(NoImagePlaceholder.DATA_URI), result, "face: $face")
        }
    }

    @Test
    fun `valid face data uri with line breaks is kept`() {
        val face = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ\n" +
            "AAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

        val result = renderSvg(PORTRAIT_TEMPLATE, mapOf("face" to face))

        assertEquals(portraitSvg(face), result)
    }

    @Test
    fun `https image href is kept`() {
        val logoUrl = "https://example.com/logo.png"

        val result = renderSvg(PORTRAIT_TEMPLATE, mapOf("face" to logoUrl))

        assertEquals(portraitSvg(logoUrl), result)
    }

    @Test
    fun `http url with host is kept`() {
        val urls = listOf(
            "https://example.com:8080/logo.png",
            "https://user:pw@example.com/logo.png",
            "https://[::1]:8080/logo.png",
            "HTTP://EXAMPLE.COM/LOGO.PNG"
        )

        for (url in urls) {
            val result = renderSvg(PORTRAIT_TEMPLATE, mapOf("face" to url))

            assertEquals(portraitSvg(url), result, "face: $url")
        }
    }

    @Test
    fun `data uri without supported image content uses placeholder`() {
        val nonImageFaces = listOf(
            "data:image/png;base64,aGVsbG8=",
            "data:image/png,hello",
            "data:image/jpeg;base64,PHN2Zz48L3N2Zz4=",
            "data:image/svg+xml;base64,aGVsbG8=",
            "data:image/svg+xml,hello",
            "data:image/webp;base64,UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA=="
        )

        for (face in nonImageFaces) {
            val result = renderSvg(PORTRAIT_TEMPLATE, mapOf("face" to face))

            assertEquals(portraitSvg(NoImagePlaceholder.DATA_URI), result, "face: $face")
        }
    }

    @Test
    fun `data uri with image content is kept`() {
        val imageFaces = listOf(
            "data:image/png;base64,/9j/4AAQSkZJRg==",
            "data:image/gif;base64,R0lGODlhAQABAAAAACw=",
            "data:image/bmp;base64,Qk0AAAAA",
            "data:image/tiff;base64,SUkqAA==",
            "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciLz4=",
            "data:image/png,%89PNG%0D%0A%1A%0A"
        )

        for (face in imageFaces) {
            val result = renderSvg(PORTRAIT_TEMPLATE, mapOf("face" to face))

            assertEquals(portraitSvg(face), result, "face: $face")
        }
    }

    @Test
    fun `http url without host uses placeholder`() {
        val hostlessUrls = listOf(
            "https://", "http://", "HTTPS://", "https:///logo.png", "https://?size=1", "https://#photo",
            "https://user@", "https://:443", "https://user@:443/logo.png"
        )

        for (url in hostlessUrls) {
            val result = renderSvg(PORTRAIT_TEMPLATE, mapOf("face" to url))

            assertEquals(portraitSvg(NoImagePlaceholder.DATA_URI), result, "face: $url")
        }
    }

    @Test
    fun `single quoted href uses placeholder with same quote`() {
        val result = renderSvg("<svg><image id='portrait' href='-'></image></svg>", emptyMap())

        assertEquals("<svg><image id='portrait' href='${NoImagePlaceholder.DATA_URI}'></image></svg>", result)
    }

    @Test
    fun `percent encoded face data uri is kept`() {
        val face = "data:image/svg+xml,%3Csvg%20xmlns%3D%27http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%27%2F%3E"

        val result = renderSvg(PORTRAIT_TEMPLATE, mapOf("face" to face))

        assertEquals(portraitSvg(face), result)
    }

    @Test
    fun `data href attribute is unchanged`() {
        val svg = "<svg><image id=\"portrait\" data-href=\"-\" xlink:href=\"{{/credentialSubject/face}}\"/></svg>"

        val result = renderSvg(svg, emptyMap())

        assertEquals("<svg><image id=\"portrait\" data-href=\"-\" xlink:href=\"${NoImagePlaceholder.DATA_URI}\"/></svg>", result)
    }

    @Test
    fun `image without href is unchanged`() {
        val svg = "<svg><image id=\"portrait\" width=\"80\" height=\"100\"/></svg>"

        val result = renderSvg(svg, emptyMap())

        assertEquals(svg, result)
    }

    @Test
    fun `image with child content replaces only opening tag href`() {
        val svg = "<svg><image id=\"portrait\" xlink:href=\"{{/credentialSubject/face}}\">" +
            "<title>Portrait</title><desc>href=\"-\"</desc></image></svg>"

        val result = renderSvg(svg, emptyMap())

        assertEquals(
            "<svg><image id=\"portrait\" xlink:href=\"${NoImagePlaceholder.DATA_URI}\">" +
                "<title>Portrait</title><desc>href=\"-\"</desc></image></svg>",
            result
        )
    }

    private fun renderSvg(
        svg: String,
        credentialSubject: Map<String, Any?>,
        renderMethodJson: String = """{"type":"TemplateRenderMethod"}"""
    ): String {
        val vc = mapper.valueToTree<JsonNode>(mapOf("credentialSubject" to credentialSubject))
        val renderMethod = mapper.readTree(renderMethodJson)
        return JsonPointerResolver("test-trace-id").replaceSvgPlaceholders(svg, vc, renderMethod, "{}", null)
    }

    private fun portraitSvg(href: String) = "<svg><image id=\"portrait\" xlink:href=\"$href\"/></svg>"

    private companion object {
        const val TINY_PNG_DATA_URI =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        const val PORTRAIT_TEMPLATE = "<svg><image id=\"portrait\" xlink:href=\"{{/credentialSubject/face}}\"/></svg>"
    }
}
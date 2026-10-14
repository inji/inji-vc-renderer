## InjiVcRenderer - Kotlin Library
- A Kotlin library to convert SVG Template to SVG Image by replacing the placeholders in the SVG Template with actual Verifiable Credential Json Data. Strictly follows JSON Pointer Algorithm RFC6901 to extract the values from the VC.

### Features
- Downloads the SVG template from the renderMethod field of the VC to support VC Data Model 2.0.
- Replace the placeholders in the SVG template with actual VC Json Data.
- Generates aar and jar from the library .

### Installation

The library is implemented in Kotlin and is compatible with both **Kotlin** and **Java** projects. It is published to Maven Central as two artifacts:

* **`injivcrenderer-aar`** – Android library (AAR)
* **`injivcrenderer-jar`** – JVM library (JAR)

Choose the artifact that best fits your project.

#### Gradle

**Android (AAR)**

```gradle
implementation("io.inji:injivcrenderer-aar:1.0.0-rc.1")
```

**JVM (JAR)**

```gradle
implementation("io.inji:injivcrenderer-jar:1.0.0-rc.1")
```

#### Maven

**Android (AAR)**

```xml
<dependency>
    <groupId>io.inji</groupId>
    <artifactId>injivcrenderer-aar</artifactId>
    <version>1.0.0-rc.1</version>
</dependency>
```

**JVM (JAR)**

```xml
<dependency>
    <groupId>io.inji</groupId>
    <artifactId>injivcrenderer-jar</artifactId>
    <version>1.0.0-rc.1</version>
</dependency>
```

### Build
- Modules in the Kotlin Project
1. example-android-app
    - Application that uses **injivcrenderer** library project to print Updated SVG Template.
    - Update the test data of VC with valid SVG Host URL.
    - Run using  `./gradlew :example-android-app:build`
2. example-java-app
    - Application that uses **injivcrenderer** library project to print Updated SVG Template.
    - Update the test data of VC with valid SVG Host URL.
    - Run using  `./gradlew :example-java-app:build`
3. injivcrenderer
       - Library to replace the placeholders in the Svg Template received from the renderMethod with the actual Verifiable Credential
       - Run using `./gradlew :injivcrenderer:assembleRelease` to generate the aar
       - Gradle task is registered to generate jar by running the command `./gradlew :injivcrenderer:build` which creates jar in the `build/libs` folder
       - Run Tests using `./gradlew testDebugUnitTest` or `./gradlew testReleaseUnitTest` based on the build type.

### Runtime Dependencies
- The library does not bundle its runtime dependencies in the generated aar or jar.
- The runtime dependencies are to be explicitly added in the consumer application to avoid runtime failures.

### API
- `generateCredentialDisplayContent(credentialFormat: CredentialFormat, wellknownJsonString: String? = null, vcJsonString: String, qrCodeData: String? = null)` - expects the Verifiable Credential, Well-known Json, Credential Format and QR Code Data as input and returns the list of replaced SVG Templates.
  - `credentialFormat` - Enum to specify the credential format. Currently only LDP_VC format is supported.
  - `wellknownJsonString` - Well-known Json downloaded in stringified format. It is optional parameter.
  - `vcJsonString` - VC Downloaded in stringified format.
  - `qrCodeData` - Encoded QR code data to embed in the SVG. It is optional parameter.
    - NOTE : It is preferred that `qrCodeData` provided to the method is already encoded (for example, Base45-encoded). 
    - The encoding should be performed before invoking this method.
    

- This method takes entire VC data as input.
- Example :
```
        val vcJsonString = """{
            "credentialSubject": {
                "fullName": "John",
            },
            "renderMethod": {
                    "type": "TemplateRenderMethod",
                    "renderSuite": "svg-mustache",
                      "template": {
                        "id": "https://degree.example/credential-templates/sample.svg",
                        "mediaType": "image/svg+xml",
                        "digestMultibase": "zQmerWC85Wg6wFl9znFCwYxApG270iEu5h6JqWAPdhyxz2dR"
                      }
                  }
              }
        }"""
        // Assume SVG Template hosted is "<svg>{{/credentialSubject/fullName}}</svg>"
    Result will be => [<svg>John</svg>]
```
- Returns the Replaced svg template to render proper SVG Image. It list of SVG Template if multiple render methods are present in the VC.

- `convertSvgToPdf(svgList: List<String>): String` - expects the list of SVG Templates as input and returns the PDF in base64 format.
    - `svgList` - List of SVG Templates to be converted to each pages in PDF.


## Package Structure
```
io.mosip.injivcrenderer/commonMain
├── InjiVcRenderer.kt                  # Main library class with public API
├── constants/         # Constants used across the library
│   ├── Constants.kt   
│   ├── ContentType.kt      
│   ├── CredentialFormat.kt   
│   └── VcRendererErrorCodes.kt #Error codes used for Custom Exceptions              
├── exceptions/        # Exceptions
│   ├── VcRendererExceptions.kt  # Centralized exception definitions
├── extensions/  
│   └── Extensions.kt #Kotlin extension functions 
├── networkManager/         
│   ├── NetworkManager.kt   #Network related utilities  
│   └── TemplateResponse.kt # data class for template download response
│── qrCode/          
│   ├── QRCodeGenerator.kt  # QR code generation utility
│   └── QrDataConvertor.kt # Implementation of QR code generation
│── templateEngine/svg//          
│   ├── JsonPointerResolver.kt  # Json Pointer Algorithm implementation
│   └── SvgToPdfConvertor.kt # Svg to Pdf conversion utility   
│── utils/ - # Helpers and utility classes      
│   ├── TemplateHelper.kt  
│   └── XMLHelper.kt    
```

###### Exceptions

1. InvalidRenderSuiteException is thrown if render suite is not `svg-mustache`
2. InvalidRenderMethodTypeException is thrown if render method type is not `TemplateRenderMethod`
3. QRCodeGenerationFailureException is thrown if QR code generation fails
4. MissingTemplateIdException is thrown if template id is missing in render method
5. SvgFetchException is thrown if fetching SVG from the URL fails
6. InvalidRenderMethodException is thrown if render method object is invalid
7. MultibaseValidationException is thrown if digestMultibase validation fails
8. UnsupportedCredentialFormat is thrown if unsupported credential format is passed to the generateCredentialDisplayContent method


### Steps involved in SVG Template to SVG Image Conversion
- Render Method Extraction from VC
  - Extracts the render method from the VC Json data.
  - If multiple render methods are present, it will process all the render methods and return the list of replaced SVG Templates.

#### Validate renderSuite and type fields
For each item in the renderMethodArray, the library validates the `renderSuite` and `type` fields
- Only `svg-mustache` is supported as renderSuite and `TemplateRenderMethod` is supported as type.


#### Downloading SVG Template from URL in VC
  - If Render Method object has `template` field as object with `id` field as url and `mediaType` as `image/svg+xml`, SVG Template needs to be downloaded from the URL and then replace the placeholders.
      ```
          "renderMethod": {
          "type": "TemplateRenderMethod",
          "renderSuite": "svg-mustache",
          "template": {
                  "id": "https://degree.example/credential-templates/bachelors",
                  "mediaType": "image/svg+xml",
                  "digestMultibase": "zQmerWC85Wg6wFl9znFCwYxApG270iEu5h6JqWAPdhyxz2dR"
              }
          }
      ```
 - Render method type should be `TemplateRenderMethod` and render suite should be `svg-mustache`.
- Note : Embedded SVG Template and hosting render method as jsonld document are not supported in this library. Hosting the SVG Template as URL is supported.

#### Fetching the Template
- Fetches the SVG Template from the URL provided in the `id` field of the `template` object in the render method.
- `mediaType` field in renderMethod should be `image/svg+xml`.
- `image/svg+xml` is used when the URL directly points to SVG Template.
- Validates the `Content-Type` header in the response while downloading Template from the URL.

##### image/svg+xml
- If Content-Type is `image/svg+xml`, it will consider the entire response as SVG Template.
- Example:
    ```
    <svg>...</svg>
    ```
- If Content-Type is not of type `image/svg+xml` , it will throw `SvgFetchException`.



#### Preprocessing the SVG Template
- After fetching raw SVG Template from the URL, it will preprocess the SVG Template for below scenarios before replacing the placeholders.

##### Digest Multibase Validation
- If the `digestMultibase` field is present in the `template` object, it will validate the downloaded SVG Template using the digestMultibase value.
- `MultibaseValidationException` is thrown if the validation fails or digestMultibase is invalid.
- Example:
    ```
          "renderMethod": {
              "type": "TemplateRenderMethod",
              "renderSuite": "svg-mustache",
              "template": {
                      "id": "https://degree.example/credential-templates/bachelors",
                      "mediaType": "image/svg+xml",
                      "digestMultibase": "zQmerWC85Wg6wFl9znFCwYxApG270iEu5h6JqWAPdhyxz2dR"
                  }
          }
      ```
- As per spec `digestMultibase` is optional field and it should follow below standard if present.
    - An OPTIONAL multibase-encoded Multihash of the render method referenced if id is specified. The multibase value MUST be u (base64url-nopad) and the multihash value MUST be SHA-2 with 256-bits of output (0x12).

##### QR Code Placeholder
- If the SVG Template has `{{/qrCodeImage}}` placeholder, it will generate the QR code using Pixelpass library and replace the placeholder with generated QR code image in base64 format.
  - If qrCodeData is provided, the library will generate the QR code using the value supplied.
  - If qrCodeData is not provided, it will generate the QR code using the vcJsonString.
  - Example:
      ```
      val vcJsonString = """{"credentialSubject" : { "id": "did:example:123456789", "name": "Tester"}}"""
        
      val svgTempalte = "<svg><image id = "qrCodeImage" href="{{/qrCodeImage}}"</svg>"
        
      //result => <svg><image id = "qrCodeImage" href="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMgAAADICAYAAACtWK6eAAAABmJLR0QA/wD/AP+gvaeTAAAIKklEQVR4nO3de5QdZZnv8e9M7MzMzM7szszM7s"
- If the template contains a `{{/qrCodeImage}}` placeholder but the QR code generation fails, it replaces the placeholder with an fallback image.
- Note : It is mandatory to have <image id= "qrCodeImage" .../> tag in the SVG template for the QR code replacement to work. Because if it is fallback scenario, `<image>` id will be replaced with `qrCodeFallbackImage` which can be used to identify from consumer side if design have valid QR code or fallback one.


##### Handling Render Property
  - If the `template` field is an object and has `renderMethod` property. Property in the `renderMethod` will be taken into consideration for further processing and rest of the fields placeholders will be replaced with empty string.
    - Example:
        ```
          "renderMethod": {
              "type": "TemplateRenderMethod",
              "renderSuite": "svg-mustache",
              "template": {
                      "id": "https://example.edu/credential-templates/BachelorDegree",
                      "mediaType": "image/svg+xml",
                      "digestMultibase": "zQmerWC85Wg6wFl9znFCwYxApG270iEu5h6JqWAPdhyxz2dR",
                      "renderProperty": [
                        "/issuer", "/validFrom", "/credentialSubject/degree/name"
                      ]
                  }
          }
        ```
    - In the above example, only the fields `issuer`, `validFrom` and `credentialSubject/degree/name` will be considered for replacing the placeholders in the SVG Template.
  - If `renderProperty` is not present, all the fields in the VC will be considered for replacing the placeholders in the SVG Template.

##### Array Fields Handling
- For array fields in the VC, index based approach will be followed.
  - Example:
      ```
      val vcJsonString = """
            {"credentialSubject" : 
                {
                    "benefits": ["Critical Surgery", "Full Health Checkup", "Testing"]
                }
            }
            """
    
      val svgTempalte = "<svg>{{/credentialSubject/benefits/0}},{{/credentialSubject/benefits/1}}</svg>"
    
      //result => <svg>Critical Surgery,Full Health Checkup</svg>
      ```
    - Example for array of objects:
        ```
        val vcJsonString = """{      
          "credentialSubject": {          
                  "awards": [              
                      {"title": "Award1", "year": "2020"},              
                      {"title": "Award2", "year": "2021"}          
                  ]      
              }  
      }"""
    
        val svgTemplate = "<svg>{{/credentialSubject/awards/0/title}} - {{/credentialSubject/awards/0/year}}, {{/credentialSubject/awards/1/title}} - {{/credentialSubject/awards/1/year}}</svg>"
    
        //result => <svg>Award1 - 2020, Award2 - 2021</svg>
        ```

##### Locale Handling
- For locale handling, same JSON Pointer Algorithm is used to extract the value from the VC.
  - Example:
      ```
      val vcJsonString = """
        {      
            "credentialSubject": {
                "city": [
                    {"value": "TestCITY", "language": "eng"},
                    {"value": "VilleTest", "language": "fr"}
                ]
            }
        }   
        """
          
        val svgTempalte = "<svg>{{/credentialSubject/city/0/value}}</svg>"
          
        //result => <svg>TestCITY</svg>
    ```

#### Replacing Placeholders in SVG Template
- Replaces the placeholders in the SVG Template with actual VC Json Data strictly follows JSON Pointer Algorithm RFC6901.
- Returns the list of replaced SVG Templates if multiple render methods are present in the VC.


### References
- [JSON Pointer Algorithm - RFC6901](https://www.rfc-editor.org/rfc/rfc6901)
- [Draft Implementation of Verifiable Credential Rendering Methods](https://w3c-ccg.github.io/vc-render-method/#the-rendermethod-property)
- [Data model 2.0 implementation](https://www.w3.org/TR/vc-data-model-2.0/#reserved-extension-points)
package my.baas.processors

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import my.baas.annotations.GenerateDto
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.tools.Diagnostic

@SupportedAnnotationTypes("my.baas.annotations.GenerateDto")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
class DtoGeneratorProcessor : AbstractProcessor() {

    companion object {
        // Default fields to exclude from ViewDTO
        private val DEFAULT_VIEW_EXCLUSIONS = setOf(
            "deleted", "version", "whenCreated", "whenModified",
            "whoCreated", "whoModified", "tenantId"
        )

        // Default fields to exclude from CreateDTO
        private val DEFAULT_CREATE_EXCLUSIONS = setOf(
            "id", "deleted", "version", "whenCreated", "whenModified",
            "whoCreated", "whoModified", "tenantId", "tenant"
        )
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"

    }

    private lateinit var kaptKotlinGeneratedDir: String

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME].orEmpty()
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        roundEnv.getElementsAnnotatedWith(GenerateDto::class.java)
            .forEach { element ->
                if (element.kind != ElementKind.CLASS) {
                    processingEnv.messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "@GenerateDto can only be applied to classes",
                        element
                    )
                    return@forEach
                }

                val typeElement = element as TypeElement
                val annotation = typeElement.getAnnotation(GenerateDto::class.java)

                generateDtos(typeElement, annotation)
            }

        return false
    }

    private fun generateDtos(classElement: TypeElement, annotation: GenerateDto) {
        val className = classElement.simpleName.toString()
        val packageName = processingEnv.elementUtils.getPackageOf(classElement).toString()

        // Get all fields from the class
        val fields = getFields(classElement)

        if (annotation.createDto) {
            generateCreateDto(packageName, className, fields, annotation.excludeFromCreate)
        }

        if (annotation.viewDto) {
            generateViewDto(packageName, className, fields, annotation.excludeFromView)
        }
    }

    private fun getFields(classElement: TypeElement): List<VariableElement> {
        val fields = mutableListOf<VariableElement>()

        // Get fields from this class and all parent classes until we reach BaseModel
        var currentElement: TypeElement? = classElement
        while (currentElement != null && currentElement.qualifiedName.toString() != "java.lang.Object") {
            fields.addAll(
                currentElement.enclosedElements
                    .filterIsInstance<VariableElement>()
                    .filter {
                        it.kind == ElementKind.FIELD &&
                                !it.simpleName.toString().startsWith("Companion") &&
                                !it.simpleName.toString().startsWith("\$")
                    }
            )

            val superclass = currentElement.superclass
            currentElement = try {
                if (superclass != null) {
                    val element = processingEnv.typeUtils.asElement(superclass) as? TypeElement
                    // Stop at BaseModel to include its fields
                    if (element?.qualifiedName?.toString()?.contains("BaseModel") == true) {
                        fields.addAll(
                            element.enclosedElements
                                .filterIsInstance<VariableElement>()
                                .filter {
                                    it.kind == ElementKind.FIELD &&
                                            !it.simpleName.toString().startsWith("Companion") &&
                                            !it.simpleName.toString().startsWith("\$")
                                }
                        )
                        null
                    } else {
                        element
                    }
                } else null
            } catch (e: Exception) {
                null
            }
        }

        return fields.distinctBy { it.simpleName.toString() }
    }

    private fun generateCreateDto(
        packageName: String,
        className: String,
        fields: List<VariableElement>,
        additionalExclusions: Array<String>
    ) {
        val createDtoName = "${className}CreateDto"
        val exclusions = DEFAULT_CREATE_EXCLUSIONS + additionalExclusions.toSet()

        val includedFields = fields.filter { field ->
            field.simpleName.toString() !in exclusions
        }

        val createDtoBuilder = TypeSpec.classBuilder(createDtoName)
            .addModifiers(KModifier.DATA)

        val constructorBuilder = FunSpec.constructorBuilder()

        includedFields.forEach { field ->
            val fieldName = field.simpleName.toString()
            val fieldType = getKotlinTypeName(field)

            val property = PropertySpec.builder(fieldName, fieldType)
                .initializer(fieldName)
                .build()

            createDtoBuilder.addProperty(property)
            constructorBuilder.addParameter(fieldName, fieldType)
        }

        createDtoBuilder.primaryConstructor(constructorBuilder.build())

        val fileSpec = FileSpec.builder("${packageName}.dto", createDtoName)
            .addType(createDtoBuilder.build())
            .build()

        val kaptKotlinGeneratedDirFile = File(kaptKotlinGeneratedDir)
        kaptKotlinGeneratedDirFile.mkdirs()
        fileSpec.writeTo(kaptKotlinGeneratedDirFile)
    }

    private fun generateViewDto(
        packageName: String,
        className: String,
        fields: List<VariableElement>,
        additionalExclusions: Array<String>
    ) {
        val viewDtoName = "${className}ViewDto"
        val exclusions = DEFAULT_VIEW_EXCLUSIONS + additionalExclusions.toSet()

        val includedFields = fields.filter { field ->
            field.simpleName.toString() !in exclusions
        }

        val viewDtoBuilder = TypeSpec.classBuilder(viewDtoName)
            .addModifiers(KModifier.DATA)

        val constructorBuilder = FunSpec.constructorBuilder()

        includedFields.forEach { field ->
            val fieldName = field.simpleName.toString()
            val fieldType = getKotlinTypeName(field)

            val property = PropertySpec.builder(fieldName, fieldType)
                .initializer(fieldName)
                .build()

            viewDtoBuilder.addProperty(property)
            constructorBuilder.addParameter(fieldName, fieldType)
        }

        viewDtoBuilder.primaryConstructor(constructorBuilder.build())

        val fileSpec = FileSpec.builder("${packageName}.dto", viewDtoName)
            .addType(viewDtoBuilder.build())
            .build()

        val kaptKotlinGeneratedDirFile = File(kaptKotlinGeneratedDir)
        kaptKotlinGeneratedDirFile.mkdirs()
        fileSpec.writeTo(kaptKotlinGeneratedDirFile)
    }

    private fun getKotlinTypeName(element: VariableElement): TypeName {
        return try {
            val typeName = element.asType().toString()
            when {
                typeName.startsWith("java.lang.String") -> STRING
                typeName.startsWith("java.lang.Long") -> LONG
                typeName.startsWith("long") -> LONG
                typeName.startsWith("java.lang.Integer") -> INT
                typeName.startsWith("int") -> INT
                typeName.startsWith("java.lang.Boolean") -> BOOLEAN
                typeName.startsWith("boolean") -> BOOLEAN
                typeName.startsWith("java.time.Instant") -> ClassName("java.time", "Instant")
                typeName.startsWith("java.util.List") -> {
                    // Extract generic type for lists
                    LIST.parameterizedBy(ANY.copy(nullable = true))
                }

                typeName.contains("ReportModel.") -> {
                    // Handle nested enums/classes
                    val nestedType = typeName.substringAfter("ReportModel.")
                    ClassName("my.baas.models", "ReportModel").nestedClass(nestedType)
                }

                else -> {
                    // Try to handle as a class name
                    val packageAndClass = typeName.split(".")
                    if (packageAndClass.size > 1) {
                        ClassName(packageAndClass.dropLast(1).joinToString("."), packageAndClass.last())
                    } else {
                        ClassName("", typeName)
                    }
                }
            }.copy(nullable = true) // Make all fields nullable for DTOs
        } catch (e: Exception) {
            // Fallback to Any? if we can't determine the type
            ANY.copy(nullable = true)
        }
    }

}
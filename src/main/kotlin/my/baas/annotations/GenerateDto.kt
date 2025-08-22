package my.baas.annotations

/**
 * Annotation to generate DTOs for a model class.
 * 
 * @param createDto Generate CreateDTO with only required fields for creation
 * @param viewDto Generate ViewDTO excluding internal/sensitive fields
 * @param excludeFromCreate Fields to exclude from CreateDTO
 * @param excludeFromView Fields to exclude from ViewDTO (in addition to default exclusions)
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateDto(
    val createDto: Boolean = true,
    val viewDto: Boolean = true,
    val excludeFromCreate: Array<String> = [],
    val excludeFromView: Array<String> = []
)
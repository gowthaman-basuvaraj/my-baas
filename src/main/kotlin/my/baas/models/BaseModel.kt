package my.baas.models

import io.ebean.Model
import io.ebean.annotation.*
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import java.time.Instant

@MappedSuperclass
abstract class BaseModel : Model() {

    @Id
    @GeneratedValue
    var id: Long = 0

    @SoftDelete
    var deleted: Boolean = false

    @Version
    var version: Int = 0

    @WhenCreated
    var whenCreated: Instant = Instant.now()

    @WhenModified
    var whenModified: Instant = Instant.now()

    @WhoCreated
    var whoCreated: String = ""

    @WhoModified
    var whoModified: String = ""
}

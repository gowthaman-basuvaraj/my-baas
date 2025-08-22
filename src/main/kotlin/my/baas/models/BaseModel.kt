package my.baas.models

import io.ebean.Model
import io.ebean.annotation.Index
import io.ebean.annotation.TenantId
import io.ebean.annotation.WhenCreated
import io.ebean.annotation.WhenModified
import io.ebean.annotation.WhoCreated
import io.ebean.annotation.WhoModified
import io.ebean.annotation.SoftDelete
import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Version
import java.time.Instant

@MappedSuperclass
abstract class BaseModel: Model() {

    @Id
    @GeneratedValue
    var id: Long = 0

    @SoftDelete
    var deleted: Boolean = false

    @Version
    var version: Int = 0

    @WhenCreated
    lateinit var whenCreated: Instant

    @WhenModified
    lateinit var whenModified: Instant

    @WhoCreated
    lateinit var whoCreated: String

    @WhoModified
    lateinit var whoModified: String
}

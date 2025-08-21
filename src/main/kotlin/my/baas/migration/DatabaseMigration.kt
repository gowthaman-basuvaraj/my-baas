package my.baas.migration

import io.ebean.annotation.Platform
import io.ebean.dbmigration.DbMigration
import org.slf4j.LoggerFactory
import java.io.IOException

object DatabaseMigration {

    private val logger = LoggerFactory.getLogger(DatabaseMigration::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        generateMigration()
    }

    private fun generateMigration() {
        val dbMigration = DbMigration.create()

        // Set the platform to PostgreSQL
        dbMigration.addPlatform(Platform.POSTGRES, "postgres")

        // Set the path for generated migration files
        dbMigration.setPathToResources("src/main/resources")

        try {
            // Generate the migration DDL scripts
            dbMigration.generateMigration()
            logger.info("Migration files generated successfully!")
        } catch (e: IOException) {
            logger.error("Error generating migration: ${e.message}", e)
        }
    }
}
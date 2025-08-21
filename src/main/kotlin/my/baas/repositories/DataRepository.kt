package my.baas.repositories

import io.ebean.DB
import my.baas.config.AppContext.db
import my.baas.models.DataModel

interface DataRepository {
    fun save(dataModel: DataModel): DataModel
    fun findById(id: Long): DataModel?
    fun findAll(): List<DataModel>
    fun findAllByEntityName(entityName: String): List<DataModel>
    fun update(dataModel: DataModel): DataModel
    fun deleteById(id: Long): Boolean
    fun findByUniqueIdentifier(entityName: String, uniqueIdentifier: String): DataModel?
    fun findByUniqueIdentifiers(entityName: String, uniqueIdentifiers: List<String>): List<DataModel>
    fun deleteByUniqueIdentifier(entityName: String, uniqueIdentifier: String): Boolean
}

class DataRepositoryImpl : DataRepository {

    override fun save(dataModel: DataModel): DataModel {
        dataModel.save()
        return dataModel
    }

    override fun findById(id: Long): DataModel? {
        return DB.find(DataModel::class.java, id)
    }

    override fun findAll(): List<DataModel> {
        return DB.find(DataModel::class.java).findList()
    }

    override fun findAllByEntityName(entityName: String): List<DataModel> {
        return DB.find(DataModel::class.java)
            .where()
            .eq("entityName", entityName)
            .findList()
    }

    override fun update(dataModel: DataModel): DataModel {
        dataModel.update()
        return dataModel
    }

    override fun deleteById(id: Long): Boolean {
        val dataModel = findById(id)
        return if (dataModel != null) {
            dataModel.delete()
            true
        } else {
            false
        }
    }

    override fun findByUniqueIdentifier(entityName: String, uniqueIdentifier: String): DataModel? {
        return DB.find(DataModel::class.java)
            .where()
            .eq("uniqueIdentifier", uniqueIdentifier)
            .eq("entityName", entityName)
            .findOne()
    }

    override fun findByUniqueIdentifiers(
        entityName: String,
        uniqueIdentifiers: List<String>
    ): List<DataModel> {
        return db.find(DataModel::class.java)
            .where()
            .eq("entityName", entityName)
            .`in`("uniqueIdentifier", uniqueIdentifiers)
            .findList()
    }

    override fun deleteByUniqueIdentifier(entityName: String, uniqueIdentifier: String): Boolean {
        val dataModel = findByUniqueIdentifier(entityName, uniqueIdentifier)
        return if (dataModel != null) {
            dataModel.delete()
            true
        } else {
            false
        }
    }
}
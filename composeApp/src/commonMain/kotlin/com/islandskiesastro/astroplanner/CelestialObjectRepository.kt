package com.islandskiesastro.astroplanner

import com.islandskiesastro.astroplanner.database.AstroDatabase
import com.islandskiesastro.astroplanner.database.CelestialObject as DbRow
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class DsoResponse(
    val displayName: String,
    val objectId: String,
    val ra: Double,
    val dec: Double,
    val type: String,
    val subType: String? = null,
    val constellation: String? = null,
    val recommended: Boolean
)

class CelestialObjectRepository(driverFactory: DatabaseDriverFactory) {
    private val db = AstroDatabase(driverFactory.createDriver())
    private val queries = db.celestialObjectQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val planets = listOf(
        "mercury", "venus", "mars", "jupiter", "saturn", "uranus", "neptune"
    )

    fun getAllObjects(): List<CelestialObject> =
        queries.selectAll().executeAsList().map { it.toDomain() }

    fun getRecommendedObjects(): List<CelestialObject> =
        queries.selectRecommended().executeAsList().map { it.toDomain() }

    suspend fun updateCatalog(onStatus: (String) -> Unit) {
        onStatus("Inserting planets...")
        queries.deleteAll()
        planets.forEach { name ->
            queries.insert(
                displayName = name.replaceFirstChar { it.uppercase() },
                objectId = name,
                ra = 0.0,
                dec = 0.0,
                type = ObjectType.PLANET.name,
                subType = null,
                constellation = "",
                recommended = 1L
            )
        }

        onStatus("Fetching DSO data...")
        try {
            val responseText = client.get(Config.DSO_URL).bodyAsText()
            val dsoList: List<DsoResponse> = json.decodeFromString(responseText)
            onStatus("DSO data loaded — ${dsoList.size} objects")
            dsoList.forEach { dso ->
                queries.insert(
                    displayName = dso.displayName,
                    objectId = dso.objectId,
                    ra = dso.ra * 15.0,  // endpoint returns RA in hours; DB stores degrees
                    dec = dso.dec,
                    type = dsoTypeToObjectType(dso.type).name,
                    subType = dso.subType,
                    constellation = dso.constellation,
                    recommended = if (dso.recommended) 1L else 0L
                )
            }
        } catch (e: Exception) {
            onStatus("DSO data loading failed: ${e.message}")
        }
    }

    private fun dsoTypeToObjectType(type: String): ObjectType = when (type.lowercase()) {
        "nebula" -> ObjectType.NEBULA
        "galaxy" -> ObjectType.GALAXY
        "cluster" -> ObjectType.CLUSTER
        "star" -> ObjectType.STAR
        else -> ObjectType.UNKNOWN
    }

    private fun DbRow.toDomain() = CelestialObject(
        id = id,
        displayName = displayName,
        objectId = objectId,
        ra = ra,
        dec = dec,
        type = ObjectType.entries.find { it.name == type } ?: ObjectType.UNKNOWN,
        subType = subType,
        constellation = constellation,
        recommended = recommended != 0L
    )
}

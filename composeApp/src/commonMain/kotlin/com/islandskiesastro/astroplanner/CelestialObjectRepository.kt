package com.islandskiesastro.astroplanner

import com.islandskiesastro.astroplanner.database.AstroDatabase
import com.islandskiesastro.astroplanner.database.CelestialObject as DbRow
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
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
    val recommended: Boolean,
    val magnitude: Double? = null,
    val angularSizeMajor: Double? = null,
    val angularSizeMinor: Double? = null
)

@Serializable
data class ImageResponse(
    val objectId: String,
    val url: String,
    val thumbX: Int? = null,
    val thumbY: Int? = null,
    val thumbDim: Int? = null
)

class CelestialObjectRepository(
    driverFactory: DatabaseDriverFactory,
    private val imageStorage: ImageStorage
) {
    private val db = AstroDatabase(driverFactory.createDriver())
    private val queries = db.celestialObjectQueries
    private val imageQueries = db.celestialObjectImageQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val planets = listOf(
        "moon", "mercury", "venus", "mars", "jupiter", "saturn", "uranus", "neptune"
    )

    fun getAllObjects(): List<CelestialObject> =
        queries.selectAll().executeAsList().map { it.toDomain() }

    fun getRecommendedObjects(): List<CelestialObject> =
        queries.selectRecommended().executeAsList().map { it.toDomain() }

    fun getImagesMap(): Map<String, CelestialObjectImage> {
        val dir = imageStorage.getDir()
        return imageQueries.selectAll().executeAsList().associate { row ->
            // Stored value may be a bare filename (new) or an absolute path (legacy).
            // Always reconstruct from the current documents directory so paths remain
            // valid across iOS reinstalls/simulator redeployments.
            fun resolve(stored: String?): String? {
                if (stored == null) return null
                val filename = stored.substringAfterLast('/')
                return "$dir/$filename"
            }
            row.objectId to CelestialObjectImage(
                objectId  = row.objectId,
                url       = row.url,
                fullPath  = resolve(row.fullPath),
                thumbPath = resolve(row.thumbPath),
                thumbX    = row.thumbX?.toInt(),
                thumbY    = row.thumbY?.toInt(),
                thumbDim  = row.thumbDim?.toInt()
            )
        }
    }

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
                recommended = 1L,
                magnitude = null,
                angularSizeMajor = null,
                angularSizeMinor = null
            )
        }

        onStatus("Fetching DSO data...")
        try {
            val responseText = client.get("${Config.DSO_URL}?t=${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}").bodyAsText()
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
                    recommended = if (dso.recommended) 1L else 0L,
                    magnitude = dso.magnitude,
                    angularSizeMajor = dso.angularSizeMajor,
                    angularSizeMinor = dso.angularSizeMinor
                )
            }
        } catch (e: Exception) {
            onStatus("DSO data loading failed: ${e.message}")
        }
    }

    suspend fun updateImages(onStatus: (String) -> Unit) {
        try {
            onStatus("Fetching image list...")
            imageQueries.deleteAll()
            val responseText = client.get("${Config.IMAGES_URL}?t=${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}").bodyAsText()
            val imageList: List<ImageResponse> = json.decodeFromString(responseText)
            onStatus("Downloading ${imageList.size} images...")

            imageList.forEach { img ->
                imageQueries.insert(img.objectId, img.url, null, null,
                    img.thumbX?.toLong(), img.thumbY?.toLong(), img.thumbDim?.toLong())
            }

            imageList.forEachIndexed { index, img ->
                try {
                    onStatus("Downloading ${index + 1}/${imageList.size}: ${img.objectId}")
                    val bytes = client.get(img.url).bodyAsBytes()

                    val fullFilename = "${img.objectId}_full.jpg"
                    imageStorage.write(fullFilename, bytes)
                    val fullPath = "${imageStorage.getDir()}/$fullFilename"

                    val thumbFilename = "${img.objectId}_thumb.jpg"
                    val thumbBytes = if (img.thumbX != null && img.thumbY != null && img.thumbDim != null) {
                        cropImageBytes(bytes, img.thumbX, img.thumbY, img.thumbDim)
                    } else {
                        bytes
                    }
                    imageStorage.write(thumbFilename, thumbBytes)
                    val thumbPath = "${imageStorage.getDir()}/$thumbFilename"

                    imageQueries.updatePaths(fullPath, thumbPath, img.objectId)
                } catch (e: Exception) {
                    onStatus("Failed: ${img.objectId} — ${e.message}")
                }
            }
            onStatus("Images updated successfully (${imageList.size} objects)")
        } catch (e: Exception) {
            onStatus("Image update failed: ${e.message}")
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
        recommended = recommended != 0L,
        magnitude = magnitude,
        angularSizeMajor = angularSizeMajor,
        angularSizeMinor = angularSizeMinor
    )
}

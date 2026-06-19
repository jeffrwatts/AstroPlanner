package com.islandskiesastro.astroplanner

import com.islandskiesastro.astroplanner.database.AstroDatabase
import com.islandskiesastro.astroplanner.database.CelestialObject as DbRow
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class SimbadResult {
    data class Success(
        val displayName: String,
        val ra: Double,
        val dec: Double,
        val type: ObjectType
    ) : SimbadResult()
    object AlreadyExists : SimbadResult()
    object NotFound : SimbadResult()
    data class Error(val message: String) : SimbadResult()
}

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
    val catalogId: String? = null,
    val cloudinaryId: String
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

    fun isSystemCatalogEmpty(): Boolean =
        queries.selectAll().executeAsList().none { it.userAdded == 0L }

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
                thumbPath = resolve(row.thumbPath)
            )
        }
    }

    fun existsByObjectId(objectId: String): Boolean =
        queries.selectByObjectId(objectId).executeAsOneOrNull() != null

    suspend fun lookupSimbad(rawInput: String): SimbadResult {
        val objectId = rawInput.trim().lowercase().replace(" ", "")
        if (objectId.isEmpty()) return SimbadResult.Error("Object ID cannot be empty")

        if (existsByObjectId(objectId)) return SimbadResult.AlreadyExists

        return try {
            val adql = "SELECT basic.ra, basic.dec, basic.main_id, basic.otype FROM basic JOIN ident ON basic.oid = ident.oidref WHERE ident.id = '${rawInput.trim()}'"
            val responseText = client.get(Config.SIMBAD_TAP_URL) {
                parameter("REQUEST", "doQuery")
                parameter("LANG", "ADQL")
                parameter("FORMAT", "json")
                parameter("QUERY", adql)
            }.bodyAsText()

            val root = Json.parseToJsonElement(responseText).jsonObject
            val data = root["data"]?.jsonArray ?: return SimbadResult.Error("Unexpected SIMBAD response")
            if (data.isEmpty()) return SimbadResult.NotFound

            val row = data[0].jsonArray
            val ra = row[0].jsonPrimitive.content.toDoubleOrNull()
                ?: return SimbadResult.Error("Could not parse RA from SIMBAD")
            val dec = row[1].jsonPrimitive.content.toDoubleOrNull()
                ?: return SimbadResult.Error("Could not parse Dec from SIMBAD")
            val mainId = row[2].jsonPrimitive.content.trim()
            val otype = row[3].jsonPrimitive.content

            SimbadResult.Success(
                displayName = mainId,
                ra = ra,
                dec = dec,
                type = simbadTypeToObjectType(otype)
            )
        } catch (e: Exception) {
            SimbadResult.Error(e.message ?: "SIMBAD lookup failed")
        }
    }

    fun addUserObject(
        displayName: String,
        objectId: String,
        ra: Double,
        dec: Double,
        type: ObjectType,
        magnitude: Double?
    ) {
        queries.insertUserObject(
            displayName      = displayName,
            objectId         = objectId,
            ra               = ra,
            dec              = dec,
            type             = type.name,
            subType          = null,
            constellation    = null,
            magnitude        = magnitude,
            angularSizeMajor = null,
            angularSizeMinor = null
        )
    }

    fun deleteUserObject(id: Long) {
        queries.deleteById(id)
    }

    fun updateUserObject(
        id: Long,
        displayName: String,
        ra: Double,
        dec: Double,
        type: ObjectType,
        magnitude: Double?
    ) {
        queries.updateUserObject(
            displayName = displayName,
            ra          = ra,
            dec         = dec,
            type        = type.name,
            magnitude   = magnitude,
            id          = id
        )
    }

    suspend fun updateCatalog(onStatus: (String) -> Unit) {
        onStatus("Inserting planets...")
        queries.deleteSystemObjects()
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
            // Normalize catalogId to match DSO objectId format (e.g. "IC 1805" → "ic1805")
            // deduplicate so only the first image per object is used
            val seenIds = mutableSetOf<String>()
            val withCatalogId = imageList
                .filter { it.catalogId != null }
                .mapNotNull { img ->
                    val objectId = img.catalogId!!.lowercase().replace(" ", "")
                    if (seenIds.add(objectId)) img.copy(catalogId = objectId) else null
                }
            onStatus("Downloading ${withCatalogId.size} images...")

            withCatalogId.forEach { img ->
                val fullUrl = "${Config.CLOUDINARY_BASE}/w_1080,q_auto,f_jpg/${img.cloudinaryId}"
                imageQueries.insert(img.catalogId!!, fullUrl, null, null, null, null, null)
            }

            withCatalogId.forEachIndexed { index, img ->
                try {
                    onStatus("Downloading ${index + 1}/${withCatalogId.size}: ${img.catalogId}")
                    val fullUrl = "${Config.CLOUDINARY_BASE}/w_1080,q_auto,f_jpg/${img.cloudinaryId}"
                    val thumbUrl = "${Config.CLOUDINARY_BASE}/w_400,q_auto,f_jpg/${img.cloudinaryId}"

                    val fullFilename = "${img.catalogId}_full.jpg"
                    imageStorage.write(fullFilename, client.get(fullUrl).bodyAsBytes())
                    val fullPath = "${imageStorage.getDir()}/$fullFilename"

                    val thumbFilename = "${img.catalogId}_thumb.jpg"
                    imageStorage.write(thumbFilename, client.get(thumbUrl).bodyAsBytes())
                    val thumbPath = "${imageStorage.getDir()}/$thumbFilename"

                    imageQueries.updatePaths(fullPath, thumbPath, img.catalogId!!)
                } catch (e: Exception) {
                    onStatus("Failed: ${img.catalogId} — ${e.message}")
                }
            }
            onStatus("Images updated successfully (${withCatalogId.size} objects)")
        } catch (e: Exception) {
            onStatus("Image update failed: ${e.message}")
        }
    }

    private fun simbadTypeToObjectType(otype: String): ObjectType {
        val lower = otype.lowercase()
        return when {
            "galaxy" in lower || otype in setOf("AGN", "Sy1", "Sy2", "QSO", "EmG", "SBG", "LIN", "BLL", "GiG", "GiC", "BiC") -> ObjectType.GALAXY
            otype in setOf("HII", "PN", "SNR", "RNe", "DkN", "MoC", "Cld", "GNe", "ISM", "SFR") || "neb" in lower -> ObjectType.NEBULA
            otype in setOf("GlCl", "OpCl", "Cl*", "Assoc*", "CIG") || (lower.contains("cl") && !lower.contains("nucl")) -> ObjectType.CLUSTER
            otype == "*" || otype.endsWith("*") -> ObjectType.STAR
            else -> ObjectType.UNKNOWN
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
        angularSizeMinor = angularSizeMinor,
        userAdded = userAdded != 0L
    )
}

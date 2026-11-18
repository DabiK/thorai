package app.regie.storage

import android.content.Context
import android.content.SharedPreferences
import app.regie.domain.model.SessionId
import app.regie.game.engine.village.HouseLayout
import app.regie.game.engine.village.StreetLayout
import app.regie.game.engine.village.VillageLayout
import app.regie.game.engine.village.VillageLayoutStore
import app.regie.game.engine.world.RoofColors
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Adapter `SharedPreferences` du port [VillageLayoutStore] (ADR 24) : la disposition du hameau, en
 * JSON, pour qu'une maison bâtie ne bouge plus d'un lancement à l'autre. Une valeur illisible, ou
 * d'une autre version du format, donne un hameau neuf plutôt qu'un plantage.
 */
class PreferencesVillageLayoutStore(private val preferences: SharedPreferences) : VillageLayoutStore {

    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    override fun load(): VillageLayout {
        val text = preferences.getString(KEY, null) ?: return VillageLayout.EMPTY
        return try {
            decode(JSONObject(text))
        } catch (_: JSONException) {
            VillageLayout.EMPTY
        }
    }

    override fun save(layout: VillageLayout) {
        preferences.edit().putString(KEY, encode(layout).toString()).apply()
    }

    companion object {
        const val FILE = "village"
        private const val KEY = "layout"
        private const val VERSION = 1

        internal fun encode(layout: VillageLayout): JSONObject = JSONObject()
            .put("version", VERSION)
            .put("streets", JSONArray(layout.streets.map { JSONObject().put("project", it.project).put("plots", it.plotCount) }))
            .put(
                "houses",
                JSONObject().also { houses ->
                    layout.houses.forEach { (id, house) ->
                        houses.put(
                            id.value,
                            JSONObject()
                                .put("project", house.project)
                                .put("plot", house.plot)
                                .put("model", house.modelId)
                                .put("roof", JSONArray(listOf(house.roof.light, house.roof.base, house.roof.shadow))),
                        )
                    }
                },
            )

        internal fun decode(json: JSONObject): VillageLayout {
            if (json.optInt("version") != VERSION) return VillageLayout.EMPTY
            val streets = json.getJSONArray("streets").let { array ->
                List(array.length()) { i ->
                    array.getJSONObject(i).let { StreetLayout(it.getString("project"), it.getInt("plots")) }
                }
            }
            val houses = json.getJSONObject("houses").let { all ->
                all.keys().asSequence().associate { id ->
                    val house = all.getJSONObject(id)
                    val roof = house.getJSONArray("roof")
                    SessionId(id) to HouseLayout(
                        project = house.getString("project"),
                        plot = house.getInt("plot"),
                        modelId = house.getString("model"),
                        roof = RoofColors(roof.getInt(0), roof.getInt(1), roof.getInt(2)),
                    )
                }
            }
            return VillageLayout(streets, houses)
        }
    }
}

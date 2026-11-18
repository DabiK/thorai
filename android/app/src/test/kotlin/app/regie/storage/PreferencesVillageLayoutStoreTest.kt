package app.regie.storage

import android.app.Application
import android.content.Context
import app.regie.domain.model.SessionId
import app.regie.game.engine.village.HouseLayout
import app.regie.game.engine.village.PlotReservation
import app.regie.game.engine.village.StreetLayout
import app.regie.game.engine.village.VillageLayout
import app.regie.game.engine.world.RoofColors
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/** La disposition du hameau survit au redémarrage de l'app (ticket 45). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PreferencesVillageLayoutStoreTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private fun preferences() = context.getSharedPreferences(PreferencesVillageLayoutStore.FILE, Context.MODE_PRIVATE)

    private val layout = VillageLayout(
        streets = listOf(StreetLayout("regie", 3), StreetLayout("api-meteo", 2)),
        houses = mapOf(
            SessionId("a") to HouseLayout("regie", 0, "maison.chaumiere", RoofColors(0xFFDB6633.toInt(), 0xFF9E5A37.toInt(), 0xFF503835.toInt())),
            SessionId("b") to HouseLayout("api-meteo", 1, "maison.tour", RoofColors(1, 2, 3)),
        ),
    )

    @Test
    fun `nothing saved yet gives an empty village`() {
        assertEquals(VillageLayout.EMPTY, PreferencesVillageLayoutStore(context).load())
    }

    @Test
    fun `a saved layout is read back by a new store, as after a restart`() {
        PreferencesVillageLayoutStore(context).save(layout)

        assertEquals(layout, PreferencesVillageLayoutStore(context).load())
    }

    @Test
    fun `a reserved plot survives a restart, bound or not (ticket 48)`() {
        val unbound = layout.copy(reservation = PlotReservation("meteo", 0, opensStreet = true))
        PreferencesVillageLayoutStore(context).save(unbound)
        assertEquals(unbound, PreferencesVillageLayoutStore(context).load())

        val bound = layout.copy(reservation = PlotReservation("regie", 2, SessionId("n")))
        PreferencesVillageLayoutStore(context).save(bound)
        assertEquals(bound, PreferencesVillageLayoutStore(context).load())
    }

    @Test
    fun `an unreadable or foreign value gives an empty village instead of a crash`() {
        preferences().edit().putString("layout", "{pas du json").commit()
        assertEquals(VillageLayout.EMPTY, PreferencesVillageLayoutStore(context).load())

        preferences().edit().putString("layout", """{"version":99}""").commit()
        assertEquals(VillageLayout.EMPTY, PreferencesVillageLayoutStore(context).load())
    }
}

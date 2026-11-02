package lowball.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PlayerSnapshot(
    val seat: Int,
    val name: String,
    val isHuman: Boolean,
    val chips: Int,
    val items: List<Item>,
    val loans: List<Loan>,
    val hand: List<DealtCard>,
    val folded: Boolean,
    val eliminated: Boolean,
    val cardsDrawn: Int?,
    val snowing: Boolean,
    val roundsPlayed: Int = 0,
    val aggressiveRounds: Int = 0,
    val aggressiveThisRound: Boolean = false,
)

/** A whole game frozen at one moment, for Save Game and Resume Game. */
@Serializable
data class TableSnapshot(
    val difficulty: Difficulty,
    val players: List<PlayerSnapshot>,
    val dealer: Int,
    val phase: Phase,
    val toAct: Int?,
    val pot: Int,
    val roundNumber: Int,
    val currentBet: Int,
    val betsMade: Int,
    val contributed: List<Int>,
    val backImage: String?,
    val lastResult: RoundResult?,
    val events: List<Event>,
    val pending: List<Int>,
    val queue: List<Int>,
    val deck: List<Card>,
    val lastAggressor: Int? = null,
)

/** Turns a game into text for saving to the phone, and back again. */
object SavedGame {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(table: Table): String = json.encodeToString(TableSnapshot.serializer(), table.snapshot())

    fun decode(text: String, images: CardImages? = null): Table =
        Table.fromSnapshot(json.decodeFromString(TableSnapshot.serializer(), text), images = images)
}

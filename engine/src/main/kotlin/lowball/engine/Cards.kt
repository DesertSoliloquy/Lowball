package lowball.engine

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Card ranks. In lowball the Ace is always the lowest card, so it has value 1. */
enum class Rank(val value: Int, val code: Char, val displayName: String) {
    ACE(1, 'a', "Ace"),
    TWO(2, '2', "Two"),
    THREE(3, '3', "Three"),
    FOUR(4, '4', "Four"),
    FIVE(5, '5', "Five"),
    SIX(6, '6', "Six"),
    SEVEN(7, '7', "Seven"),
    EIGHT(8, '8', "Eight"),
    NINE(9, '9', "Nine"),
    TEN(10, 't', "Ten"),
    JACK(11, 'j', "Jack"),
    QUEEN(12, 'q', "Queen"),
    KING(13, 'k', "King");

    companion object {
        fun fromValue(value: Int): Rank = entries.first { it.value == value }
        fun fromCode(code: Char): Rank? = entries.firstOrNull { it.code == code }
    }
}

enum class Suit(val code: Char, val displayName: String) {
    CLUBS('c', "Clubs"),
    DIAMONDS('d', "Diamonds"),
    HEARTS('h', "Hearts"),
    SPADES('s', "Spades");

    companion object {
        fun fromCode(code: Char): Suit? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One playing card. The joker has no rank or suit.
 * [code] matches the image file names: "3d" for the three of diamonds, "joker" for the joker.
 */
@Serializable
data class Card(val rank: Rank?, val suit: Suit?) {
    val isJoker: Boolean get() = rank == null

    val code: String get() = if (isJoker) JOKER_CODE else "${rank!!.code}${suit!!.code}"

    override fun toString(): String = if (isJoker) "Joker" else "${rank!!.displayName} of ${suit!!.displayName}"

    companion object {
        const val JOKER_CODE = "joker"
        val JOKER = Card(null, null)

        fun of(rank: Rank, suit: Suit) = Card(rank, suit)

        /** Parses a short code such as "3d", "ts" or "joker". */
        fun parse(code: String): Card {
            if (code == JOKER_CODE) return JOKER
            require(code.length == 2) { "Bad card code: $code" }
            val rank = Rank.fromCode(code[0]) ?: error("Bad rank in $code")
            val suit = Suit.fromCode(code[1]) ?: error("Bad suit in $code")
            return Card(rank, suit)
        }
    }
}

/** The 53-card California Lowball deck: 52 cards plus one joker. */
class Deck(private val random: Random) {
    private val cards = ArrayDeque<Card>()

    init {
        shuffle()
    }

    val remaining: Int get() = cards.size

    fun shuffle() {
        cards.clear()
        cards.addAll(fullDeck().shuffled(random))
    }

    fun deal(): Card = cards.removeFirst()

    fun deal(count: Int): List<Card> = List(count) { deal() }

    /** The cards still in the deck, top first. Used when saving a game. */
    fun snapshot(): List<Card> = cards.toList()

    fun restore(saved: List<Card>) {
        cards.clear()
        cards.addAll(saved)
    }

    companion object {
        fun fullDeck(): List<Card> =
            Suit.entries.flatMap { suit -> Rank.entries.map { Card(it, suit) } } + Card.JOKER
    }
}

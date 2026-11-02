package lowball.engine

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Belongings the house will buy from a player who is short of money, most valuable first. */
@Serializable
enum class Item(val displayName: String, val price: Int) {
    GOLD_WATCH("Gold watch", 1_000),
    WEDDING_RING("Wedding ring", 750),
    IPHONE("iPhone", 500),
    GOLD_CHAIN("Gold chain", 400),
    CAR_KEYS("Car keys", 300),
    EYEGLASSES("Eyeglasses", 150),
    PANTS("Pants", 100),
    SUNGLASSES("Sunglasses", 80),
    SHIRT("Shirt", 75),
    WALLET("Wallet (empty, naturally)", 60),
    LEFT_SHOE("Left shoe", 50),
    RIGHT_SHOE("Right shoe", 50),
    BELT("Belt", 40),
    BASEBALL_CAP("Baseball cap", 40),
    RABBITS_FOOT("Lucky rabbit's foot", 15),
    LEFT_SOCK("Left sock", 10),
    RIGHT_SOCK("Right sock", 10),
    UNDERWEAR("Underwear", 5),
}

/** An item the house is holding. [balance] is what the player still owes before it comes back. */
@Serializable
data class Loan(val item: Item, val amount: Int, var balance: Int)

@Serializable
enum class Difficulty { EASY, MEDIUM, HARD }

/** A card in a player's hand together with the picture chosen for it this round. */
@Serializable
data class DealtCard(val card: Card, val image: String?)

class Player(
    val seat: Int,
    val name: String,
    val isHuman: Boolean,
    var chips: Int,
) {
    /** Belongings the player still has. */
    val items: MutableList<Item> = Item.entries.toMutableList()

    /** Belongings the house holds, oldest first. */
    val loans: MutableList<Loan> = mutableListOf()

    val hand: MutableList<DealtCard> = mutableListOf()

    /** Out of this round (folded or sat out). */
    var folded: Boolean = false

    /** Out of the game: no money for the ante and nothing left to sell. */
    var eliminated: Boolean = false

    /** Cards drawn this round, or null before this player has drawn. 0 means stood pat. */
    var cardsDrawn: Int? = null

    /** Computer players only: standing pat on a bad hand this round to bluff (a "snow"). */
    var snowing: Boolean = false

    /** Rounds this player was dealt into, for the computer players' memory of how they bet. */
    var roundsPlayed: Int = 0

    /** Rounds in which this player bet or raised at least once. */
    var aggressiveRounds: Int = 0

    /** Has bet or raised during the current round. */
    var aggressiveThisRound: Boolean = false

    /** How often this player bets or raises, from 0 to 1. Assumes a normal 30% until there's enough to go on. */
    val aggression: Double
        get() = if (roundsPlayed < 4) NORMAL_AGGRESSION else aggressiveRounds.toDouble() / roundsPlayed

    val inHand: Boolean get() = !eliminated && !folded

    val cards: List<Card> get() = hand.map { it.card }

    /** Sells [item] to the house, which holds it as a loan. */
    fun sell(item: Item) {
        require(items.remove(item)) { "$name doesn't have ${item.displayName}" }
        chips += item.price
        loans.add(Loan(item, item.price, item.price))
    }

    /**
     * Pays the house its share of a won pot toward the oldest loans.
     * Returns the items that came back.
     */
    fun repayLoans(amount: Int): List<Item> {
        var left = amount
        val returned = mutableListOf<Item>()
        while (left > 0 && loans.isNotEmpty()) {
            val loan = loans.first()
            val payment = minOf(left, loan.balance)
            loan.balance -= payment
            chips -= payment
            left -= payment
            if (loan.balance == 0) {
                loans.removeAt(0)
                items.add(loan.item)
                returned.add(loan.item)
            }
        }
        items.sortBy { it.ordinal }
        return returned
    }
}

const val NORMAL_AGGRESSION = 0.3

object OpponentNames {
    val ALL = listOf(
        "Never Sober Sam",
        "Born t' Hang Hank",
        "One Arm Kate",
        "Whiskey Pete",
        "Calamity Carla",
        "Lefty Lou",
        "Snake Oil Sal",
        "Busted Flush Barney",
        "Ten-Gallon Tilly",
        "Deadeye Dolores",
        "Two-Bit Tex",
        "Muleskinner Mabel",
    )

    fun pick(count: Int, random: Random): List<String> = ALL.shuffled(random).take(count)
}

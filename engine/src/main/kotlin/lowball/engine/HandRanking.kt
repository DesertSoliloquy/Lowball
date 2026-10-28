package lowball.engine

/**
 * How bad a hand is, from the lowball point of view. Lower categories win.
 * Straights and flushes don't exist in California Lowball.
 */
enum class Category(val displayName: String) {
    NO_PAIR("No pair"),
    ONE_PAIR("One pair"),
    TWO_PAIR("Two pair"),
    THREE_OF_A_KIND("Three of a kind"),
    FULL_HOUSE("Full house"),
    FOUR_OF_A_KIND("Four of a kind"),
}

/**
 * A ranked five-card hand. A hand that compares LOWER is the BETTER hand.
 *
 * [ranks] are the rank values the hand plays as, with the joker already replaced,
 * ordered for comparison: groups first (biggest group, then highest rank), then single cards high to low.
 */
data class LowHand(val category: Category, val ranks: List<Int>) : Comparable<LowHand> {

    override fun compareTo(other: LowHand): Int {
        if (category != other.category) return category.compareTo(other.category)
        for (i in ranks.indices) {
            if (ranks[i] != other.ranks[i]) return ranks[i].compareTo(other.ranks[i])
        }
        return 0
    }

    /** Players' name for the hand, e.g. "7-5-4-3-2" or "Pair of Aces". */
    fun describe(): String = when (category) {
        Category.NO_PAIR -> when (ranks) {
            listOf(5, 4, 3, 2, 1) -> "The Wheel (5-4-3-2-A)"
            else -> ranks.joinToString("-") { shortName(it) }
        }
        Category.ONE_PAIR -> "Pair of ${plural(ranks[0])}"
        Category.TWO_PAIR -> "Two pair, ${plural(ranks[0])} and ${plural(ranks[1])}"
        Category.THREE_OF_A_KIND -> "Three ${plural(ranks[0])}"
        Category.FULL_HOUSE -> "Full house, ${plural(ranks[0])} over ${plural(ranks[1])}"
        Category.FOUR_OF_A_KIND -> "Four ${plural(ranks[0])}"
    }

    /** The highest card of an unpaired hand, e.g. 7 for a "seven low". 14 for any paired hand. */
    val highCard: Int get() = if (category == Category.NO_PAIR) ranks[0] else 14

    private fun shortName(value: Int) = when (value) {
        1 -> "A"; 10 -> "T"; 11 -> "J"; 12 -> "Q"; 13 -> "K"
        else -> value.toString()
    }

    private fun plural(value: Int) = when (value) {
        6 -> "Sixes"
        else -> Rank.fromValue(value).displayName + "s"
    }
}

object HandEvaluator {

    fun evaluate(cards: List<Card>): LowHand {
        require(cards.size == 5) { "A hand has five cards, got ${cards.size}" }
        val values = resolveJoker(cards)
        val counts = values.groupingBy { it }.eachCount()

        // Groups first (largest group, then highest rank), then single cards high to low.
        val ordered = counts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
            .map { it.key }

        val shape = counts.values.sortedDescending()
        val category = when {
            shape[0] == 4 -> Category.FOUR_OF_A_KIND
            shape[0] == 3 && shape[1] == 2 -> Category.FULL_HOUSE
            shape[0] == 3 -> Category.THREE_OF_A_KIND
            shape[0] == 2 && shape[1] == 2 -> Category.TWO_PAIR
            shape[0] == 2 -> Category.ONE_PAIR
            else -> Category.NO_PAIR
        }
        return LowHand(category, ordered)
    }

    /**
     * Rank values of the hand with the joker counted as the lowest rank not already held
     * ("the bug" in California Lowball).
     */
    fun resolveJoker(cards: List<Card>): List<Int> {
        val natural = cards.filterNot { it.isJoker }.map { it.rank!!.value }
        if (natural.size == cards.size) return natural
        val missing = (1..13).first { it !in natural }
        return natural + missing
    }
}

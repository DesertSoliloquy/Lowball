package lowball.engine

import kotlin.random.Random

/**
 * Knows which picture files exist for each card, and picks one at random.
 *
 * File names follow the project's scheme (without the ".webp" extension):
 *   card_3d_01   three of diamonds, first picture
 *   joker_02     second joker picture
 *   back_07      seventh card back
 */
class CardImages(fileNames: Collection<String>) {
    private val fronts: Map<String, List<String>>
    private val backs: List<String>

    /** File names that don't follow the scheme. Shown to the player so a typo can be fixed. */
    val unrecognized: List<String>

    init {
        val frontMap = mutableMapOf<String, MutableList<String>>()
        val backList = mutableListOf<String>()
        val bad = mutableListOf<String>()
        for (raw in fileNames) {
            val name = raw.substringBeforeLast(".webp")
            when {
                CARD_PATTERN.matches(name) -> frontMap.getOrPut(name.substring(5, 7)) { mutableListOf() }.add(name)
                JOKER_PATTERN.matches(name) -> frontMap.getOrPut(Card.JOKER_CODE) { mutableListOf() }.add(name)
                BACK_PATTERN.matches(name) -> backList.add(name)
                else -> bad.add(raw)
            }
        }
        fronts = frontMap.mapValues { it.value.sorted() }
        backs = backList.sorted()
        unrecognized = bad.sorted()
    }

    /** Cards in the 53-card deck that have no picture at all. */
    val missingCards: List<String>
        get() = Deck.fullDeck().map { it.code }.filter { it !in fronts }

    fun variantCount(card: Card): Int = fronts[card.code]?.size ?: 0

    val backCount: Int get() = backs.size

    /** A random picture for [card], or null if there is none. */
    fun pickFront(card: Card, random: Random): String? = fronts[card.code]?.random(random)

    fun pickBack(random: Random): String? = backs.randomOrNull(random)

    companion object {
        private val CARD_PATTERN = Regex("card_[a2-9tjqk][cdhs]_\\d+")
        private val JOKER_PATTERN = Regex("joker_\\d+")
        private val BACK_PATTERN = Regex("back_\\d+")
    }
}

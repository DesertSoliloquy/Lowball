package lowball.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandRankingTest {

    private fun hand(vararg codes: String) = HandEvaluator.evaluate(codes.map(Card::parse))

    private fun assertBeats(winner: LowHand, loser: LowHand) =
        assertTrue(winner < loser, "${winner.describe()} should beat ${loser.describe()}")

    @Test
    fun wheelIsTheBestHand() {
        val wheel = hand("ac", "2d", "3h", "4s", "5c")
        assertEquals("The Wheel (5-4-3-2-A)", wheel.describe())
        assertBeats(wheel, hand("ac", "2d", "3h", "4s", "6c"))
    }

    @Test
    fun straightsAndFlushesDontCount() {
        val flushWheel = hand("ah", "2h", "3h", "4h", "5h")
        assertEquals(Category.NO_PAIR, flushWheel.category)
        assertEquals(0, flushWheel.compareTo(hand("ac", "2d", "3h", "4s", "5c")))
    }

    @Test
    fun highestCardIsComparedFirst() {
        assertBeats(hand("7c", "5d", "4h", "3s", "2c"), hand("7d", "6c", "4s", "3h", "2d"))
        assertBeats(hand("8c", "7d", "6h", "5s", "4c"), hand("9c", "4d", "3h", "2s", "ac"))
    }

    @Test
    fun anyUnpairedHandBeatsAnyPair() {
        assertBeats(hand("kc", "qd", "jh", "ts", "9c"), hand("ac", "ad", "2h", "3s", "4c"))
    }

    @Test
    fun aPairOfAcesIsTheLowestPair() {
        val aces = hand("ac", "ad", "5h", "6s", "7c")
        val twos = hand("2c", "2d", "3h", "4s", "5c")
        assertEquals("Pair of Aces", aces.describe())
        assertBeats(aces, twos)
    }

    @Test
    fun pairsThenKickers() {
        assertBeats(hand("3c", "3d", "kh", "2s", "ac"), hand("4c", "4d", "2h", "as", "5c"))
        assertBeats(hand("3c", "3d", "8h", "2s", "ac"), hand("3h", "3s", "9h", "2c", "ad"))
    }

    @Test
    fun worseCategoriesLose() {
        val onePair = hand("kc", "kd", "qh", "js", "tc")
        val twoPair = hand("ac", "ad", "2h", "2s", "3c")
        val trips = hand("ac", "ad", "ah", "2s", "3c")
        val fullHouse = hand("ac", "ad", "ah", "2s", "2c")
        val quads = hand("ac", "ad", "ah", "as", "2c")
        assertBeats(onePair, twoPair)
        assertBeats(twoPair, trips)
        assertBeats(trips, fullHouse)
        assertBeats(fullHouse, quads)
    }

    @Test
    fun jokerBecomesTheLowestMissingCard() {
        val withJoker = hand("ac", "2d", "4h", "6s", "joker")
        assertEquals("6-4-3-2-A", withJoker.describe())

        assertEquals("The Wheel (5-4-3-2-A)", hand("2c", "3d", "4h", "5s", "joker").describe())
        assertEquals("K-Q-J-T-A", hand("kc", "qd", "jh", "ts", "joker").describe())
    }

    @Test
    fun jokerCanNotMakeAPair() {
        val h = hand("ac", "2d", "3h", "4s", "joker")
        assertEquals(Category.NO_PAIR, h.category)
        assertEquals("The Wheel (5-4-3-2-A)", h.describe())
    }

    @Test
    fun identicalHandsTie() {
        assertEquals(0, hand("7c", "5d", "4h", "3s", "2c").compareTo(hand("7h", "5s", "4c", "3d", "2h")))
    }
}

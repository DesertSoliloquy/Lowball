package lowball.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardImagesTest {

    @Test
    fun readsTheNamingScheme() {
        val images = CardImages(
            listOf("card_3d_01.webp", "card_3d_02.webp", "card_ac_08.webp", "joker_01.webp", "back_07.webp", "Card 3D.webp"),
        )
        assertEquals(2, images.variantCount(Card.parse("3d")))
        assertEquals(1, images.variantCount(Card.parse("ac")))
        assertEquals(1, images.variantCount(Card.JOKER))
        assertEquals(1, images.backCount)
        assertEquals(listOf("Card 3D.webp"), images.unrecognized)
        assertEquals(50, images.missingCards.size)
    }

    @Test
    fun picksOnlyThatCardsPictures() {
        val images = CardImages(listOf("card_3d_01", "card_3d_02", "card_4d_01"))
        val random = Random(3)
        val picks = List(50) { images.pickFront(Card.parse("3d"), random) }.toSet()
        assertEquals(setOf("card_3d_01", "card_3d_02"), picks)
        assertTrue(images.pickBack(random) == null)
    }
}

package com.openbible.data.model

/**
 * Canonical Red-Letter verse ranges — verses where Jesus Christ is
 * directly speaking. Covers all four Gospels plus Acts 9, Revelation 1–3, 22.
 *
 * Ranges are (bookId, chapter, verseStart, verseEnd) based on standard
 * KJV versification. bookId follows the canonical Protestant ordering
 * (1=Genesis…40=Matthew, 41=Mark, 42=Luke, 43=John, 44=Acts, 66=Revelation).
 *
 * ponytail: covers the undisputed direct-speech passages. OT theophanies,
 * angelic messages quoting Christ, and indirect quotes deferred. Add if
 * users request them.
 */
object RedLetterData {

    /** (bookId, chapter, verseStart, verseEnd) inclusive. */
    private val RANGES: List<Quad> = listOf(
        // ── Matthew ───────────────────────────────────────────
        Quad(40, 4, 4, 4),   // "It is written..."
        Quad(40, 4, 7, 7),
        Quad(40, 4, 10, 10),
        Quad(40, 4, 17, 17),  // "Repent: for the kingdom..."
        Quad(40, 4, 19, 19),  // "Follow me..."
        Quad(40, 5, 3, 48),   // Sermon on the Mount (entire chapter)
        Quad(40, 6, 1, 34),   // Sermon on the Mount (continued)
        Quad(40, 7, 1, 29),   // Sermon on the Mount (continued)
        Quad(40, 8, 3, 4),   // "I will; be thou clean"
        Quad(40, 8, 7, 7),   // "I will come and heal him"
        Quad(40, 8, 10, 13), // "Verily I say unto you..."
        Quad(40, 8, 20, 22), // "Foxes have holes..."
        Quad(40, 8, 24, 26), // "Why are ye fearful...?"
        Quad(40, 8, 28, 32), // Casting out devils
        Quad(40, 9, 2, 2),   // "Son, be of good cheer..."
        Quad(40, 9, 4, 6),   // "Wherefore think ye evil...?"
        Quad(40, 9, 9, 9),   // "Follow me"
        Quad(40, 9, 12, 17), // "They that be whole need not a physician..."
        Quad(40, 9, 22, 22), // "Daughter, be of good comfort..."
        Quad(40, 9, 24, 24), // "Give place: for the maid is not dead..."
        Quad(40, 9, 28, 30), // "Believe ye that I am able...?"
        Quad(40, 9, 37, 38), // "The harvest truly is plenteous..."
        Quad(40, 10, 1, 42), // Commissioning the Twelve
        Quad(40, 11, 1, 30), // "Go and show John..." to "Come unto me..."
        Quad(40, 12, 1, 50), // Lord of the Sabbath, Beelzebub controversy
        Quad(40, 13, 1, 58), // Parables of the Kingdom
        Quad(40, 14, 13, 21), // Feeding the 5000 (teaching discourse)
        Quad(40, 14, 27, 33), // "Be of good cheer; it is I..."
        Quad(40, 15, 1, 20), // Tradition of the elders
        Quad(40, 15, 24, 28), // "I am not sent but unto the lost sheep..."
        Quad(40, 15, 32, 39), // Feeding the 4000
        Quad(40, 16, 2, 4),  // "When it is evening..."
        Quad(40, 16, 6, 28), // "Take heed...", Peter's confession, "Get thee behind me..."
        Quad(40, 17, 1, 27), // Transfiguration, "O faithless generation..."
        Quad(40, 18, 1, 35), // "Who is the greatest...", forgiveness
        Quad(40, 19, 1, 30), // Divorce, rich young ruler
        Quad(40, 20, 1, 34), // Parable of labourers, "Ye know not what ye ask"
        Quad(40, 21, 1, 46), // Triumphal entry, cleansing temple, parables
        Quad(40, 22, 1, 46), // Parables, tribute to Caesar, great commandment
        Quad(40, 23, 1, 39), // Woes to Pharisees
        Quad(40, 24, 1, 51), // Olivet Discourse
        Quad(40, 25, 1, 46), // Ten virgins, talents, sheep and goats
        Quad(40, 26, 1, 75), // Last Supper, Gethsemane, trial (extensive)
        Quad(40, 27, 11, 11), // "Thou sayest" (to Pilate)
        Quad(40, 27, 46, 46), // "My God, my God, why hast thou forsaken me?"
        Quad(40, 28, 9, 10), // "All hail..."
        Quad(40, 28, 18, 20), // Great Commission

        // ── Mark ──────────────────────────────────────────────
        Quad(41, 1, 8, 8),   // "I indeed have baptized you..."
        Quad(41, 1, 15, 15), // "The time is fulfilled..."
        Quad(41, 1, 17, 17), // "Come ye after me..."
        Quad(41, 1, 25, 25), // "Hold thy peace..."
        Quad(41, 1, 38, 44), // "Let us go into the next towns..."
        Quad(41, 2, 5, 11),  // "Son, thy sins be forgiven thee..."
        Quad(41, 2, 17, 28), // "They that are whole have no need..."
        Quad(41, 3, 1, 35),  // Withered hand, choosing the twelve
        Quad(41, 4, 1, 41),  // Parables of the Kingdom
        Quad(41, 5, 1, 43),  // Gadarene demoniac, Jairus's daughter
        Quad(41, 6, 1, 56),  // Teaching at Nazareth, feeding 5000, walking on water
        Quad(41, 7, 1, 37),  // Tradition of the elders
        Quad(41, 8, 1, 38),  // Feeding 4000, leaven, Peter's confession
        Quad(41, 9, 1, 50),  // Transfiguration, "Lord, I believe..."
        Quad(41, 10, 1, 52), // Divorce, rich young ruler, Bartimaeus
        Quad(41, 11, 1, 33), // Triumphal entry, fig tree, cleansing temple
        Quad(41, 12, 1, 44), // Parables, tribute, great commandment, widow's mite
        Quad(41, 13, 1, 37), // Olivet Discourse
        Quad(41, 14, 1, 72), // Last Supper, Gethsemane, trial
        Quad(41, 15, 2, 2),  // "Thou sayest it" (to Pilate)
        Quad(41, 15, 34, 34), // "Eloi, Eloi, lama sabachthani?"
        Quad(41, 16, 15, 18), // Great Commission

        // ── Luke ──────────────────────────────────────────────
        Quad(42, 2, 49, 49), // "Wist ye not that I must be about my Father's business?"
        Quad(42, 4, 1, 13),  // Temptation
        Quad(42, 4, 18, 24), // "The Spirit of the Lord is upon me..."
        Quad(42, 4, 35, 35), // "Hold thy peace..."
        Quad(42, 4, 41, 43), // "I must preach the kingdom..."
        Quad(42, 5, 1, 39),  // Calling disciples, "Fear not..."
        Quad(42, 6, 1, 49),  // Sermon on the Plain
        Quad(42, 7, 1, 50),  // Centurion's servant, widow's son, John's question
        Quad(42, 8, 1, 56),  // Parables, storm, Jairus's daughter
        Quad(42, 9, 1, 62),  // Sending the twelve, feeding 5000, transfiguration
        Quad(42, 10, 1, 42), // Sending the seventy, good Samaritan, Martha & Mary
        Quad(42, 11, 1, 54), // Lord's Prayer, Beelzebub, sign of Jonah
        Quad(42, 12, 1, 59), // Leaven, fear not, rich fool, faithful steward
        Quad(42, 13, 1, 35), // Repent, fig tree, healing, narrow way, Jerusalem
        Quad(42, 14, 1, 35), // Healing on Sabbath, great supper, counting cost
        Quad(42, 15, 1, 32), // Lost sheep, lost coin, prodigal son
        Quad(42, 16, 1, 31), // Unjust steward, rich man and Lazarus
        Quad(42, 17, 1, 37), // Offenses, faith, lepers, coming of the kingdom
        Quad(42, 18, 1, 43), // Importunate widow, Pharisee & publican, children
        Quad(42, 19, 1, 48), // Zacchaeus, parable of pounds, triumphal entry
        Quad(42, 20, 1, 47), // Authority, tribute, resurrection, David's son
        Quad(42, 21, 1, 38), // Widow's mite, Olivet Discourse
        Quad(42, 22, 1, 71), // Last Supper, Gethsemane, trial
        Quad(42, 23, 3, 3),  // "Thy Son" / "Thou sayest"
        Quad(42, 23, 28, 31), // "Daughters of Jerusalem..."
        Quad(42, 23, 34, 34), // "Father, forgive them..."
        Quad(42, 23, 43, 43), // "Today shalt thou be with me in paradise"
        Quad(42, 23, 46, 46), // "Father, into thy hands I commend my spirit"
        Quad(42, 24, 1, 53), // Resurrection appearances, "O fools and slow of heart..."
        Quad(42, 24, 44, 53), // "These are the words which I spake..."

        // ── John ──────────────────────────────────────────────
        Quad(43, 1, 38, 51), // "What seek ye?", "Come and see", Nathanael
        Quad(43, 2, 1, 25),  // "Woman, what have I to do with thee?", cleansing temple
        Quad(43, 3, 1, 21),  // Nicodemus, "Ye must be born again"
        Quad(43, 4, 1, 54),  // Woman at the well, "I that speak unto thee am he"
        Quad(43, 5, 1, 47),  // Healing at Bethesda, "My Father worketh hitherto..."
        Quad(43, 6, 1, 71),  // Feeding 5000, "I am the bread of life"
        Quad(43, 7, 1, 53),  // "My time is not yet come", at the feast
        Quad(43, 8, 1, 59),  // "He that is without sin...", "I am the light of the world"
        Quad(43, 9, 1, 41),  // Healing blind man
        Quad(43, 10, 1, 42), // "I am the door", "I am the good shepherd"
        Quad(43, 11, 1, 57), // Lazarus, "I am the resurrection and the life"
        Quad(43, 12, 1, 50), // "The hour is come", "I, if I be lifted up..."
        Quad(43, 13, 1, 38), // Washing feet, "A new commandment I give unto you"
        Quad(43, 14, 1, 31), // "I am the way, the truth, and the life"
        Quad(43, 15, 1, 27), // "I am the true vine"
        Quad(43, 16, 1, 33), // "These things have I spoken unto you..."
        Quad(43, 17, 1, 26), // High Priestly Prayer
        Quad(43, 18, 1, 40), // Arrest, "Whom seek ye?", before Annas and Caiaphas
        Quad(43, 19, 11, 11), // "Thou couldest have no power at all..."
        Quad(43, 20, 1, 29),  // Resurrection, "Mary", "Peace be unto you"
        Quad(43, 21, 1, 25),  // "Lovest thou me?", "Feed my sheep"

        // ── Acts (Jesus speaking from heaven) ──────────────────
        Quad(44, 9, 4, 6),   // "Saul, Saul, why persecutest thou me?"
        Quad(44, 9, 10, 16), // Vision to Ananias
        Quad(44, 18, 9, 10), // "Be not afraid, but speak..."

        // ── Revelation (Christ speaking to the churches) ───────
        Quad(66, 1, 8, 8),   // "I am Alpha and Omega"
        Quad(66, 1, 11, 20), // "I am the first and the last"
        Quad(66, 2, 1, 29),  // Letters to Ephesus, Smyrna, Pergamos, Thyatira
        Quad(66, 3, 1, 22),  // Letters to Sardis, Philadelphia, Laodicea
        Quad(66, 16, 15, 15), // "Behold, I come as a thief"
        Quad(66, 22, 6, 21), // "Behold, I come quickly"
    )

    /**
     * Check if a verse is a Red-Letter (Jesus speaking) verse.
     *
     * @param bookId Canonical book number (1=Gen…40=Matt…66=Rev)
     * @param chapter Chapter number (1-based)
     * @param verse Verse number (1-based)
     */
    fun isRedLetter(bookId: Int, chapter: Int, verse: Int): Boolean {
        // Fast-path: only Gospels, Acts 9, Revelation 1-3/22
        if (bookId !in 40..44 && bookId != 66) return false
        if (bookId == 44 && chapter != 9 && chapter != 18) return false
        if (bookId == 66 && chapter !in 1..3 && chapter != 16 && chapter != 22) return false

        return RANGES.any { q ->
            q.bookId == bookId && q.chapter == chapter &&
            verse in q.verseStart..q.verseEnd
        }
    }

    /**
     * Get the crimson color for red-letter text.
     */
    fun redLetterColor(isDark: Boolean): Long =
        if (isDark) 0xFFCC3333 else 0xFFB22222 // firebrick

    private data class Quad(
        val bookId: Int,
        val chapter: Int,
        val verseStart: Int,
        val verseEnd: Int
    )
}

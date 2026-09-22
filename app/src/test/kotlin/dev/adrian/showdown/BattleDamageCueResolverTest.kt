package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleDamageCueResolverTest {
    @Test
    fun acceptsUnannotatedDamageForThePendingMoveTarget() {
        assertTrue(
            BattleDamageCueResolver.isDirectMoveDamage(
                "|-damage|p2a: Garchomp|40/100".split('|'),
                listOf("p2a: Garchomp")
            )
        )
    }

    @Test
    fun requiresTheCurrentMoveTargetForAnnotatedDamage() {
        assertTrue(
            BattleDamageCueResolver.isDirectMoveDamage(
                "|-damage|p2a: Garchomp|40/100|[from] move: Seismic Toss".split('|'),
                listOf("p2a: Garchomp")
            )
        )
        assertFalse(
            BattleDamageCueResolver.isDirectMoveDamage(
                "|-damage|p2a: Garchomp|40/100|[from] move: Seismic Toss".split('|'),
                emptyList()
            )
        )
    }

    @Test
    fun rejectsResidualDamageAndUnrelatedPackets() {
        assertFalse(
            BattleDamageCueResolver.isDirectMoveDamage(
                "|-damage|p2a: Garchomp|90/100|[from] brn".split('|'),
                listOf("p2a: Garchomp")
            )
        )
        assertFalse(
            BattleDamageCueResolver.isDirectMoveDamage(
                "|-damage|p2a: Garchomp|90/100".split('|'),
                emptyList()
            )
        )
        assertFalse(
            BattleDamageCueResolver.isDirectMoveDamage(
                "|move|p1a: Pikachu|Thunderbolt|p2a: Garchomp".split('|'),
                listOf("p2a: Garchomp")
            )
        )
    }

    @Test
    fun matchesBroadSpreadMoveTargetsWithoutIgnoringConcreteSlotMismatches() {
        assertEquals(
            listOf("p2b: Garchomp"),
            BattleDamageCueResolver.directDamageTargets(
                "|-damage|p2b: Garchomp|40/100".split('|'),
                listOf("all adjacent foes"),
                mapOf("p2b" to 100f)
            )
        )
        assertTrue(
            BattleDamageCueResolver.directDamageTargets(
                "|-damage|p2b: Garchomp|40/100".split('|'),
                listOf("p2a: Gengar"),
                mapOf("p2b" to 100f)
            ).isEmpty()
        )
    }

    @Test
    fun onlyTreatsSetHpAsDamageWhenTheMoveActuallyReducedHealth() {
        val packet = "|-sethp|p1a: Pikachu|50/100|p2a: Garchomp|100/100".split('|')

        assertEquals(
            listOf("p1a: Pikachu"),
            BattleDamageCueResolver.directDamageTargets(
                packet,
                listOf("p1a: Pikachu", "p2a: Garchomp"),
                mapOf("p1a" to 80f, "p2a" to 40f)
            )
        )
        assertTrue(
            BattleDamageCueResolver.directDamageTargets(
                "|-sethp|p1a: Pikachu|90/100|[from] move: Pain Split".split('|'),
                listOf("p1a: Pikachu"),
                mapOf("p1a" to 100f)
            ).isNotEmpty()
        )
        assertTrue(
            BattleDamageCueResolver.directDamageTargets(
                "|-sethp|p1a: Pikachu|100/100|[from] move: Pain Split".split('|'),
                listOf("p1a: Pikachu"),
                mapOf("p1a" to 40f)
            ).isEmpty()
        )
    }

    @Test
    fun gatesUnannotatedDamageByMoveCategoryAndPower() {
        assertFalse(
            BattleDamageCueResolver.acceptsUnannotatedMoveDamage(
                BattleSession.MoveInfo("—", "—", "Status")
            )
        )
        assertTrue(
            BattleDamageCueResolver.acceptsUnannotatedMoveDamage(
                BattleSession.MoveInfo("80", "100", "Physical")
            )
        )
        assertFalse(BattleDamageCueResolver.acceptsUnannotatedMoveDamage(null))
    }

    @Test
    fun allowsAnnotatedMoveDamageForStatusMovesButRejectsUnannotatedHpChanges() {
        val pendingTargets = listOf("p2a: Garchomp")
        val previousHealth = mapOf("p2a" to 100f)

        assertTrue(
            BattleDamageCueResolver.directDamageTargets(
                "|-damage|p2a: Garchomp|50/100|[from] move: Pain Split".split('|'),
                pendingTargets,
                previousHealth,
                emptyList()
            ).isNotEmpty()
        )
        assertTrue(
            BattleDamageCueResolver.directDamageTargets(
                "|-damage|p2a: Garchomp|50/100".split('|'),
                pendingTargets,
                previousHealth,
                emptyList()
            ).isEmpty()
        )
    }
}

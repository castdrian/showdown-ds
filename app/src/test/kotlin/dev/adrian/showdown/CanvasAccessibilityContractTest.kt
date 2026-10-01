package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasAccessibilityContractTest {
    @Test
    fun treeLookupAndTextSearchReturnVisibleNodeSemantics() {
        val move = CanvasAccessibilityNode(
            id = 12,
            label = "Ice Beam, Ice type, power 90, accuracy 100 percent",
            bounds = CanvasAccessibilityBounds(14, 28, 208, 104),
            selected = true,
            onClick = {}
        )
        val tree = CanvasAccessibilityNodeTree { listOf(move) }

        assertEquals(move, tree.node(12))
        assertEquals(listOf(move), tree.findByText("accuracy 100", CanvasAccessibilityNodeTree.HOST_NODE_ID))
        assertTrue(tree.findByText("earthquake", CanvasAccessibilityNodeTree.HOST_NODE_ID).isEmpty())
        assertEquals(CanvasAccessibilityBounds(14, 28, 208, 104), tree.node(12)?.bounds)
        assertTrue(tree.node(12)?.selected == true)
    }

    @Test
    fun activationRunsOnlyEnabledClickableActions() {
        var activations = 0
        val nodes = listOf(
            CanvasAccessibilityNode(1, "Fight", CanvasAccessibilityBounds(0, 0, 10, 10), onClick = { activations += 1 }),
            CanvasAccessibilityNode(2, "Unavailable", CanvasAccessibilityBounds(10, 0, 20, 10), enabled = false, onClick = { activations += 10 }),
            CanvasAccessibilityNode(3, "Status text", CanvasAccessibilityBounds(20, 0, 30, 10), role = CanvasAccessibilityNode.Role.TEXT)
        )
        val tree = CanvasAccessibilityNodeTree { nodes }

        assertEquals("Fight", tree.activate(1)?.label)
        assertNull(tree.activate(2))
        assertNull(tree.activate(3))
        assertNull(tree.activate(4))
        assertEquals(1, activations)
    }

    @Test
    fun accessibilityAndInputFocusTrackAndClearIndependently() {
        val node = CanvasAccessibilityNode(7, "Pokémon", CanvasAccessibilityBounds(1, 2, 3, 4))
        val tree = CanvasAccessibilityNodeTree { listOf(node) }

        assertEquals(node, tree.requestAccessibilityFocus(7)?.focused)
        assertEquals(node, tree.focusedNode(accessibilityFocus = true))
        assertNull(tree.focusedNode(accessibilityFocus = false))
        assertNull(tree.requestAccessibilityFocus(7))
        assertEquals(node, tree.requestInputFocus(7))
        assertEquals(node, tree.focusedNode(accessibilityFocus = false))
        assertTrue(tree.clearInputFocus(7))
        assertNull(tree.focusedNode(accessibilityFocus = false))
        assertEquals(node, tree.focusedNode(accessibilityFocus = true))
        assertEquals(node, tree.clearAccessibilityFocus(7))
        assertNull(tree.focusedNode(accessibilityFocus = true))
        assertFalse(tree.clearInputFocus(7))
    }

    @Test
    fun focusIsClearedWhenItsVirtualNodeLeavesTheTree() {
        var nodes = listOf(CanvasAccessibilityNode(8, "Replay speed", CanvasAccessibilityBounds(2, 3, 4, 5)))
        val tree = CanvasAccessibilityNodeTree { nodes }
        tree.requestAccessibilityFocus(8)
        tree.requestInputFocus(8)
        nodes = emptyList()

        assertEquals("Replay speed", tree.reconcile(nodes)?.label)
        assertNull(tree.focusedNode(accessibilityFocus = true))
        assertNull(tree.focusedNode(accessibilityFocus = false))
    }
}

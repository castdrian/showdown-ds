package dev.adrian.showdown

import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider

internal data class CanvasAccessibilityBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal fun Rect.toCanvasAccessibilityBounds() = CanvasAccessibilityBounds(left, top, right, bottom)

internal data class CanvasAccessibilityNode(
    val id: Int,
    val label: String,
    val bounds: CanvasAccessibilityBounds,
    val role: Role = Role.BUTTON,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val onClick: (() -> Unit)? = null
) {
    enum class Role {
        BUTTON,
        TEXT
    }
}

internal data class CanvasAccessibilityFocusChange(
    val previous: CanvasAccessibilityNode?,
    val focused: CanvasAccessibilityNode
)

internal class CanvasAccessibilityNodeTree(
    private val provideNodes: () -> List<CanvasAccessibilityNode>
) {
    private var accessibilityFocusedId = HOST_NODE_ID
    private var accessibilityFocusedNode: CanvasAccessibilityNode? = null
    private var inputFocusedId = HOST_NODE_ID

    fun visibleNodes() = provideNodes()

    fun node(id: Int) = visibleNodes().firstOrNull { it.id == id }

    fun findByText(text: String?, virtualViewId: Int): List<CanvasAccessibilityNode> {
        if (text.isNullOrBlank()) return emptyList()
        val searchSpace = if (virtualViewId == HOST_NODE_ID) {
            visibleNodes()
        } else {
            listOfNotNull(node(virtualViewId))
        }
        return searchSpace.filter { it.label.contains(text, ignoreCase = true) }
    }

    fun focusedNode(accessibilityFocus: Boolean): CanvasAccessibilityNode? {
        val id = if (accessibilityFocus) accessibilityFocusedId else inputFocusedId
        return if (id == HOST_NODE_ID) null else node(id)
    }

    fun activate(id: Int): CanvasAccessibilityNode? {
        val target = node(id) ?: return null
        if (!target.enabled || target.onClick == null) return null
        target.onClick.invoke()
        return target
    }

    fun requestAccessibilityFocus(id: Int): CanvasAccessibilityFocusChange? {
        val target = node(id) ?: return null
        if (accessibilityFocusedId == id) return null
        val previous = accessibilityFocusedNode
        accessibilityFocusedId = id
        accessibilityFocusedNode = target
        return CanvasAccessibilityFocusChange(previous, target)
    }

    fun clearAccessibilityFocus(id: Int): CanvasAccessibilityNode? {
        if (accessibilityFocusedId != id) return null
        val previous = accessibilityFocusedNode
        accessibilityFocusedId = HOST_NODE_ID
        accessibilityFocusedNode = null
        return previous
    }

    fun requestInputFocus(id: Int): CanvasAccessibilityNode? {
        val target = node(id) ?: return null
        if (inputFocusedId == id) return null
        inputFocusedId = id
        return target
    }

    fun clearInputFocus(id: Int): Boolean {
        if (inputFocusedId != id) return false
        inputFocusedId = HOST_NODE_ID
        return true
    }

    fun reconcile(current: List<CanvasAccessibilityNode>): CanvasAccessibilityNode? {
        val focusedId = accessibilityFocusedId
        val currentFocused = current.firstOrNull { it.id == focusedId }
        val removedFocus = if (focusedId != HOST_NODE_ID && currentFocused == null) {
            accessibilityFocusedNode
        } else {
            null
        }
        if (focusedId != HOST_NODE_ID) {
            accessibilityFocusedNode = currentFocused
            if (currentFocused == null) accessibilityFocusedId = HOST_NODE_ID
        }
        if (inputFocusedId != HOST_NODE_ID && current.none { it.id == inputFocusedId }) {
            inputFocusedId = HOST_NODE_ID
        }
        return removedFocus
    }

    companion object {
        const val HOST_NODE_ID = -1
    }
}

internal class CanvasAccessibilityNodeProvider(
    private val host: View,
    private val hostDescription: () -> String,
    nodes: () -> List<CanvasAccessibilityNode>
) : AccessibilityNodeProvider() {
    private val tree = CanvasAccessibilityNodeTree(nodes)
    private var lastContentSignature: List<Any>? = null

    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
        if (virtualViewId == CanvasAccessibilityNodeTree.HOST_NODE_ID) {
            return AccessibilityNodeInfo.obtain(host).also(host::onInitializeAccessibilityNodeInfo)
        }
        val node = tree.node(virtualViewId) ?: return null
        return AccessibilityNodeInfo.obtain(host, node.id).apply {
            setParent(host)
            packageName = host.context.packageName
            className = when (node.role) {
                CanvasAccessibilityNode.Role.BUTTON -> "android.widget.Button"
                CanvasAccessibilityNode.Role.TEXT -> "android.widget.TextView"
            }
            if (node.role == CanvasAccessibilityNode.Role.TEXT) {
                text = node.label
            } else {
                contentDescription = node.label
            }
            isEnabled = node.enabled
            isClickable = node.onClick != null
            isFocusable = true
            isFocused = tree.focusedNode(accessibilityFocus = false)?.id == node.id
            isSelected = node.selected
            isVisibleToUser = host.isShown
            isAccessibilityFocused = tree.focusedNode(accessibilityFocus = true)?.id == node.id
            setBoundsInScreen(node.boundsInScreen())
            if (node.onClick != null) addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
            if (isAccessibilityFocused) {
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
            }
            addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_FOCUS)
            if (isFocused) addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_FOCUS)
        }
    }

    override fun findAccessibilityNodeInfosByText(text: String?, virtualViewId: Int): MutableList<AccessibilityNodeInfo> =
        tree.findByText(text, virtualViewId)
            .mapNotNull { createAccessibilityNodeInfo(it.id) }
            .toMutableList()

    override fun findFocus(focus: Int): AccessibilityNodeInfo? {
        val accessibilityFocus = focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY
        if (!accessibilityFocus && focus != AccessibilityNodeInfo.FOCUS_INPUT) return null
        val focused = tree.focusedNode(accessibilityFocus) ?: return null
        return createAccessibilityNodeInfo(focused.id)
    }

    override fun performAction(virtualViewId: Int, action: Int, arguments: android.os.Bundle?): Boolean {
        if (virtualViewId == CanvasAccessibilityNodeTree.HOST_NODE_ID) return host.performAccessibilityAction(action, arguments)
        val node = tree.node(virtualViewId) ?: return false
        return when (action) {
            AccessibilityNodeInfo.ACTION_CLICK -> {
                val activated = tree.activate(node.id) ?: return false
                sendEvent(activated.id, AccessibilityEvent.TYPE_VIEW_CLICKED, activated.label, activated.role)
                refreshIfChanged()
                true
            }
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> requestAccessibilityFocus(node.id)
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> clearAccessibilityFocus(node.id)
            AccessibilityNodeInfo.ACTION_FOCUS -> requestInputFocus(node.id)
            AccessibilityNodeInfo.ACTION_CLEAR_FOCUS -> tree.clearInputFocus(node.id)
            else -> false
        }
    }

    fun addVirtualChildren(info: AccessibilityNodeInfo) {
        tree.visibleNodes().forEach { info.addChild(host, it.id) }
    }

    fun refreshIfChanged() {
        val current = tree.visibleNodes()
        val signature = buildList<Any> {
            add(hostDescription())
            current.forEach { node ->
                add(node.id)
                add(node.label)
                add(node.bounds)
                add(node.role)
                add(node.enabled)
                add(node.selected)
            }
        }
        if (signature == lastContentSignature) return
        lastContentSignature = signature
        if (host.isAttachedToWindow) {
            host.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
        tree.reconcile(current)?.let { node ->
            sendEvent(node.id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED, node.label, node.role)
        }
    }

    private fun requestAccessibilityFocus(id: Int): Boolean {
        val change = tree.requestAccessibilityFocus(id) ?: return false
        change.previous?.let { previous ->
            sendEvent(previous.id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED, previous.label, previous.role)
        }
        val node = change.focused
        sendEvent(node.id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED, node.label, node.role)
        return true
    }

    private fun clearAccessibilityFocus(nodeId: Int): Boolean {
        val node = tree.clearAccessibilityFocus(nodeId) ?: return false
        sendEvent(nodeId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED, node.label, node.role)
        return true
    }

    private fun requestInputFocus(nodeId: Int): Boolean {
        val node = tree.requestInputFocus(nodeId) ?: return false
        sendEvent(node.id, AccessibilityEvent.TYPE_VIEW_FOCUSED, node.label, node.role)
        return true
    }

    private fun sendEvent(nodeId: Int, eventType: Int, label: String, role: CanvasAccessibilityNode.Role) {
        val event = AccessibilityEvent.obtain(eventType).apply {
            packageName = host.context.packageName
            className = when (role) {
                CanvasAccessibilityNode.Role.BUTTON -> "android.widget.Button"
                CanvasAccessibilityNode.Role.TEXT -> "android.widget.TextView"
            }
            contentDescription = label
            setSource(host, nodeId)
            text.add(label)
        }
        val parent = host.parent
        if (parent == null || !parent.requestSendAccessibilityEvent(host, event)) event.recycle()
    }

    private fun CanvasAccessibilityNode.boundsInScreen(): Rect {
        val screenLocation = IntArray(2)
        host.getLocationOnScreen(screenLocation)
        return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom).apply {
            offset(screenLocation[0], screenLocation[1])
        }
    }
}

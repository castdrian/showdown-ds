package dev.adrian.showdown

import android.app.Activity
import android.os.Bundle
import java.util.concurrent.CopyOnWriteArrayList

class ShowdownLogParityHarnessActivity : Activity() {
    val nativeEntries = CopyOnWriteArrayList<Pair<Long, String>>()
    val synchronizedGenerations = CopyOnWriteArrayList<Long>()
    lateinit var renderer: ShowdownMoveEffectsView
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderer = ShowdownMoveEffectsView(
            context = this,
            audioCueListener = {},
            battleLogListener = { value, generation -> nativeEntries += generation to value },
            battleLogSyncListener = synchronizedGenerations::add
        )
        setContentView(renderer)
        renderer.setPerspective("p1")
        renderer.setPlaybackSpeed(BattlePlaybackSpeed.MAXIMUM)
    }

    override fun onDestroy() {
        renderer.release()
        super.onDestroy()
    }
}

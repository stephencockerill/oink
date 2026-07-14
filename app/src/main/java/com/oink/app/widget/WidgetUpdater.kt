package com.oink.app.widget

/**
 * A seam for refreshing the home-screen widget after a data change.
 *
 * The per-habit detail ViewModel is an [androidx.lifecycle.AndroidViewModel] and
 * refreshes the widget directly through [OinkWidget.updateAllWidgets]. The home
 * list and private ViewModels are plain [androidx.lifecycle.ViewModel]s that
 * otherwise need no Application context; injecting this functional interface lets
 * them trigger a widget refresh without dragging Android context into them,
 * keeping them pure-JVM testable. Production wires the real implementation in
 * [com.oink.app.AppContainer]; tests default to a no-op.
 */
fun interface WidgetUpdater {
    suspend fun update()

    companion object {
        /** A [WidgetUpdater] that does nothing; the default for tests. */
        val NoOp: WidgetUpdater = WidgetUpdater { }
    }
}

package io.github.manawear.watch

import android.content.Context
import io.github.manawear.shared.HttpUsageSource
import io.github.manawear.shared.UsageSource

/** The phone relay by default. A URL set through adb switches to direct HTTP. */
fun usageSourceFor(context: Context, config: Config = ConfigStore(context).load()): UsageSource =
    config.url?.let { HttpUsageSource(it, config.token) } ?: DataLayerUsageSource(context)

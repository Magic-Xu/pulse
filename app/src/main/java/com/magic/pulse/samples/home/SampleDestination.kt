package com.magic.pulse.samples.home

enum class SampleDestination(val title: String, val subtitle: String) {
    HOME(title = "Pulse Samples", subtitle = "MVI playground"),
    COUNTER(title = "Counter Demo", subtitle = "State + Intent + Effect"),
    SPLIT_INTENT_BASIC(
        title = "Basic: Split Intent",
        subtitle = "Recommended first step: minimal and explicit",
    ),
    NETWORK(
        title = "Standard: Network Request",
        subtitle = "Repository / DataSource / Service + manual executor",
    ),
    STATE_DECOMPOSITION(
        title = "Advanced: State Decomposition",
        subtitle = "Large state split + reducer DSL + file-level reducers",
    ),
}

package com.magic.pulse.samples.home

enum class SampleDestination(val title: String, val subtitle: String) {
    HOME(title = "Pulse Samples", subtitle = "MVI playground"),
    COUNTER(title = "Counter Demo", subtitle = "State + Intent + Effect"),
    NETWORK(title = "Network Request Demo", subtitle = "Repository / DataSource / Service"),
}

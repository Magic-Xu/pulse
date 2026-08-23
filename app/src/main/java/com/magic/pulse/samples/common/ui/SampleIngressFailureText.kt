package com.magic.pulse.samples.common.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.magic.pulse.R
import com.magic.pulse.samples.common.SampleIngressFailure

@Composable
internal fun SampleIngressFailureText(
    failure: SampleIngressFailure,
    modifier: Modifier = Modifier,
) {
    val message = when (failure) {
        SampleIngressFailure.CAPACITY_REACHED -> R.string.sample_ingress_capacity_reached
        SampleIngressFailure.SCREEN_UNAVAILABLE -> R.string.sample_ingress_screen_unavailable
    }
    Text(
        text = stringResource(message),
        modifier = modifier,
        color = MaterialTheme.colorScheme.error,
    )
}

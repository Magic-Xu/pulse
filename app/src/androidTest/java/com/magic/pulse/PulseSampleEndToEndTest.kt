package com.magic.pulse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PulseSampleEndToEndTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun basicDropWhileRunningTaskCompletesAcrossActivityRecreation() {
        compose.onNodeWithText("Basic - Split Intent").performClick()
        compose.onNodeWithText("requestCount = 0").assertIsDisplayed()

        compose.onNodeWithText("Load Images").performClick()
        compose.activityRule.scenario.recreate()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("- img-basic-1 | Portrait Lite")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("requestCount = 1").assertIsDisplayed()
        compose.onNodeWithText("lastOperation = Image models loaded").assertIsDisplayed()
        compose.onNodeWithText("Load Images").assertIsDisplayed()
    }
}

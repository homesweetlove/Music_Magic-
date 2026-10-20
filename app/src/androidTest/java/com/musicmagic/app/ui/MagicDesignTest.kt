package com.musicmagic.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MagicDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glassNavigationAndActionRemainAccessibleInBothThemes() {
        val dark = mutableStateOf(false)
        val navigations = mutableIntStateOf(0)
        val actions = mutableIntStateOf(0)
        compose.setContent {
            MaterialTheme(
                colorScheme = if (dark.value) darkColorScheme() else lightColorScheme(),
                typography = MagicTypography,
            ) {
                Column {
                    GlassIconButton(
                        icon = Icons.Rounded.Menu,
                        contentDescription = "메뉴 열기",
                        onClick = { navigations.intValue++ },
                    )
                    MagicButton(onClick = { actions.intValue++ }) { Text("변경 저장") }
                }
            }
        }
        compose.onNodeWithContentDescription("메뉴 열기").assertIsDisplayed().performClick()
        compose.onNodeWithText("변경 저장").performClick()
        compose.runOnIdle {
            assertEquals(1, navigations.intValue)
            assertEquals(1, actions.intValue)
            dark.value = true
        }
        compose.onNodeWithContentDescription("메뉴 열기").assertIsDisplayed().performClick()
        compose.onNodeWithText("변경 저장").performClick()
        compose.runOnIdle {
            assertEquals(2, navigations.intValue)
            assertEquals(2, actions.intValue)
        }
    }
}

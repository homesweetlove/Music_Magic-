package com.musicmagic.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.musicmagic.app.library.LibraryMedia
import com.musicmagic.app.library.LibraryTrack
import com.musicmagic.app.AudioInfo
import org.junit.Rule
import org.junit.Test
import java.io.File

class LiquidCarouselTest {
    @get:Rule val compose = createComposeRule()
    private val items by lazy {
        val cover = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply {
            val canvas = android.graphics.Canvas(this)
            canvas.drawColor(android.graphics.Color.rgb(24, 79, 125))
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(230, 164, 96) }
            canvas.drawCircle(80f, 46f, 32f, paint)
        }
        listOf(
            LiquidCardItem("a", "여름의 기록", "Music Magic", "FLAC · 44100 Hz", artwork = cover, actionLabel = "정보 편집"),
            LiquidCardItem("b", "새벽의 산책", "두 번째 앨범", "WAV · 48000 Hz", artwork = cover, actionLabel = "정보 편집"),
        )
    }

    @Test fun documentIdentityAndCardRefreshHandleRenameAndBatchChanges() {
        val picker = Uri.parse("content://media.documents/document/music%3A123")
        val tree = Uri.parse("content://media.documents/tree/music%3A/document/music%3A123")
        val renamed = Uri.parse("content://media.documents/document/music%3A456")
        assertTrue(LibraryMedia.sameDocument(picker, tree))
        assertFalse(LibraryMedia.sameDocument(picker, renamed))
        assertFalse(LibraryMedia.sameDocument(picker, Uri.parse("content://another.documents/document/music%3A123")))
        val original = LibraryTrack(tree, "old.flac", "old", "old artist", "old album", "", "FLAC")
        val updated = LibraryMedia.updated(original, renamed, AudioInfo("new.flac", title = "new", artist = "new artist", album = "new album"))
        assertEquals(renamed, updated.uri)
        assertEquals("new album", updated.album)
        assertEquals("new artist", updated.artist)
        assertEquals("new.flac", updated.fileName)
    }

    @Test fun pickedArtworkUsesBoundedDecodeAndJpegOutput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File.createTempFile("cover-test", ".png", context.cacheDir)
        try {
            val source = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888)
            input.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
            source.recycle()
            val result = LibraryMedia.prepareArtwork(context, Uri.fromFile(input)).getOrThrow()
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(result, 0, result.size, bounds)
            assertEquals("image/jpeg", bounds.outMimeType)
            assertEquals(1000, bounds.outWidth)
            assertTrue(bounds.outHeight <= 1000)
            input.writeText("not an image")
            assertTrue(LibraryMedia.prepareArtwork(context, Uri.fromFile(input)).isFailure)
            input.outputStream().use { out ->
                val chunk = ByteArray(1024 * 1024)
                repeat(12) { out.write(chunk) }
                out.write(0)
            }
            val oversized = LibraryMedia.prepareArtwork(context, Uri.fromFile(input))
            assertTrue(oversized.isFailure)
            assertTrue(oversized.exceptionOrNull()?.message.orEmpty().contains("12MB"))
        } finally { input.delete() }
    }

    @Test fun buttonsAndSwipeNavigateWithoutOpeningEditor() {
        val actions = mutableListOf<String>()
        val settled = mutableListOf<String>()
        compose.setContent { MaterialTheme { LiquidCardCarousel(items, onAction = { actions.add(it.id) }, onSettled = { settled.add(it.id) }) } }
        compose.onNodeWithContentDescription("이전 카드").assertIsNotEnabled()
        assertEquals(emptyList<String>(), settled)
        compose.onNodeWithContentDescription("다음 카드").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("다음 카드").assertIsNotEnabled()
        assertEquals(emptyList<String>(), actions)
        compose.waitUntil(timeoutMillis = 3000) { settled.isNotEmpty() }
        assertEquals(listOf("b"), settled)
        compose.onNodeWithContentDescription("새벽의 산책, 정보 편집").performClick()
        assertEquals(listOf("b"), actions)
        compose.onNodeWithContentDescription("이전 카드").performClick()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("다음 카드").assertIsNotEnabled()
        assertEquals(listOf("b"), actions)
        evidence("library-light")
    }

    @Test fun disabledActionAndShrinkingListStaySafe() {
        val cards = mutableStateOf(items.map { it.copy(enabled = false) })
        compose.setContent { MaterialTheme { LiquidCardCarousel(cards.value, onAction = { error("Disabled action") }) } }
        compose.onNodeWithContentDescription("여름의 기록, 정보 편집").assertIsNotEnabled()
        compose.onNodeWithContentDescription("다음 카드").performClick()
        compose.runOnIdle { cards.value = cards.value.take(1) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("다음 카드").assertIsNotEnabled()
        compose.runOnIdle { cards.value = emptyList() }
        compose.onNodeWithText("표시할 카드가 없습니다").assertIsDisplayed()
    }

    @Test fun comparisonBindsEachActionToItsActualUri() {
        val before = Uri.parse("content://test/source")
        val after = Uri.parse("content://test/result")
        val played = mutableListOf<Uri>()
        compose.setContent {
            MaterialTheme { AudioComparisonCarousel(AudioComparison(before, after, "여름의 기록", "FLAC", "PCM WAV"), false, onPlay = { played.add(it) }) }
        }
        compose.onNodeWithContentDescription("Before · 원본, 원본 재생").performClick()
        compose.onNodeWithContentDescription("다음 카드").performClick()
        compose.onNodeWithContentDescription("After · 변환 결과, 결과 재생").performClick()
        assertEquals(listOf(before, after), played)
        evidence("comparison-light")
    }

    @Test fun largeTextDarkModeKeepsNavigationAvailable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            LiquidCardCarousel(items, onAction = {})
                        }
                    }
                }
            }
        }
        // Large cards are allowed to grow; the host scrolls, rather than clipping text.
        compose.onNodeWithText("여름의 기록").assertExists()
        evidence("large-text-dark")
        compose.onNodeWithContentDescription("다음 카드").performScrollTo().performClick()
        compose.onNodeWithContentDescription("다음 카드").assertIsNotEnabled()
    }

    @Test fun disabledSystemAnimationStillAllowsNavigation() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String): String = automation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText().trim() }
        }
        val previous = shell("settings get global animator_duration_scale")
        try {
            shell("settings put global animator_duration_scale 0")
            compose.setContent { MaterialTheme(colorScheme = lightColorScheme()) { LiquidCardCarousel(items, onAction = {}) } }
            compose.onNodeWithContentDescription("다음 카드").performClick()
            compose.onNodeWithContentDescription("다음 카드").assertIsNotEnabled()
            evidence("reduced-motion")
        } finally {
            if (previous == "null") shell("settings delete global animator_duration_scale")
            else shell("settings put global animator_duration_scale $previous")
        }
    }

    private fun evidence(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-evidence").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

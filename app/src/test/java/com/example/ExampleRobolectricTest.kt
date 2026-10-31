package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.game.engine.SpyGameEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name and spy secret phrase match Arabic specification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val spyPhrase = context.getString(R.string.spy_secret_card_phrase)
        assertEquals("الجاسوس", appName)
        assertEquals(SpyGameEngine.SPY_SECRET_MESSAGE, spyPhrase)
        assertEquals("أنت الجاسوس إعمل نفسك تعرف الكلمة", spyPhrase)
    }
}

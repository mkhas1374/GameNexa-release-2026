package com.example

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.MainActivity
import androidx.compose.ui.test.junit4.createComposeRule
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {
    @Test
    fun testActivityStartup() {
        try {
            val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
            val activity = controller.get()
            println("MAIN ACTIVITY STARTED SUCCESSFULLY")
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            
            val vm = activity.findViewById<android.view.View>(android.R.id.content)
            println("Activity view retrieved: $vm")
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }
}


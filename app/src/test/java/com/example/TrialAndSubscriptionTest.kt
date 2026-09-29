package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.GameNetViewModel
import com.example.ui.SubscriptionActivationScreen
import com.example.ui.UnifiedEntryScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TrialAndSubscriptionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testRenderSubscriptionScreen() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = GameNetViewModel(app)
        println("ViewModel created successfully")
        
        composeTestRule.setContent {
            SubscriptionActivationScreen(viewModel = vm, isEmbedded = false)
        }
        composeTestRule.waitForIdle()
        println("SubscriptionActivationScreen rendered successfully")
    }

    @Test
    fun testActivateFreeTrial() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = GameNetViewModel(app)
        
        var resultMsg = ""
        vm.activateFreeTrial { success, msg ->
            resultMsg = msg
            println("activateFreeTrial result: success=$success, msg=$msg")
        }
        
        Thread.sleep(1000)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        println("Trial activated, final msg: $resultMsg")
    }

    @Test
    fun testClickBuySubscriptionInUnifiedEntryScreen() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = GameNetViewModel(app)
        
        composeTestRule.setContent {
            UnifiedEntryScreen(viewModel = vm)
        }
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithText("خرید اشتراک").performClick()
        composeTestRule.waitForIdle()
        println("Clicked خرید اشتراک successfully!")

        vm.fetchSubscriptionPlans()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        composeTestRule.waitForIdle()
        println("Plans rendered successfully in UnifiedEntryScreen!")
    }

    @Test
    fun testClick24hTrialInUnifiedEntryScreen() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = GameNetViewModel(app)
        
        composeTestRule.setContent {
            UnifiedEntryScreen(viewModel = vm)
        }
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithText("اجرای تست ۲۴ ساعته").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(1500)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        composeTestRule.waitForIdle()
        println("Clicked اجرای تست ۲۴ ساعته successfully!")
    }

    @Test
    fun testAppContentWithTrialMode() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = GameNetViewModel(app)
        
        vm.activateFreeTrial { success, msg ->
            println("activateFreeTrial in testAppContent: success=$success, msg=$msg")
        }
        Thread.sleep(1500)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        composeTestRule.setContent {
            com.example.AppContent(viewModel = vm)
        }
        composeTestRule.waitForIdle()
        println("AppContent rendered with trial mode successfully!")
    }
}

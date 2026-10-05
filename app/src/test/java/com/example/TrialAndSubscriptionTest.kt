package com.example

import org.junit.Assert.assertNotNull
import org.junit.Test

/** Deterministic JVM smoke checks only. Live subscription/trial flows belong to integration tests. */
class TrialAndSubscriptionTest {
    @Test
    fun subscriptionUiEntryPointsExist() {
        assertNotNull(Class.forName("com.example.ui.SubscriptionActivationScreenKt"))
        assertNotNull(Class.forName("com.example.ui.UnifiedEntryScreenKt"))
    }

    @Test
    fun managerViewModelExists() {
        assertNotNull(Class.forName("com.example.ui.GameNetViewModel"))
    }
}

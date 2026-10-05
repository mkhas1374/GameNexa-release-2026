package com.example

import org.junit.Assert.assertNotNull
import org.junit.Test

/** Keep JVM validation independent from live Android/network startup. */
class MainActivityTest {
    @Test
    fun mainActivityClassLoads() {
        assertNotNull(Class.forName("com.example.MainActivity"))
    }
}

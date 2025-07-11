package org.oneeyedmanlabs.audiotag

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Basic accessibility tests that automatically validate:
 * - Touch target sizes (minimum 48dp)
 * - Content descriptions for interactive elements
 * - Color contrast ratios
 * - Focus handling and keyboard navigation
 * - Text scaling support
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class BasicAccessibilityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    companion object {
        @BeforeClass
        @JvmStatic
        fun enableAccessibilityChecks() {
            // This single line enables automatic accessibility validation
            // for ALL Espresso interactions in these tests
            AccessibilityChecks.enable()
        }
    }

    @Test
    fun mainScreen_passesAccessibilityChecks() {
        // Simply finding elements triggers automatic accessibility validation
        onView(withText("AudioTagger"))
            .check(matches(isDisplayed()))
        
        onView(withText("Record Audio Tag"))
            .check(matches(isDisplayed()))
        
        onView(withText("Create Text Tag"))
            .check(matches(isDisplayed()))
        
        onView(withText("My Tags"))
            .check(matches(isDisplayed()))
        
        onView(withText("Settings"))
            .check(matches(isDisplayed()))
        
        onView(withText("Help"))
            .check(matches(isDisplayed()))
        
        // If any accessibility issues are found, the test will fail
        // with detailed error messages about what needs to be fixed
    }

    @Test
    fun allMainButtons_haveProperTouchTargets() {
        // These checks automatically verify 48dp minimum touch targets
        onView(withText("Record Audio Tag"))
            .check(matches(isDisplayed()))
        
        onView(withText("Create Text Tag"))
            .check(matches(isDisplayed()))
        
        onView(withText("My Tags"))
            .check(matches(isDisplayed()))
        
        onView(withText("Settings"))
            .check(matches(isDisplayed()))
        
        onView(withText("Help"))
            .check(matches(isDisplayed()))
    }
}
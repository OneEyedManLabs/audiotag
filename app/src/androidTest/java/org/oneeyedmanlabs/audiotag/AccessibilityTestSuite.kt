package org.oneeyedmanlabs.audiotag

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test suite for running all accessibility tests together
 * 
 * Run this to validate the entire app's accessibility compliance:
 * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.oneeyedmanlabs.audiotag.AccessibilityTestSuite
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    BasicAccessibilityTest::class,
    AccessibilityTest::class
)
class AccessibilityTestSuite
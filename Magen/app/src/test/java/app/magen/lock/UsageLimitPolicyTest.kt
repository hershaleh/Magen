package app.magen.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageLimitPolicyTest {
    @Test fun limitIsNotReachedBeforeThreshold() {
        assertFalse(UsageLimitPolicy.isReached(60, 59 * 60_000L))
    }

    @Test fun limitIsReachedAtThreshold() {
        assertTrue(UsageLimitPolicy.isReached(60, 60 * 60_000L))
    }

    @Test fun missingLimitDoesNotBlock() {
        assertFalse(UsageLimitPolicy.isReached(null, 90 * 60_000L))
    }

    @Test fun zeroMinuteLimitBlocksImmediately() {
        assertTrue(UsageLimitPolicy.isReached(0, 0L))
        assertTrue(UsageLimitPolicy.isReached(0, 1L))
    }
}

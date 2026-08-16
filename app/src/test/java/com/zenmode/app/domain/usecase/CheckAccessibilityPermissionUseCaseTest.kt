package com.zenmode.app.domain.usecase

import com.zenmode.app.testing.FakeAccessibilityPermissionMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckAccessibilityPermissionUseCaseTest {

    private val monitor = FakeAccessibilityPermissionMonitor(enabled = false)
    private val useCase = CheckAccessibilityPermissionUseCase(monitor)

    @Test
    fun `a permission that has never been granted reads as disabled`() = runTest {
        assertFalse(useCase().first())
        assertFalse(useCase.isEnabledNow())
    }

    @Test
    fun `granting the permission is observed`() = runTest {
        monitor.setEnabled(true)

        assertTrue(useCase().first())
        assertTrue(useCase.isEnabledNow())
    }

    @Test
    fun `revoking the permission is observed just as normally`() = runTest {
        monitor.setEnabled(true)
        monitor.setEnabled(false)

        assertFalse(useCase().first())
    }
}

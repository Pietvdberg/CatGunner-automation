package nl.pietvdberg.catgunner.autopilot.domain.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceConnectionRegistryTest {
    @Test
    fun oldUnbindCannotClearANewerService() {
        val registry = ServiceConnectionRegistry<Any>()
        val old = Any()
        val current = Any()
        registry.connect(old)
        registry.connect(current)
        assertFalse(registry.disconnect(old))
        assertSame(current, registry.current)
    }

    @Test
    fun onlyCurrentServiceDisconnectsOnce() {
        val registry = ServiceConnectionRegistry<Any>()
        val current = Any()
        registry.connect(current)
        assertTrue(registry.disconnect(current))
        assertNull(registry.current)
        assertFalse(registry.disconnect(current))
    }
}

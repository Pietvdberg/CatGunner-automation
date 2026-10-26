package nl.pietvdberg.catgunner.autopilot.domain.state

/** Late teardown callbacks from an old service must not clear a newer connection. */
class ServiceConnectionRegistry<T : Any> {
    @get:Synchronized
    var current: T? = null
        private set

    @Synchronized
    fun connect(service: T) {
        current = service
    }

    @Synchronized
    fun disconnect(service: T): Boolean {
        if (current !== service) return false
        current = null
        return true
    }
}

package nl.pietvdberg.catgunner.autopilot.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.pietvdberg.catgunner.autopilot.domain.purchase.GameReading

object ReadingStore {
    private val mutableReading = MutableStateFlow<GameReading?>(null)
    val reading = mutableReading.asStateFlow()
    fun update(reading: GameReading) {
        mutableReading.value = reading
    }
}

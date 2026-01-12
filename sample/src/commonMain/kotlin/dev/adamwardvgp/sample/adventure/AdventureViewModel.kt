package dev.adamwardvgp.sample.adventure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow

interface AdventureViewModel {
    val adventureState: StateFlow<AdventureState>
    fun dispatchEvent(event: AdventureEvent)
}
class AdventureViewModelImpl: ViewModel(), AdventureViewModel {

    private val stateMachine = getAdventureStateMachine(viewModelScope)
    override val adventureState = stateMachine.currentState

    override fun dispatchEvent(event: AdventureEvent) {
        stateMachine.dispatchEvent(event)
    }

}

val monsters = listOf(
    "Cave Troll",
    "Goblin King",
    "Ancient Spider",
    "Slime Beast",
    "Skeleton Knight"
)

fun randomMonster(): String = monsters.random()
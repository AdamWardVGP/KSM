package coffee.adammakes.ksm.ir.model

/** A composite state's embedded child FSM, keyed by the owning state's id in this [Graph]. */
data class CompositeDeclaration(
    val ownerId: String,
    val childGraphKey: String,
    val exitWiring: MutableList<ExitEdge> = mutableListOf(),
)

/** Exit wiring: entering [childStateId] in the child auto-dispatches [parentEventName] here. */
data class ExitEdge(val childStateId: String, val childStateName: String, val parentEventName: String)

data class Graph(
    val name: String,
    val states: MutableSet<String> = mutableSetOf(),
    val edges: MutableList<Edge> = mutableListOf(),
    val effects: MutableMap<String, MutableList<StateEffect>> = mutableMapOf(),
    val stateDeclarations: MutableMap<String, StateDeclaration> = linkedMapOf(),
    val stateNames: MutableMap<String, String> = linkedMapOf(),
    val composites: MutableMap<String, CompositeDeclaration> = linkedMapOf(),
    var initialStateId: String? = null,
) {
    fun declareState(id: String, name: String, parentId: String?) {
        states.add(name)
        stateNames[id] = name
        stateDeclarations[id] = StateDeclaration(id, name, parentId)
    }

    fun referenceState(id: String, name: String) {
        stateNames.putIfAbsent(id, name)
    }

    fun stateName(id: String): String = stateNames[id] ?: id

    fun declareComposite(ownerId: String, childGraphKey: String) {
        composites.getOrPut(ownerId) { CompositeDeclaration(ownerId, childGraphKey) }
    }

    fun addExitWiring(ownerId: String, childStateId: String, childStateName: String, parentEventName: String) {
        composites[ownerId]?.exitWiring?.add(ExitEdge(childStateId, childStateName, parentEventName))
    }
}

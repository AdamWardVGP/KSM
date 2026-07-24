package coffee.adammakes.ksm.ir.model

data class Graph(
    val name: String,
    val states: MutableSet<String> = mutableSetOf(),
    val edges: MutableList<Edge> = mutableListOf(),
    val effects: MutableMap<String, MutableList<StateEffect>> = mutableMapOf(),
    val stateDeclarations: MutableMap<String, StateDeclaration> = linkedMapOf(),
    val stateNames: MutableMap<String, String> = linkedMapOf(),
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
}

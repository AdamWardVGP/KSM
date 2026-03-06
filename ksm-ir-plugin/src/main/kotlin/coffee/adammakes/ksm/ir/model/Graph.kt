package coffee.adammakes.ksm.ir.model

data class Graph(
    val name: String,
    val states: MutableSet<String> = mutableSetOf(),
    val edges: MutableList<Edge> = mutableListOf()
)
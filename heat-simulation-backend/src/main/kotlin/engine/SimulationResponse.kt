package com.example.engine

import kotlinx.serialization.Serializable

@Serializable
data class SimulationResponse(
    val temperature: List<List<Double>>,
    val time: Double
)

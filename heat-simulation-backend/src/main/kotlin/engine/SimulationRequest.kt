package com.example.engine

import kotlinx.serialization.Serializable

@Serializable
data class SimulationRequest(
    val timeSteps: Int,
    val threadCount: Int,
    val tau: Double,
    val dx: Double,
    val lambda: Double,
    val alpha: Double,
    val qLeft: Double,
    val qRight: Double,
    val qTop: Double,
)

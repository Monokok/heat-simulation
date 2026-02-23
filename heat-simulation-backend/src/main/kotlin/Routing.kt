package com.example

import com.example.engine.HeatSolver
import com.example.engine.SimulationRequest
import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }


        post("/simulate"){
            val request = call.receive<SimulationRequest>()

            val solver = HeatSolver(
                request.timeSteps,
                request.threadCount,
                request.tau,
                request.dx,
                request.lambda,
                request.alpha,
                request.qLeft,
                request.qRight,
                request.qTop
            )

            val result = solver.solve()

            call.respond(result)
        }
    }
}

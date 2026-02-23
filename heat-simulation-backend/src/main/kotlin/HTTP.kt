package com.example

import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureHTTP() {
    routing {
        openAPI(path = "openapi", swaggerFile = "documentation.yaml")
        swaggerUI(path = "docs", swaggerFile = "documentation.yaml")
    }
//    routing {
//        swaggerUI(path = "openapi") {
//            info = OpenApiInfo(title = "Heat-simulation API", version = "1.0.0")
//        }
//    }
}

package com.example

import com.example.engine.HeatSolver
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }


    /**
     * Lambda = 0 должно выбрасывать исключение
     */
    @Test
    fun testLambdaZeroException() {
        assertFails {
            HeatSolver(
                timeSteps = 10,
                threadCount = 2,
                tau = 0.001,
                dx = 0.01,
                lambda = 0.0, // ← критический случай
                alpha = 1.0,
                qLeft = 0.0,
                qRight = 0.0,
                qTop = 0.0
            ).solve()
        }
    }

    /**
     * При q = 0 границы должны соответствовать теплоизоляции
     */
    @Test
    fun testBoundaryConditionIsolation() {
        val solver = HeatSolver(
            timeSteps = 1,
            threadCount = 2,
            tau = 0.001,
            dx = 0.1,
            lambda = 1.0,
            alpha = 1.0,
            qLeft = 0.0,
            qRight = 0.0,
            qTop = 0.0
        )

        val response = solver.solve()
        val field = response.temperature

        // Левая граница
        for (y in field.indices) {
            assertEquals(field[y][0], field[y][1], 1e-9)
        }

        // Правая граница
        for (y in field.indices) {
            assertEquals(field[y][field[0].size - 1], field[y][field[0].size - 2], 1e-9)
        }
    }

    /**
     * Проверка детерминированности решения
     */
    @Test
    fun testDeterministicSolution() {
        val solver = HeatSolver(
            timeSteps = 5,
            threadCount = 2,
            tau = 0.001,
            dx = 0.1,
            lambda = 1.0,
            alpha = 1.0,
            qLeft = 0.0,
            qRight = 0.0,
            qTop = 0.0
        )

        val r1 = solver.solve()
        val r2 = solver.solve()

        assertEquals(r1.temperature, r2.temperature)
    }

    /**
     * Проверка автоматической коррекции устойчивости
     */
    @Test
    fun testStabilityCorrection() {
        val solver = HeatSolver(
            timeSteps = 1,
            threadCount = 2,
            tau = 10.0, // заведомо большое значение
            dx = 0.01,
            lambda = 1.0,
            alpha = 5.0,
            qLeft = 0.0,
            qRight = 0.0,
            qTop = 0.0
        )

        val result = solver.solve()

        assertNotNull(result)
    }
}

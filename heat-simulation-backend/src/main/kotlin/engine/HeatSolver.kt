package com.example.engine

import java.util.concurrent.Executors
import java.util.concurrent.Future

class HeatSolver(
    private val timeSteps: Int,
    private val threadCount: Int,
    private var tau: Double,
    private val dx: Double,
    private val lambda: Double,
    private val alpha: Double,
    private val qLeft: Double,
    private val qRight: Double,
    private val qTop: Double,
) {
    fun solve(): SimulationResponse{
        /** Шаг по y */
        val dy = dx
        /** Длина (по Х) */
        val width = 1.0f
        /** Высота (по У) */
        val height = width
        /** Число узлов сетки по x */
        val nx = (width / dx).toInt() + 1
        /** Число узлов сетки по y */
        val ny = (height / dy).toInt() + 1
        require(threadCount <= ny - 2)
        //температурное поле
        var temperature = Array(ny) { DoubleArray(nx) } //Y - строки, X - столбцы
        var temperatureNew = Array(ny) { DoubleArray(nx) } //Y - строки, X - столбцы

        //граничные условия
        applyBoundaryConditions(
            temperatureField = temperature, h = dx, qLeft = qLeft, qRight = qRight, qTop = qTop, lambda = lambda
        )

        var r: Double = alpha * alpha * tau / (dx * dx)
        if (r > 0.25){
            //error("Нарушено условие устойчивости: r = $r")
            tau = 0.25 * dx * dx / (alpha * alpha)
            r = alpha * alpha * tau / (dx * dx)
        }

        val executor =
            Executors.newFixedThreadPool(threadCount) //TODO: вынести вне функции - ибо повторный вызов = утечка thread pool
        val startTime = System.nanoTime()
        //основной расчетный цикл
        for (step in 0 until timeSteps) {
            //* (star projection) - ожидаем что будет любой тип.
            val futures = mutableListOf<Future<*>>()

            //по всем потокам
            for (threadIndex in 0 until threadCount) {
                //Реальная вычисляемая область: 1...ny-2. Вся область: 0...n-1
                val start =
                    threadIndex * (ny - 2) / threadCount + 1 //Это начало блока внутри вычисляемой области (нумерация с 0) + 1 (т.к. в 0 граница)
                val end = minOf( //чтоб не выйти за пределы
                    (threadIndex + 1) //конец блока в вычисляемой области
                            * (ny - 2) / threadCount + 1, ny - 1
                )

                futures.add(
                    executor.submit {
                        for (y in start until end) {
                            for (x in 1 until nx - 1) {

                                temperatureNew[y][x] =
                                    temperature[y][x] + r * (temperature[y + 1][x] + temperature[y - 1][x] + temperature[y][x + 1] + temperature[y][x - 1] - 4.0 * temperature[y][x])
                            }
                        }
                    }

                )
            }

            //собрали
            for (f in futures) {
                f.get() //работает как barrier
            }

            //применяем граничные условия к новому слою
            applyBoundaryConditions(temperatureNew, dx, lambda, qLeft = qLeft, qRight = qRight, qTop = qTop)

            //swap буферов
            val tmp = temperature
            temperature = temperatureNew
            temperatureNew = tmp
        }
        //фиксируем время конца расчета
        val endTime = System.nanoTime()
        //вывод результата
        val executionTimeMs = (endTime - startTime) / 1_000_000.0

        executor.shutdown()

        val temperatureList = temperature.map {
            row -> row.toList()
        }
        return SimulationResponse(
            temperatureList, executionTimeMs
        )
    }

     /** Установка начальных значений в температурном поле.
     * q > 0 - тепло поступает извне
     * q < 0 - отвод тепла наружу
     * q = 0 - теплоизолированность
     * */
    private fun applyBoundaryConditions(
        temperatureField: Array<DoubleArray>,
        h: Double,
        lambda: Double,
        qLeft: Double,
        qRight: Double,
//    qBottom: Double, //
        qTop: Double
    ) {
        if (lambda == 0.0) throw Exception("Lambda mustn't be 0.0")
        val ny = temperatureField.size
        val nx = temperatureField[0].size

        // Начальные условия
        //по Г2 - всей нижней границе (I род) T = 10
        for (x in 0..<nx) {
            temperatureField[0][x] = 10.0
        }

        //по Г4 - всей верхней границе (II род) q = 0
        //U_N = U_(N-1) - h*q/lambda
        for (x in 0..<nx) {
            //по мат формуле: но в таком случае q < 0 мы ПОЛУЧАЕМ, q > 0 ОТДАЕМ тепло
//        temperatureField[ny - 1][x] = temperatureField[ny - 2][x] - (h * qTop) / lambda
            temperatureField[ny - 1][x] = temperatureField[ny - 2][x] + (h * qTop) / lambda

        }

        //по Г1 - всей левой границе (II род)
        //U_1 = U_2 + h*q/lambda
        for (y in 0..<ny) {
            temperatureField[y][0] = temperatureField[y][1] + (h * qLeft) / lambda
        }

        //по Г3 - всей правой границе
        //U_N = U_(N-1) - h*q/lambda
        for (y in 0..<ny) {
            //по мат формуле: но в таком случае q < 0 мы ПОЛУЧАЕМ, q > 0 ОТДАЕМ тепло
//        temperatureField[y][nx - 1] = temperatureField[y][nx - 2] - (h * qRight) / lambda
            temperatureField[y][nx - 1] = temperatureField[y][nx - 2] + (h * qRight) / lambda

            //чтоб унифицировано:
//        temperatureField[y][nx - 2] + (h * qRight) / lambda
        }
    }
}
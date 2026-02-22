import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Точка входа в приложение
 */
fun main() {


    val time = readDoubleFromConsole("Укажите количество шагов по времени")//5_000_000
    val tau = readDoubleFromConsole("Укажите шаг по времени")//5_000_000
    print("Далее укажите значение температур для границ. Учтите: q > 0 - получение тепла извне, q < 0 - отдача тепла наружу, q = 0 - теплоизолированность\n")
    val qLeft = readDoubleFromConsole("Введите qLeft")
    val qRight = readDoubleFromConsole("Введите qRight")
    val qTop = readDoubleFromConsole("Введите qTop")
//    val qBottom = readDoubleFromConsole("Введите qBottom")
    //sequenceSolve(time.toInt(), tau = tau, qLeft = qLeft, qRight = qRight, qTop = qTop)

    val threadCount: Int = readDoubleFromConsole("Укажите количество потоков threadCount").roundToInt()

    parallelSolve(
        time.toInt(), threadCount, tau, qLeft, qRight, qTop
    )
}

/**
 * Вариант №7 - Золото
 * lambda = 0.3,    5
 * Г1 II род условия, alpha = 3.99, q = 0
 * Г2 I T = 10
 * Г3 II q = 0
 * Г4 II q = 0
 * шаг по х = щаг по у = h = 0.01
 */


/** Теплопроводность */
const val lambda = 0.35

/** Шаг по х */
const val dx = 0.01

/** Шаг по y */
const val dy = 0.01

/** Температуропроводность */
const val alpha = 3.99

/** Значение q для границы 2-го рода */
const val q = 0


fun parallelSolve(timeSteps: Int = 10, threadCount: Int, tau: Double, qLeft: Double, qRight: Double, qTop: Double) {
    /** Длина (по Х) */
    val width: Float = 1.0f

    /** Высота (по У) */
    val height: Float = width

    // Число узлов сетки
    val nx = (width / dx).toInt() + 1
    val ny = (height / dy).toInt() + 1
    require(threadCount <= ny - 2)

    //температурное поле
    var temperature = Array(ny) { DoubleArray(nx) } //Y - строки, X - столбцы
    var temperatureNew = Array(ny) { DoubleArray(nx) } //Y - строки, X - столбцы

    //граничные условия
    applyBoundaryConditions(
        temperatureField = temperature,
        h = dx,
        qLeft = qLeft,
        qRight = qRight,
        qTop = qTop,
        lambda = lambda
    )
    val startTime = System.nanoTime()
    var currentTime = 0.0
    var r: Double = alpha * alpha * tau / (dx * dx)
    if (r >= 0.25) r = 0.2// error("Нарушено условие устойчивости: r = $r")

    //размер секции, обрабатываемой одним thread'ом
    val blockSize = ny / threadCount
    println("ThreadCount = $threadCount")
    println("ny = $ny")
    println("BlockSize = ${ny / threadCount}")

    val executor = Executors.newFixedThreadPool(threadCount) //TODO: вынести вне функции - ибо повторный вызов = утечка thread pool

    //основной расчетный цикл
    for (step in 0 until timeSteps) {
        //* (star projection) - ожидаем что будет любой тип.
        val futures = mutableListOf<Future<*>>()

        //по всем потокам
        for (threadIndex in 0 until threadCount) {
            val start = threadIndex * (ny - 2) / threadCount + 1
            val end = minOf((threadIndex + 1) * (ny - 2) / threadCount + 1, ny - 1)
//            val start = threadIndex * blockSize + 1
//            //val end = minOf((threadIndex + 1) * blockSize, ny - 1)
//            val end = minOf((threadIndex + 1) * ny / threadCount, ny - 1)

            futures.add(
                executor.submit {
                    for (y in start until end) {
                        for (x in 1 until nx - 1) {

                            temperatureNew[y][x] =
                                temperature[y][x] +
                                        r * (
                                        temperature[y + 1][x] +
                                                temperature[y - 1][x] +
                                                temperature[y][x + 1] +
                                                temperature[y][x - 1] -
                                                4.0 * temperature[y][x]
                                        )
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

        if ((step < 1000 && step % 100 == 0) || step % 1000 == 0) {//step > timeSteps/2 &&

            clearConsole()

            println("Step: $step")

            val field = temperature

            val minT = field.minOf { row -> row.min() }
            val maxT = field.maxOf { row -> row.max() }

            for (y in field.size - 1 downTo 0) {//for (y in field.indices) {
                for (x in field[y].indices) {

                    val value = field[y][x]

                    val (r, g, b) = heatmapColor(value, minT, maxT)

                    print("\u001B[48;2;${r};${g};${b}m  ")
                }
                println()
            }

            print("\u001B[0m")

            Thread.sleep(1000)
        }

        currentTime += tau
    }
    //фиксируем время конца расчета
    val endTime = System.nanoTime()
    //вывод результата
    val executionTimeMs = (endTime - startTime) / 1_000_000.0
    println("Time to solve: $executionTimeMs Ms")
    saveFieldToFile(temperature, "temperature_result.txt")
    println("Нажмите Enter для продолжения...")
    readLine()

    executor.shutdown()
}


fun sequenceSolve(timeSteps: Int = 10, tau: Double, qLeft: Double, qRight: Double, qTop: Double) {
    /** Длина (по Х) */
    val width: Float = 1.0f

    /** Высота (по У) */
    val height: Float = width

    // Число узлов сетки
    val nx = (width / dx).toInt() + 1
    val ny = (height / dy).toInt() + 1

    //температурное поле
    var temperature = Array(ny) { DoubleArray(nx) } //Y - строки, X - столбцы
    var temperatureNew = Array(ny) { DoubleArray(nx) } //Y - строки, X - столбцы

    //граничные условия
    applyBoundaryConditions(
        temperatureField = temperature,
        h = dx,
        qLeft = qLeft,
        qRight = qRight,
        qTop = qTop,
        lambda = lambda
    )
//    printField(temperature)


//    Thread.sleep(1000)
    //фиксируем время начала расчета
    val startTime = System.nanoTime()
    var currentTime = 0.0
    var r: Double = alpha * alpha * tau / (dx * dx)
    if (r >= 0.25) r = 0.2// error("Нарушено условие устойчивости: r = $r")
    //основной расчетный цикл
    for (step in 0 until timeSteps) {

        for (y in 1..<ny - 1) {
            for (x in 1..<nx - 1) {

                temperatureNew[y][x] =
                    temperature[y][x] +
                            r * (
                            temperature[y + 1][x] +
                                    temperature[y - 1][x] +
                                    temperature[y][x + 1] +
                                    temperature[y][x - 1] -
                                    4.0 * temperature[y][x]
                            )
            }
        }

        //применяем граничные условия к новому слою
        applyBoundaryConditions(temperatureNew, dx, lambda, qLeft = qLeft, qRight = qRight, qTop = qTop)
//        if (step % 500 == 0) {
//            clearConsole()
//            println("Step: $step")
////            printFieldColored(temperature)
//            val (r, g, b) = heatmapColor(value, minT, maxT)
//            print("\u001B[48;2;${r};${g};${b}m  ")
//            Thread.sleep(100)   // чтобы глаз успевал видеть
//        }
        if ((step < 1000 && step % 100 == 0) || step % 1000 == 0) {//step > timeSteps/2 &&

            clearConsole()

            println("Step: $step")

            val field = temperature

            val minT = field.minOf { row -> row.min() }
            val maxT = field.maxOf { row -> row.max() }

            for (y in field.size - 1 downTo 0) {//for (y in field.indices) {
                for (x in field[y].indices) {

                    val value = field[y][x]

                    val (r, g, b) = heatmapColor(value, minT, maxT)

                    print("\u001B[48;2;${r};${g};${b}m  ")
                }
                println()
            }

            print("\u001B[0m")

            Thread.sleep(1000)
        }
        //swap буферов
        val tmp = temperature
        temperature = temperatureNew
        temperatureNew = tmp
        currentTime += tau
    }
    //фиксируем время конца расчета
    val endTime = System.nanoTime()
    //вывод результата
    val executionTimeMs = (endTime - startTime) / 1_000_000.0
    println("Time to solve: $executionTimeMs Ms")
    saveFieldToFile(temperature, "temperature_result.txt")
    println("Нажмите Enter для продолжения...")
    readLine()
}

/** Установка начальных значений в температурном поле.
 * q > 0 - тепло поступает извне
 * q < 0 - отвод тепла наружу
 * q = 0 - теплоизолированность
 * */
fun applyBoundaryConditions(
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

fun printField(field: Array<DoubleArray>) {
    for (row in field) {
        println(row.joinToString(" ") {
            if (it.isNaN()) "NaN"
            else String.format("%.3f", it)
        })
    }
}

fun saveFieldToFile(field: Array<DoubleArray>, fileName: String) {
    val file = File(fileName)
    file.printWriter().use { out ->
        for (row in field) {
            out.println(row.joinToString(" ") { "%.6f".format(it) })
        }
    }
}

/** Функция печати цветного температурного поля */
fun printFieldColored(field: Array<DoubleArray>) {

    val ny = field.size
    val nx = field[0].size

    val minT = field.minOf { row -> row.min() }
    val maxT = field.maxOf { row -> row.max() }

    for (y in 0 until ny) {
        for (x in 0 until nx) {

            val value = field[y][x]

            // Нормировка в [0..1]
            val normalized =
                if (maxT - minT == 0.0) 0.0
                else (value - minT) / (maxT - minT)

            // Перевод в цвет 256-спектра
            val color = (16 + normalized * 215).toInt()

            print("\u001B[38;5;${color}m██")
        }
        println()
    }

    // Сброс цвета
    println("\u001B[0m")
}

fun clearConsole() {
    print("\u001B[H\u001B[2J")
    System.out.flush()
}

fun heatmapColor(value: Double, min: Double, max: Double): Triple<Int, Int, Int> {

    val normalized =
        if (max - min == 0.0) 0.0
        else (value - min) / (max - min)

    return when {

        normalized <= 0.5 -> {
            val t = normalized / 0.5

            val r = (t * 255).toInt()
            val g = (t * 200).toInt()
            val b = (180 + (1 - t) * 75).toInt()

            Triple(r, g, b)
        }

        else -> {
            val t = (normalized - 0.5) / 0.5

            val r = (255 - t * 35).toInt()
            val g = (200 - t * 200).toInt()
            val b = (0 + (1 - t) * 0).toInt()

            Triple(r, g, b)
        }
    }
}

fun readDoubleFromConsole(prompt: String): Double {
    while (true) {
        print("$prompt: ")

        val input = readLine()

        try {
            return input?.toDouble() ?: 0.0
        } catch (e: Exception) {
            println("Введите числовое значение!")
        }
    }
}
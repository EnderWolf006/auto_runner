package com.example.auto_runner

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.MockLocationProvider
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask
import kotlin.math.*
import kotlin.random.Random

class MethodChannel(messenger: BinaryMessenger, private val ctx: MainActivity) :
    MethodChannel.MethodCallHandler {

    private var channel: MethodChannel = MethodChannel(messenger, "Flutter.MethodChannel")

    private var state: Boolean? = false
    private var routeJson: String = "{}"

    private var speed: Double? = null
    private var randomOffset: Double? = null
    private var updateFrequency: Double? = null
    private var cadence: Double? = null
    private var timer: Timer? = null

    // 变速相关变量（±50%范围）
    private var currentSpeedFactor = 1.0
    private var targetSpeedFactor = 1.0
    private var lastSpeedChangeTime = 0L
    private var speedChangeDuration = 0L
    private var nextSpeedChangeInterval = 0L

    // 新增距离跟踪变量
    private var lastTickTime = 0L
    private var accumulatedDistance = 0.0
    private var accumulatedSteps = 0.0  // 累积步数（使用 Double 以精确累加）

    private data class Node(val longitude: Double, val latitude: Double)

    private var routeNodes: List<Node> = emptyList()
    private var segmentDistances: MutableList<Double> = mutableListOf()
    private var totalDistance: Double = 0.0
    private var startTime: Long = 0L
    private var isMoving: Boolean = false

    init {
        channel.setMethodCallHandler(this)
        initSpeedVariation()
    }

    private fun initSpeedVariation() {
        currentSpeedFactor = 1.0
        targetSpeedFactor = 1.0
        lastSpeedChangeTime = System.currentTimeMillis()
        nextSpeedChangeInterval = Random.nextLong(30_000L, 120_000L)
        speedChangeDuration = Random.nextLong(5_000L, 15_000L)
    }

    private fun start() {
        routeNodes = parseRouteJson(routeJson).filterConsecutiveDuplicates()
        if (routeNodes.size < 2) return

        calculateSegmentDistances()

        if (speed == null || speed!! <= 0.0) return

        initSpeedVariation()
        startTime = System.currentTimeMillis()
        lastTickTime = startTime
        accumulatedDistance = 0.0
        accumulatedSteps = 0.0
        isMoving = true
    }

    private fun parseRouteJson(json: String): List<Node> {
        val nodes = mutableListOf<Node>()
        try {
            val jsonObject = JSONObject(json)
            val nodesArray = jsonObject.getJSONArray("nodes")
            for (i in 0 until nodesArray.length()) {
                val nodeObj = nodesArray.getJSONObject(i)
                nodes.add(Node(nodeObj.getDouble("longitude"), nodeObj.getDouble("latitude")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return nodes
    }

    private fun List<Node>.filterConsecutiveDuplicates(): List<Node> {
        val filtered = mutableListOf<Node>()
        var prev: Node? = null
        for (node in this) {
            if (prev == null || node.longitude != prev.longitude || node.latitude != prev.latitude) {
                filtered.add(node)
                prev = node
            }
        }
        return filtered
    }

    private fun calculateSegmentDistances() {
        segmentDistances.clear()
        totalDistance = 0.0
        for (i in 0 until routeNodes.size - 1) {
            val nodeA = routeNodes[i]
            val nodeB = routeNodes[i + 1]
            val distance = haversine(nodeA.longitude, nodeA.latitude, nodeB.longitude, nodeB.latitude)
            totalDistance += distance
            segmentDistances.add(totalDistance)
        }
    }

    private fun haversine(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
        val R = 6371e3
        val φ1 = lat1.toRadians()
        val φ2 = lat2.toRadians()
        val Δφ = (lat2 - lat1).toRadians()
        val Δλ = (lon2 - lon1).toRadians()

        val a = sin(Δφ / 2).pow(2) + cos(φ1) * cos(φ2) * sin(Δλ / 2).pow(2)
        return 2 * atan2(sqrt(a), sqrt(1 - a)) * R
    }

    private fun Double.toRadians() = Math.toRadians(this)

    private fun tick() {
        if (!isMoving || speed == null || speed!! <= 0.0 || routeNodes.size < 2) return

        val currentTime = System.currentTimeMillis()
        val timeDelta = (currentTime - lastTickTime) / 1000.0
        lastTickTime = currentTime

        updateSpeedFactor(currentTime)

        val deltaDistance = speed!! * currentSpeedFactor * timeDelta
        accumulatedDistance += deltaDistance

        if (accumulatedDistance >= totalDistance) {
            routeNodes.lastOrNull()?.let { setLocation(it.longitude, it.latitude) }
            isMoving = false
            return
        }

        val segmentIndex = findCurrentSegment(accumulatedDistance)
        if (segmentIndex == -1) return

        val (currentLon, currentLat) = calculatePosition(segmentIndex, accumulatedDistance)
        setLocation(currentLon, currentLat)
    }

    private fun updateSpeedFactor(currentTime: Long) {
        if (currentTime - lastSpeedChangeTime > nextSpeedChangeInterval) {
            if (Random.nextDouble() > 0.4872){
                targetSpeedFactor = 1 + Random.nextDouble() * 1.1 // +110% - 1
                nextSpeedChangeInterval = Random.nextLong(10_000L, 35_000L)
            }else{
                targetSpeedFactor = 0.6 + Random.nextDouble() * 0.4 // -40% - 1
                nextSpeedChangeInterval = Random.nextLong(30_000L, 100_000L)
            }

            speedChangeDuration = Random.nextLong(5_000L, 15_000L)
            lastSpeedChangeTime = currentTime
        }

        val progress = (currentTime - lastSpeedChangeTime).toDouble() / speedChangeDuration

        currentSpeedFactor = when {
            progress < 0 -> targetSpeedFactor
            progress < 1.0 -> lerp(currentSpeedFactor, targetSpeedFactor, easeInOutQuad(progress.coerceIn(0.0, 1.0)))
            else -> targetSpeedFactor
        }.coerceIn(0.6, 2.1)
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun easeInOutQuad(t: Double): Double {
        return if (t < 0.5) 2 * t * t else -1 + (4 - 2 * t) * t
    }

    private fun findCurrentSegment(distance: Double): Int {
        return segmentDistances.binarySearch(distance).let { if (it >= 0) it else -it - 1 }.takeIf { it < segmentDistances.size } ?: -1
    }

    private fun calculatePosition(segmentIndex: Int, distance: Double): Pair<Double, Double> {
        val prevDistance = if (segmentIndex == 0) 0.0 else segmentDistances[segmentIndex - 1]
        val segmentStart = routeNodes[segmentIndex]
        val segmentEnd = routeNodes[segmentIndex + 1]
        val ratio = (distance - prevDistance) / (segmentDistances[segmentIndex] - prevDistance)

        return Pair(
            interpolate(segmentStart.longitude, segmentEnd.longitude, ratio),
            interpolate(segmentStart.latitude, segmentEnd.latitude, ratio)
        )
    }

    private fun interpolate(start: Double, end: Double, ratio: Double) = start + (end - start) * ratio

    @SuppressLint("WorldReadableFiles")
    private fun setLocation(longitude: Double, latitude: Double) {
        val finalLon = Random.nextDouble(-1.0, 1.0) * (randomOffset!! * 0.00001141) + longitude
        val finalLat = Random.nextDouble(-1.0, 1.0) * (randomOffset!! * 0.00000899) + latitude
        MockLocationProvider.pushLocation(finalLat, finalLon)

        // 累积步数增量，增速跟随 currentSpeedFactor 波动
        val currentTime = System.currentTimeMillis()
        val timeDelta = (currentTime - lastTickTime) / 1000.0
        val stepIncrement = cadence!! * currentSpeedFactor * timeDelta
        accumulatedSteps += stepIncrement
        
        val step = accumulatedSteps.roundToInt()
        ctx.getSharedPreferences("auto_runner", Context.MODE_WORLD_READABLE).edit().apply {
            putInt("step", step)
            commit()
        }
    }

    private fun resetTimer() {
        timer?.cancel()
        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() { tick() }
            }, 0, (updateFrequency!! * 1000).toLong())
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "toggleChanged" -> {
                state = call.argument("state")
                routeJson = call.argument("routeJson") ?: "{}"
                speed = call.argument("speed")
                randomOffset = call.argument("randomOffset")
                updateFrequency = call.argument("updateFrequency")
                cadence = call.argument("cadence")

                if (state == true) {
                    resetTimer()
                    start()
                } else {
                    timer?.cancel()
                    isMoving = false
                }
                result.success(mapOf("suc" to true))
            }
            "getInfo" -> {
                val sp = ctx.getSharedPreferences("auto_runner", Context.MODE_WORLD_READABLE)
                result.success(mapOf(
                    "isMoving" to isMoving,
                    "step" to sp.getInt("step", 0),
                    "speed" to speed,
                    "cadence" to cadence,
                    "totalDistance" to totalDistance,
                    "distanceTravelled" to accumulatedDistance,
                    "startTime" to startTime,
                    "currentSpeedFactor" to currentSpeedFactor
                ))
            }
        }
    }

    fun cleanup() {
        timer?.cancel()
        timer = null
    }
}
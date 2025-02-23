package com.example.auto_runner

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.MockLocationProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.util.Objects
import java.util.Timer
import java.util.TimerTask
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


/**
 * des:
 */
class MethodChannel(messenger: BinaryMessenger, private val ctx: MainActivity) :
    MethodChannel.MethodCallHandler {

    private var channel: MethodChannel = MethodChannel(messenger, "Flutter.MethodChannel")

    private var state: Boolean? = false
    private var routeJson: String = "{}"

    private var speed: Double? = null // m/s
    private var randomOffset: Double? = null // m
    private var updateFrequency: Double? = null
    private var cadence: Double? = null
    private var timer: Timer? = null

    private data class Node(val longitude: Double, val latitude: Double)

    private var routeNodes: List<Node> = emptyList()
    private var segmentDistances: MutableList<Double> = mutableListOf()
    private var totalDistance: Double = 0.0
    private var startTime: Long = 0L
    private var isMoving: Boolean = false
    private var distanceTravelled: Double = 0.0

    init {
        channel.setMethodCallHandler(this)
    }

    private fun start() {
        // 解析路线节点
        routeNodes = parseRouteJson(routeJson).filterConsecutiveDuplicates()
        if (routeNodes.size < 2) return

        // 计算各段距离和总长度
        calculateSegmentDistances()

        if (speed == null || speed!! <= 0.0) return
        startTime = System.currentTimeMillis()
        isMoving = true
    }

    private fun parseRouteJson(json: String): List<Node> {
        val nodes = mutableListOf<Node>()
        try {
            val jsonObject = JSONObject(json)
            val nodesArray = jsonObject.getJSONArray("nodes")
            for (i in 0 until nodesArray.length()) {
                val nodeObj = nodesArray.getJSONObject(i)
                nodes.add(
                    Node(
                        nodeObj.getDouble("longitude"),
                        nodeObj.getDouble("latitude")
                    )
                )
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
            val distance =
                haversine(nodeA.longitude, nodeA.latitude, nodeB.longitude, nodeB.latitude)
            totalDistance += distance
            segmentDistances.add(totalDistance)
        }
    }

    private fun haversine(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
        val R = 6371e3 // Earth radius in meters
        val φ1 = lat1.toRadians()
        val φ2 = lat2.toRadians()
        val Δφ = (lat2 - lat1).toRadians()
        val Δλ = (lon2 - lon1).toRadians()

        val a = sin(Δφ / 2) * sin(Δφ / 2) +
                cos(φ1) * cos(φ2) *
                sin(Δλ / 2) * sin(Δλ / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun Double.toRadians() = Math.toRadians(this)

    private fun tick() {
        if (!isMoving || speed == null || speed!! <= 0.0 || routeNodes.size < 2) return

        val currentTime = System.currentTimeMillis()
        val elapsedSeconds = (currentTime - startTime) / 1000.0
        distanceTravelled = speed!! * elapsedSeconds

        if (distanceTravelled >= totalDistance) {
            // 到达终点
            routeNodes.lastOrNull()?.let {
                setLocation(it.longitude, it.latitude)
            }
            isMoving = false
            return
        }

        // 查找当前所在的线段
        val segmentIndex = findCurrentSegment(distanceTravelled)
        if (segmentIndex == -1) return

        val (currentLon, currentLat) = calculatePosition(segmentIndex, distanceTravelled)
        setLocation(currentLon, currentLat)
    }

    private fun findCurrentSegment(distanceTravelled: Double): Int {
        val index = segmentDistances.binarySearch(distanceTravelled).let {
            if (it >= 0) it else -it - 1
        }
        return if (index < segmentDistances.size) index else -1
    }

    private fun calculatePosition(
        segmentIndex: Int,
        distanceTravelled: Double
    ): Pair<Double, Double> {
        val prevDistance = if (segmentIndex == 0) 0.0 else segmentDistances[segmentIndex - 1]
        val segmentStartNode = routeNodes[segmentIndex]
        val segmentEndNode = routeNodes[segmentIndex + 1]

        val segmentLength = segmentDistances[segmentIndex] - prevDistance
        val ratio = (distanceTravelled - prevDistance) / segmentLength

        return Pair(
            interpolate(segmentStartNode.longitude, segmentEndNode.longitude, ratio),
            interpolate(segmentStartNode.latitude, segmentEndNode.latitude, ratio)
        )
    }

    private fun interpolate(start: Double, end: Double, ratio: Double) =
        start + (end - start) * ratio

    @SuppressLint("WorldReadableFiles")
    private fun setLocation(longitude: Double, latitude: Double) {
        val finalLongitude =
            Math.random() * (randomOffset!! * 0.00001141) * 2 - (randomOffset!! * 0.00001141) + longitude
        val finalLatitude =
            Math.random() * (randomOffset!! * 0.00000899) * 2 - (randomOffset!! * 0.00000899) + latitude
        MockLocationProvider.pushLocation(finalLatitude, finalLongitude)
        val step = ((System.currentTimeMillis() - startTime) / 1000).toInt() * cadence!!.toInt()
        val sp: SharedPreferences =
            ctx.getSharedPreferences("auto_runner", Context.MODE_WORLD_READABLE)
        val editor: SharedPreferences.Editor = sp.edit()
        editor.putInt("step", step)
        val suc = editor.commit()

        Log.i("xposed", "setLocation out: step = $step suc=$suc")

    }

    private fun finnish() {
        segmentDistances = mutableListOf()
        totalDistance = 0.0
        startTime = 0L
        isMoving = false
    }

    private fun resetTimer() {
        if (timer == null) {
            timer = Timer()
        }
        if (timer!!.purge() > 0) {
            timer!!.cancel()
        }
        timer = Timer()
        val timerTask = object : TimerTask() {
            override fun run() {
                tick()
            }
        }
        timer!!.schedule(timerTask, 0, (updateFrequency!! * 1000).toLong())
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        Log.i("xposed", "onMethodCall: " + call.method)
        if (call.method == "toggleChanged") {
            state = call.argument("state") as Boolean?
            routeJson = (call.argument("routeJson") as String?)!!
            speed = call.argument("speed") as Double?
            randomOffset = call.argument("randomOffset") as Double?
            updateFrequency = call.argument("updateFrequency")!!
            cadence = call.argument("cadence")!!
            if (state == true) {
                resetTimer()
                finnish()
                start()
            } else {
                if (timer != null) {
                    timer!!.cancel()
                }
                finnish()
            }
            val map = mapOf(
                "suc" to true,
            )
            result.success(map)
        } else if (call.method == "getInfo") {
            val sp: SharedPreferences =
                ctx.getSharedPreferences("auto_runner", Context.MODE_WORLD_READABLE)
            val step = sp.getInt("step", 0)
            val map = mapOf(
                "isMoving" to isMoving,
                "step" to step,
                "speed" to speed,
                "cadence" to cadence,
                "totalDistance" to totalDistance,
                "distanceTravelled" to distanceTravelled,
                "startTime" to startTime
            )
            result.success(map)
        }
    }
}

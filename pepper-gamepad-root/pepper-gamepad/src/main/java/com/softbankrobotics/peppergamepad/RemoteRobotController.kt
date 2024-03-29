package com.softbankrobotics.peppergamepad

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.AttachedFrame
import com.aldebaran.qi.sdk.`object`.actuation.LookAt
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.LookAtBuilder
import com.aldebaran.qi.sdk.builder.TransformBuilder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class RemoteRobotController(private val qiContext: QiContext) {

    private val TAG = "RemoteRobotController"

    var isRunning = false
        private set
    private var startAfterInitialization = false
    private var isMoving = false

    private var currentLeftJoystickX = 0
    private var currentLeftJoystickY = 0
    private var currentRightJoystickX = 0
    private var currentRightJoystickY = 0

    private lateinit var lookAtFuture: Future<Void>
    private lateinit var animateFuture: Future<Void>

    private lateinit var defaultPosition: Transform
    private lateinit var targetFrame: AttachedFrame
    private lateinit var lookAt: LookAt

    init {
        qiContext.actuation.async().gazeFrame().andThenConsume { robotFrame ->
            defaultPosition = TransformBuilder.create().fromXTranslation(100.0)
            targetFrame = robotFrame.makeAttachedFrame(defaultPosition)
            lookAt = LookAtBuilder.with(qiContext).withFrame(targetFrame.frame()).build()
            lookAt.addOnStartedListener {
                Log.i(TAG, "LookAt started")
            }
            if (startAfterInitialization) start()
        }
    }

    fun start() {
        Log.d(TAG, "start")

        if (!::lookAt.isInitialized) {
            startAfterInitialization = true
            return
        }

        if (isRunning) return

        lookAtFuture = lookAt.async().run()
        lookAtFuture.thenConsume {
            when {
                lookAtFuture.isDone -> Log.i(TAG, "LookAt done")
                lookAtFuture.hasError() -> Log.e(TAG, "LookAt error: ${lookAtFuture.errorMessage}")
                lookAtFuture.isCancelled -> Log.e(TAG, "LookAt cancelled")
            }
        }

        isRunning = true
    }

    fun stop() {
        Log.d(TAG, "stop")

        if (!isRunning) return

        lookAtFuture.requestCancellation()
        animateFuture.requestCancellation()

        isRunning = false
    }

    fun updateTarget(
        newLeftJoystickX: Float,
        newLeftJoystickY: Float,
        newRightJoystickX: Float,
        newRightJoystickY: Float
    ) {
        Log.d(
            TAG, "updateTarget newLeftJoystickX=$newLeftJoystickX " +
                    "newLeftJoystickY=$newLeftJoystickY " +
                    "newRightJoystickX=$newRightJoystickX " +
                    "newRightJoystickY=$newRightJoystickY"
        )

        // Round values
        var roundedNewLeftJoystickX = 0
        var roundedNewLeftJoystickY = 0
        if (!(newLeftJoystickX == 0f && newLeftJoystickY == 0f)) {
            val leftJoystickTheta = atan2(newLeftJoystickY, newLeftJoystickX)
            roundedNewLeftJoystickX = (cos(leftJoystickTheta) * 2).roundToInt() * 5
            roundedNewLeftJoystickY = (sin(leftJoystickTheta) * 2).roundToInt() * 5
        }
        var roundedNewRightJoystickX = 0
        var roundedNewRightJoystickY = 0
        if (!(newRightJoystickX == 0f && newRightJoystickY == 0f)) {
            val rightJoystickTheta = atan2(newRightJoystickY, newRightJoystickX)
            roundedNewRightJoystickX = (cos(rightJoystickTheta) * 10).roundToInt()
            roundedNewRightJoystickY = (sin(rightJoystickTheta) * 10).roundToInt()
        }

        // Avoid repeating commands
        if (!(roundedNewLeftJoystickX == currentLeftJoystickX && roundedNewLeftJoystickY == currentLeftJoystickY)) {
            currentLeftJoystickX = roundedNewLeftJoystickX
            currentLeftJoystickY = roundedNewLeftJoystickY

            makeTranslation()
        }
        if (!(roundedNewRightJoystickX == currentRightJoystickX && roundedNewRightJoystickY == currentRightJoystickY)) {
            currentRightJoystickX = roundedNewRightJoystickX
            currentRightJoystickY = roundedNewRightJoystickY

            makeRotation()
        }
    }

    private fun makeTranslation() {
        Log.d(
            TAG,
            "makeTranslation currentLeftJoystickX=$currentLeftJoystickX currentLeftJoystickY=$currentLeftJoystickY"
        )

        if (!isRunning) return

        if (::animateFuture.isInitialized && !animateFuture.isDone) {
            animateFuture.requestCancellation()
        } else if (!(currentLeftJoystickX == 0 && currentLeftJoystickY == 0) && !isMoving) {
            isMoving = true
            lookAt.async().setPolicy(LookAtMovementPolicy.HEAD_ONLY).andThenConsume {
                val targetX = -currentLeftJoystickY.toDouble()
                val targetY = -currentLeftJoystickX.toDouble()

                val animationString = "[\"Holonomic\", [\"Line\", [$targetX, $targetY]], 0.0, 40.0]"
                val animation = AnimationBuilder.with(qiContext).withTexts(animationString).build()
                val animate = AnimateBuilder.with(qiContext).withAnimation(animation).build()
                animate.addOnStartedListener {
                    Log.i(TAG, "Animate started")

                    if (!(targetX == -currentLeftJoystickY.toDouble() && targetY == -currentLeftJoystickX.toDouble())) {
                        animateFuture.requestCancellation()
                    }
                }

                animateFuture = animate.async().run()
                animateFuture.thenConsume {
                    when {
                        animateFuture.isSuccess -> Log.i(TAG, "Animate finished with success")
                        animateFuture.hasError() -> Log.e(
                            TAG,
                            "Animate error: ${animateFuture.errorMessage}"
                        )
                        animateFuture.isCancelled -> Log.i(TAG, "Animate cancelled")
                    }

                    lookAt.policy = LookAtMovementPolicy.HEAD_AND_BASE
                    isMoving = false

                    makeTranslation()
                }
            }
        }
    }

    private fun makeRotation() {
        Log.d(
            TAG,
            "makeRotation currentRightJoystickX=$currentRightJoystickX currentRightJoystickY=$currentRightJoystickY"
        )

        if (!isRunning) return

        if (currentRightJoystickX == 0 && currentRightJoystickY == 0) {
            targetFrame.update(defaultPosition)
        } else {
            val targetX = -currentRightJoystickY.toDouble()
            val targetY = -currentRightJoystickX.toDouble()

            val transform = TransformBuilder.create().from2DTranslation(targetX, targetY)
            targetFrame.update(transform)
        }
    }
}

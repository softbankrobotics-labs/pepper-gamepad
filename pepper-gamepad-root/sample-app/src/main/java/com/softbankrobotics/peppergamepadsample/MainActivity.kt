package com.softbankrobotics.peppergamepadsample

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.hardware.input.InputManager
import android.hardware.input.InputManager.InputDeviceListener
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.`object`.holder.Holder
import com.aldebaran.qi.sdk.builder.HolderBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.softbankrobotics.peppergamepad.RemoteRobotController
import kotlin.concurrent.thread
import kotlin.random.Random

class MainActivity : Activity(), RobotLifecycleCallbacks, InputDeviceListener {

    private val TAG = "RemoteControlSample"

    private lateinit var inputManager: InputManager

    private lateinit var builder: AlertDialog.Builder

    private lateinit var dialog: AlertDialog

    private lateinit var basicAwarenessHolder: Holder

    private lateinit var remoteRobotController: RemoteRobotController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        QiSDK.register(this, this)

        buildDialog()

        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
    }

    private fun buildDialog() {
        builder = AlertDialog.Builder(this)

        builder.setMessage(R.string.no_controller_detected_title)
            .setTitle(R.string.no_controller_detected_message)

        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)
    }

    override fun onResume() {
        super.onResume()
        inputManager.registerInputDeviceListener(this, null)
        checkControllerConnection()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        Log.d(TAG, "onInputDeviceRemoved")
        checkControllerConnection()
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        Log.d(TAG, "onInputDeviceAdded")
        checkControllerConnection()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        Log.d(TAG, "onInputDeviceChanged")
        checkControllerConnection()
    }

    private fun checkControllerConnection() {
        val connectedControllers = getGameControllerIds()
        if (connectedControllers.isEmpty() && !dialog.isShowing) {
            dialog.show()
        } else if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    private fun getGameControllerIds(): List<Int> {
        val gameControllerDeviceIds = mutableListOf<Int>()
        val deviceIds = inputManager.inputDeviceIds
        deviceIds.forEach { deviceId ->
            InputDevice.getDevice(deviceId).apply {

                // Verify that the device has gamepad buttons, control sticks, or both.
                if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
                    || sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
                ) {
                    // This device is a game controller. Store its device ID.
                    gameControllerDeviceIds
                        .takeIf { !it.contains(deviceId) }
                        ?.add(deviceId)
                }
            }
        }
        return gameControllerDeviceIds
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.i(TAG, "onRobotFocusGained")
        // Hold Basic Awareness to avoid robot getting distracted
        basicAwarenessHolder = HolderBuilder.with(qiContext)
            .withAutonomousAbilities(AutonomousAbilitiesType.BASIC_AWARENESS)
            .build()
        basicAwarenessHolder.async().hold().thenConsume {
            when {
                it.isSuccess -> Log.i(TAG, "BasicAwareness held with success")
                it.hasError() -> Log.e(TAG, "holdBasicAwareness error: " + it.errorMessage)
                it.isCancelled -> Log.e(TAG, "holdBasicAwareness cancelled")
            }
        }
        remoteRobotController = RemoteRobotController(qiContext)
        Log.i(TAG, "after RemoteRobotController instantiation")

        sayWelcomeSentence(qiContext)
    }

    private fun sayWelcomeSentence(qiContext: QiContext) {
        val welcomeSentences = resources.getStringArray(R.array.welcome_strings)
        val i = Random.nextInt(0, welcomeSentences.size - 1)
        SayBuilder.with(qiContext)
            .withText(welcomeSentences[i])
            .build()
            .run()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        Log.d(TAG, "onGenericMotionEvent $event")

        // Add null protection for when the controller disconnects
        val inputDevice = event.device ?: return super.onGenericMotionEvent(event)

        // Get left joystick coordinates
        val leftJoystickX = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_X)
        val leftJoystickY = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_Y)

        // Get right joystick coordinates
        val rightJoystickX = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_Z)
        val rightJoystickY = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_RZ)

        if (::remoteRobotController.isInitialized) {
            thread {
                remoteRobotController.updateTarget(leftJoystickX, leftJoystickY, rightJoystickX, rightJoystickY)
            }
        } else {
            Log.d(TAG, "@@@@@@@@@ not initialized")
        }

        return true
    }

    private fun getCenteredAxis(
        event: MotionEvent,
        device: InputDevice,
        axis: Int
    ): Float {
        val range: InputDevice.MotionRange? = device.getMotionRange(axis, event.source)

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        range?.apply {
            val value = event.getAxisValue(axis)

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                return value
            }
        }
        return 0f
    }

    override fun onRobotFocusLost() {
        Log.i(TAG, "onRobotFocusLost")
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.e(TAG, "onRobotFocusRefused: $reason")
    }

    override fun onPause() {
        super.onPause()
        inputManager.unregisterInputDeviceListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        QiSDK.unregister(this, this)
    }
}

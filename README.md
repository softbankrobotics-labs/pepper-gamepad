# Pepper Gamepad Remote Control Library

This Android Library will help you to connect an external controller and map buttons in order to make a Pepper move, using the QiSDK.

This library was tested with a Xbox One Controller, but can be used with any Bluetooth input device.

### Video Demonstration

This video was filmed at SoftBank Robotics Europe, and shows the basic control scheme while navigating around the SBRE Showroom. 

[Watch video on YouTube](https://www.youtube.com/watch?v=rdoyns-ZicA)

## Getting Started


### Prerequisites

A robotified project for Pepper with QiSDK. Read the [documentation](https://developer.softbankrobotics.com/pepper-qisdk) if needed

A connection to an external controller.

### Connecting a Controller

1. Turn on the Bluetooth for the Android Tablet on Pepper.
2. Set you controller to pairing mode. This will differ between devices, please check the instructions for your specific device if unsure.
3. In the Tablet settings, navigate to Bluetooth Settings, find the device and pair it.

### Running the Sample Application

The project comes complete with a sample project. You can clone the repository, open it in Android Studio, and run this directly onto a Robot. 

The sample application will handle the case where the controller is not connected, you can use a similar function in your own application. 

Because any number of external controllers can be used, and the mapping needed may be different, it is necessary to override `onGenericMotionEvent` and pass the values to the `RemoteRobotController` in the **pepper-gamepad** library.

Full implementation details are available to see in the sample project.

### Installing

[**Download the latest compiled .aar**](pepper-gamepad-root/pepper-gamepad/compiled/pepper-gamepad-1.0.0.aar)

In order to implement the library into your own project, you must build and install the .aar library, please follow this steps: 

1.  Build the `pepper-gamepad` project either with Android Studio, or by running `./gradlew build` The output AAR file is located in **pepper-gamepad > build > outputs > aar**.

2.  In your robitified project, add the compiled AAR file:
    * Click File > New > New Module.
    * Click Import .JAR/.AAR Package then click Next.
    * Enter the location of the compiled AAR or JAR file then click Finish.

3.    Make sure the library is listed at the top of your `settings.gradle` file:
```
include ':app',  ':pepper-gamepad-1.0.0'
```

4.  Open the app module's  `build.gradle`  file and add a new line to the  `dependencies`  block as shown in the following snippet:
```
dependencies {
   implementation project(":pepper-gamepad-1.0.0")
}
```

5.  Click  **Sync Project with Gradle Files**.


## Usage

*This README assumes some standard setup can be done by the user, such as initialising variables or implementing code in the correct functions. Refer to the Sample Project for full usage code.*

Initialise the QISDK in the onCreate. If you are unsure how to do this, refer to the QISDK tutorials [here](https://qisdk.softbankrobotics.com/sdk/doc/pepper-sdk/ch1_gettingstarted/starting_project.html)

    QiSDK.register(this, this)

In the `onRobotFocusGained`, disable BasicAwareness, and instantiate a `RemoteRobotController` object by passing it the QiContext. 


```
override fun onRobotFocusGained(qiContext: QiContext) {  
    val basicAwarenessHolder = HolderBuilder.with(qiContext)  
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
}
```




Get the position of the controller and call updateTarget method. It is important to call this function in a thread, as it is using references to the QISDK.
```
remoteRobotController.updateTarget(leftJoystickX, leftJoystickY, rightJoystickX, rightJoystickY)
```

- Left joystick makes Pepper translate
- Right joystick makes Pepper rotate

Example :

```
override fun onGenericMotionEvent(event: MotionEvent): Boolean {  
  
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
    } 
  
    return true  
}

private fun getCenteredAxis(event: MotionEvent, device: InputDevice, axis: Int): Float {  
    val range: InputDevice.MotionRange? = device.getMotionRange(axis, event.source)  
  
    // A joystick at rest does not always report an absolute position of  
    // (0,0). Use the getFlat() method to determine the range of values bounding the joystick axis center.  

    range?.apply {  
        val value = event.getAxisValue(axis)  
  
        // Ignore axis values that are within the 'flat' region of the joystick axis center.  
        if (Math.abs(value) > flat) {  
            return value  
        }  
    }  
    
    return 0f  
}
```

## License

This project is licensed under the BSD 3-Clause "New" or "Revised" License- see the [COPYING](COPYING.md) file for details



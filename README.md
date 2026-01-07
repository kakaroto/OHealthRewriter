<p align="center">
  <img src="assets/logo.svg" alt="OHealth Steps Rewriter Logo"/>
</p>

# OHealth Steps Rewriter

This Android application reads step data from the OHealth app (`com.heytap.health.international`) and rewrites it to Health Connect, allowing you to modify the device metadata associated with the step records.

## What it does

The primary function of this app is to intercept step data from the OHealth app, and then create new, identical step records in Health Connect with different device metadata. This can be useful if you want your step data to appear as if it's coming from a different device, such as a "OnePlus Watch 3".

The app uses a `WorkManager` to periodically check for new step data from OHealth in the background and rewrite it, making it energy efficient. You can also trigger a manual poll from the app's main screen.

## Why ?

The step data sent by the OHealth application is lacking all metadata. I started playing [Walkscape](https://walkscape.app/) and it could not pick up the step count from my smartwatch because the step counts were reported as being of "UNKNOWN" type, instead of "AUTOMATICALLY_RECORDED" type.
I built this app to fix the problem in OHealth so I can enjoy playing Walkscape with my wearable device.

## How to Build and Test

1.  Clone this repository.
2.  Open the project in Android Studio.
3.  Build the app using Gradle. You can do this from the command line with `./gradlew assembleDebug` or by using the "Build" menu in Android Studio.
4.  Install the app on an Android device that has both the OHealth app and the Health Connect app installed.
5.  Launch the app and grant the necessary Health Connect permissions when prompted.
6.  The app will then start to work in the background. You can monitor its activity in the in-app log viewer as well as configure it to your needs.

## Configuration

You can configure the device metadata (manufacturer, model, and type) that will be used for the rewritten step records by accessing the "Settings" menu in the app.

## Disclaimer on use of AI

This application was primarily developed using AI help from the Android Studio's copilot chat.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

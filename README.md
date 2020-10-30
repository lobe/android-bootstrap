<div style="text-align:center"><img src="https://github.com/lobe/android-bootstrap/raw/master/assets/header.png" /></div>

[Lobe](http://lobe.ai/) is an easy-to-use free tool to help you start working with machine learning.

This project was created to help you bootstrap your Lobe project on android. Built with [Kotlin](https://kotlinlang.org).

## Installing Your Development Environment

In this stage we’re going to get you setup so you can build, launch, and play with your app. These instructions are written for macOS, the only system you can develop android apps on.

To start, we’re going to download ("clone") this repository.

If you already have `git` installed, skip to [Step 2](#clone-this-repository).

If you prefer to use the [GitHub Desktop](https://desktop.github.com) app, click on the "Code" button above and click "Open with GitHub Desktop":

![](https://github.com/lobe/android-bootstrap/raw/master/assets/downloadProject.png)

Otherwise, we need to install a few things:

### Step 1 - Install [Homebrew](http://brew.sh/) and [Git](https://git-scm.com)

First, [open a Terminal window](http//www.youtube.com/watch?v=zw7Nd67_aFw).

Next, copy & paste the following into a Terminal window and hit return.

```shell
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install.sh)"
brew doctor
brew install git
```

Now that we have installed `git`, you can clone this repo with the following command. You'll want to navigate to a folder in Terminal where you'd like to store these files. If you need help, here's a [gentle introduction to navigation in the terminal](http//www.youtube.com/watch?v=zw7Nd67_aFw).

```shell
git clone https://github.com/lobe/android-bootstrap.git
```

### Step 2 - Installing [Android Studio](https://developer.android.com/studio)

Next, we're going to install Android Studio, a free tool from Google, via the [their website](https://developer.android.com/studio). This is a fairly straightforward process that could take an hour or more, as the IDE app is pretty large.

Once it's done, open on this repository from inside Android Studio.

Now we need to export your custom model from Lobe. If you'd like, you can skip to the [deploying your app](#deploying-your-app) section if you just want to see this app working with the default sample model.

### Step 3 - Exporting your model

Once you've trained a custom model in Lobe, you can drop it into your app.

First, let's open your project in Lobe and export it by pressing `⌘E` and selecting Tensorflow Light.

Once you have the Tensorflow Light model, rename it to `model_unquant.tflite` and drag it into this repo to replace the existing model files: 
* `lobe_android/app/src/main/assets/model_unquant.tflite`
* `lobe_android/app/src/main/assets/labels.txt`

And we're done! Next let's get it on your phone so you can see it work live.

### Step 4 - Deploying your app

Next, we'll want to get this app onto your phone so you can see it working live with your device's camera. To do this, plug in your device via a USB cable and, in the open Android Studio window, press the play button in the top right corner of the window.

And there you have it! Your app should be running on your device. And finally, if you'd like to post your app (running your custom image classification model) to the Google Play Store, you're more than welcome to do so. [Follow the instructions here](https://support.google.com/googleplay/android-developer/answer/113469?hl=en) to get the process rolling. You'll need to have an Google Developer account.

## Tips and Tricks

This app is meant as a starting place for your own project. Below is a high level overview of the project to get you started. Like any good bootstrap app, this project has been kept intentionally simple. There are only two main components in two files, `CameraActivity.kt` and `DetectorActivity.kt`.

## Contributing

If you can think of anything you'd like to add, or bugs you find, please reach out! PRs will be openly accepted (if they keep project simple, bonus points for making it even simpler) and issues will be triaged.

For project ideas or feedback, please visit our community on [Reddit](https://www.reddit.com/r/Lobe/)!

We look forward to seeing the awesome projects you put out there into the world! Cheers!

![team sig](https://github.com/lobe/iOS-bootstrap/raw/master/assets/lobeteam.png)

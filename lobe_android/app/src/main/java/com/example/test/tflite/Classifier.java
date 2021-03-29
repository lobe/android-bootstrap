/*
Copyright 2021 Microsoft. All Rights Reserved.

Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

This file has been modified by Microsoft to add support for using Lobe exported models.
==============================================================================*/

package com.example.test.tflite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.os.Trace;

import com.example.test.env.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeOp.ResizeMethod;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A classifier specialized to label images using TensorFlow Lite.
 */
public abstract class Classifier {
    private static final Logger LOGGER = new Logger();
    /**
     * Number of results to show in the UI.
     */
    private static final int MAX_RESULTS = 3;
    /**
     * Image size along the x axis.
     */
    private final int imageSizeX;
    /**
     * Image size along the y axis.
     */
    private final int imageSizeY;
    /**
     * Options for configuring the Interpreter.
     */
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();
    /**
     * Output probability TensorBuffer.
     */
    private final TensorBuffer outputProbabilityBuffer;
    /**
     * Processer to apply post processing of the output probability.
     */
    private final TensorProcessor probabilityProcessor;
    /**
     * Labels corresponding to the output of the vision model.
     */
    private final List<String> labels;
    private final int LEGACY_VERSION = -1;
    private final ArrayList<Integer> SUPPORTED_VERSIONS = new ArrayList(Arrays.asList(LEGACY_VERSION, 1));
    /**
     * An instance of the driver class to run model inference with Tensorflow Lite.
     */
    protected Interpreter tflite;
    /**
     * The loaded TensorFlow Lite model.
     */
    private MappedByteBuffer tfliteModel;
    /**
     * Optional GPU delegate for accleration.
     */
    private GpuDelegate gpuDelegate = null;
    /**
     * Optional NNAPI delegate for accleration.
     */
    private NnApiDelegate nnApiDelegate = null;
    /**
     * Input image TensorBuffer.
     */
    private TensorImage inputImageBuffer;

    /**
     * Initializes a {@code Classifier}.
     */
    protected Classifier(Activity activity, Device device, int numThreads) throws IOException {
        tfliteModel = FileUtil.loadMappedFile(activity, getModelPath());
        switch (device) {
            case NNAPI:
                nnApiDelegate = new NnApiDelegate();
                tfliteOptions.addDelegate(nnApiDelegate);
                break;
            case GPU:
                gpuDelegate = new GpuDelegate();
                tfliteOptions.addDelegate(gpuDelegate);
                break;
            case CPU:
                break;
        }
        tfliteOptions.setNumThreads(numThreads);
        tflite = new Interpreter(tfliteModel, tfliteOptions);


        // Defaults
        labels = new ArrayList<String>();
        int tempImageSizeY = 224;
        int tempImageSizeX = 224;
        DataType imageDataType = DataType.FLOAT32;
        DataType probabilityDataType = DataType.FLOAT32;
        int[] probabilityShape = new int[0];

        // Load signature file to obtain labels, input, and output formats of the model.
        // Using this instead of tflite.get* methods as order of operations and fields are not guaranteed.
        try {
            // Loads labels out from the signature file.
            String json = new String(FileUtil.loadByteFromFile(activity, getLabelPath()), StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(json);

            int version = LEGACY_VERSION;
            try {
                version = jsonObject.getInt("export_model_version");
            } catch (JSONException e) {
            }
            LOGGER.d("Version: $version");
            if (!SUPPORTED_VERSIONS.contains(version)) {
                String versionMessage = "The model version $this.version you are using for this starter project may not be compatible with the supported versions [$SUPPORTED_VERSIONS]. Please update both this starter project and Lobe to latest versions, and try exporting your model again. If the issue persists, please contact us at lobesupport@microsoft.com";
                LOGGER.e(versionMessage);
                throw new Error(versionMessage);
            }

            JSONArray jArr = jsonObject.getJSONObject("classes").getJSONArray("Label");
            try {
                for (int i = 0, l = jArr.length(); i < l; i++) {
                    labels.add(jArr.getString(i));
                }
            } catch (JSONException e) {
            }

            // Reads type and shape of input and output tensors, respectively.
            JSONObject inputImage = jsonObject.getJSONObject("inputs").getJSONObject("Image");
            JSONArray shapeArray = inputImage.getJSONArray("shape"); // {1, height, width, 3}
            String typeValue = inputImage.getString("dtype").toUpperCase();
            tempImageSizeY = shapeArray.getInt(1);
            tempImageSizeX = shapeArray.getInt(2);
            imageDataType = DataType.valueOf(typeValue);

            JSONObject outputConfidences = jsonObject.getJSONObject("outputs").getJSONObject("Confidences");
            JSONArray outputShapeArray = outputConfidences.getJSONArray("shape"); // {1, NUM_CLASSES}
            String outputTypeValue = outputConfidences.getString("dtype").toUpperCase();
            probabilityShape = new int[outputShapeArray.length()];
            for (int i = 0, l = outputShapeArray.length(); i < l; i++) {
                try {
                    probabilityShape[i] = outputShapeArray.getInt(i);
                } catch (JSONException e) {
                    probabilityShape[i] = 1; // default
                }
            }
            probabilityDataType = DataType.valueOf(outputTypeValue);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        imageSizeY = tempImageSizeY;
        imageSizeX = tempImageSizeX;

        // Creates the input tensor.
        inputImageBuffer = new TensorImage(imageDataType);

        // Create the output tensor.
        outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

        // Creates the post processor for the output probability.
        probabilityProcessor = new TensorProcessor.Builder().add(getPostprocessNormalizeOp()).build();

        LOGGER.d("Created a Tensorflow Lite Image Classifier.");
    }

    /**
     * Creates a classifier with the provided configuration
     * <p>
     * Perhaps in the future we can remove other model types from this example app
     *
     * @param activity   The current Activity.
     * @param model      The model to use for classification.
     * @param device     The device to use for classification.
     * @param numThreads The number of threads to use for classification.
     * @return A classifier with the desired configuration.
     */
    public static Classifier create(Activity activity, Model model, Device device, int numThreads)
            throws IOException {
        if (model == Model.IMAGE_CLASSIFIER) {
            return new ImageClassifier(activity, device, numThreads);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Gets the top-k results.
     */
    private static List<Recognition> getTopKProbability(Map<String, Float> labelProb) {
        // Find the best classifications.
        PriorityQueue<Recognition> pq =
                new PriorityQueue<>(
                        MAX_RESULTS,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(Recognition lhs, Recognition rhs) {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });

        for (Map.Entry<String, Float> entry : labelProb.entrySet()) {
            pq.add(new Recognition("" + entry.getKey(), entry.getKey(), entry.getValue(), null));
        }

        final ArrayList<Recognition> recognitions = new ArrayList<>();
        int recognitionsSize = Math.min(pq.size(), MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }
        return recognitions;
    }

    /**
     * Runs inference and returns the classification results.
     */
    public List<Recognition> recognizeImage(final Bitmap bitmap, int sensorOrientation) {
        // Logs this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage");

        Trace.beginSection("loadImage");
        long startTimeForLoadImage = SystemClock.uptimeMillis();
        inputImageBuffer = loadImage(bitmap, sensorOrientation);
        long endTimeForLoadImage = SystemClock.uptimeMillis();
        Trace.endSection();
        LOGGER.v("Timecost to load the image: " + (endTimeForLoadImage - startTimeForLoadImage));

        // Runs the inference call.
        Trace.beginSection("runInference");
        long startTimeForReference = SystemClock.uptimeMillis();
        tflite.run(inputImageBuffer.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());
        long endTimeForReference = SystemClock.uptimeMillis();
        Trace.endSection();
        LOGGER.v("Timecost to run model inference: " + (endTimeForReference - startTimeForReference));

        // Gets the map of label and probability.
        Map<String, Float> labeledProbability =
                new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
                        .getMapWithFloatValue();
        Trace.endSection();

        // Gets top-k results.
        return getTopKProbability(labeledProbability);
    }

    /**
     * Closes the interpreter and model to release resources.
     */
    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }
        if (nnApiDelegate != null) {
            nnApiDelegate.close();
            nnApiDelegate = null;
        }
        tfliteModel = null;
    }

    /**
     * Get the image size along the x axis.
     */
    public int getImageSizeX() {
        return imageSizeX;
    }

    /**
     * Get the image size along the y axis.
     */
    public int getImageSizeY() {
        return imageSizeY;
    }

    /**
     * Loads input image, and applies preprocessing.
     */
    private TensorImage loadImage(final Bitmap bitmap, int sensorOrientation) {
        // Loads bitmap into a TensorImage.
        inputImageBuffer.load(bitmap);

        // Creates processor for the TensorImage.
        int cropSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
        int numRotation = sensorOrientation / 90;
        // TODO(b/143564309): Fuse ops inside ImageProcessor.
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
                        .add(new ResizeOp(imageSizeX, imageSizeY, ResizeMethod.NEAREST_NEIGHBOR))
                        .add(new Rot90Op(numRotation))
                        .add(getPreprocessNormalizeOp())
                        .build();
        return imageProcessor.process(inputImageBuffer);
    }

    /**
     * Gets the name of the model file stored in Assets.
     */
    protected abstract String getModelPath();

    /**
     * Gets the name of the label file stored in Assets.
     */
    protected abstract String getLabelPath();

    /**
     * Gets the TensorOperator to nomalize the input image in preprocessing.
     */
    protected abstract TensorOperator getPreprocessNormalizeOp();

    /**
     * Gets the TensorOperator to dequantize the output probability in post processing.
     *
     * <p>For quantized model, we need de-quantize the prediction with NormalizeOp (as they are all
     * essentially linear transformation). For float model, de-quantize is not required. But to
     * uniform the API, de-quantize is added to float model too. Mean and std are set to 0.0f and
     * 1.0f, respectively.
     */
    protected abstract TensorOperator getPostprocessNormalizeOp();

    /**
     * The model type used for classification.
     */
    public enum Model {
        IMAGE_CLASSIFIER
    }

    /**
     * The runtime device type used for executing classification.
     */
    public enum Device {
        CPU,
        NNAPI,
        GPU
    }

    /**
     * An immutable result returned by a Classifier describing what was recognized.
     */
    public static class Recognition {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final String id;

        /**
         * Display name for the recognition.
         */
        private final String title;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         */
        private final Float confidence;

        /**
         * Optional location within the source image for the location of the recognized object.
         */
        private RectF location;

        public Recognition(
                final String id, final String title, final Float confidence, final RectF location
        ) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        public void setLocation(RectF location) {
            this.location = location;
        }

        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }
}

package com.haruncetin.depthestimation

import android.graphics.Bitmap
import androidx.camera.core.ExperimentalGetImage
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegateFactory
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat

@ExperimentalGetImage
class MidasNetSmall(
    internal var mapType: MapType = MapType.DEPTHVIEW_GRAYSCALE
) {
    companion object {
        private const val MODEL_NAME        = "lite-model_midas_v2_1_small_1_lite_1.tflite"
        private const val INPUT_IMAGE_DIM   = 256
        private const val NUM_THREADS       = 8
        private val NORM_MEAN               = floatArrayOf(123.675f, 116.28f, 103.53f)
        private val NORM_STD                = floatArrayOf(58.395f, 57.12f, 57.375f)
    }

    private var inferenceTime: Long = 0
    private var interpreter : Interpreter

    private val inputTensorProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_IMAGE_DIM, INPUT_IMAGE_DIM, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(NORM_MEAN, NORM_STD))
        .build()

    private val outputTensorProcessor = TensorProcessor.Builder()
        .add(DepthScalingOp())
        .build()

    init {
        val interpreterOptions = Interpreter.Options().apply {
            val compatibilityList = CompatibilityList()
            if (compatibilityList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = GpuDelegateFactory.Options()
                val gpuDelegate = GpuDelegateFactory.create(delegateOptions)
                this.addDelegate(gpuDelegate)
            }
            this.numThreads = NUM_THREADS
        }
        interpreter = Interpreter(
            FileUtil.loadMappedFile(
                DepthEstimationApp.applicationContext(),
                MODEL_NAME
            ),
            interpreterOptions
        )
    }

    fun getDepthMap(inputImage: Bitmap): Bitmap {
        var inputTensor = TensorImage.fromBitmap(inputImage)
        val startTime = System.currentTimeMillis()
        inputTensor = inputTensorProcessor.process(inputTensor)

        var outputTensor = TensorBufferFloat.createFixedSize(
            intArrayOf(INPUT_IMAGE_DIM, INPUT_IMAGE_DIM, 1),
            DataType.FLOAT32
        )

        interpreter.run(inputTensor.buffer, outputTensor.buffer)
        outputTensor = outputTensorProcessor.process(outputTensor)

        inferenceTime = System.currentTimeMillis() - startTime

        return if (mapType == MapType.DEPTHVIEW_GRAYSCALE)
            outputTensor.floatArray.toGrayscaleBitmap(INPUT_IMAGE_DIM)
        else
            outputTensor.floatArray.toHeatMapBitmap(INPUT_IMAGE_DIM)
    }

    fun getInferenceTime(): Long = inferenceTime

    class DepthScalingOp : TensorOperator {
        override fun apply(input: TensorBuffer?): TensorBuffer {
            val values = input!!.floatArray
            val max = values.maxOrNull()!!
            val min = values.minOrNull()!!
            if (max - min > Float.MIN_VALUE) {
                for (i in values.indices) {
                    var p: Int = (((values[i] - min) / (max - min)) * 255).toInt()
                    if (p < 0) p += 255
                    values[i] = p.toFloat()
                }
            } else {
                for (i in values.indices) {
                    values[i] = 0.0f
                }
            }
            val output = TensorBufferFloat.createFrom(input, DataType.FLOAT32)
            output.loadArray(values)
            return output
        }
    }
}

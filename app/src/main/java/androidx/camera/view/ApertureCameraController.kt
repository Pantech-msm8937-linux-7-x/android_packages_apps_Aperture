/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.camera.view

import android.content.Context
import androidx.annotation.MainThread
import androidx.camera.core.Camera
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.impl.utils.Threads
import androidx.lifecycle.LifecycleOwner

/**
 * [LifecycleCameraController], but recording what the camera sees as it is.
 *
 * The view port of the [PreviewView] crops all the use cases to its aspect ratio, video too. A
 * crop of the video is made on the GPU: CameraX then draws every camera frame once for the
 * preview and once more for the encoder, and the encoder converts what it gets from RGBA. That
 * keeps the GPU of a low end SoC busy enough to stall the preview while recording. Without the
 * view port, the camera feeds the encoder straight, and the video keeps its whole frame (the
 * preview shows it with fitCenter anyway).
 *
 * It lives in the package of CameraX for the package private parts of [CameraController] that
 * [LifecycleCameraController] uses; it is the same otherwise.
 */
class ApertureCameraController(context: Context) : CameraController(context) {
    private var lifecycleOwner: LifecycleOwner? = null

    @MainThread
    fun bindToLifecycle(lifecycleOwner: LifecycleOwner) {
        Threads.checkMainThread()
        this.lifecycleOwner = lifecycleOwner
        startCameraAndTrackStates()
    }

    @MainThread
    fun unbind() {
        Threads.checkMainThread()
        lifecycleOwner = null
        mCamera = null
        mCameraProvider?.unbindAll()
    }

    override fun startCamera(): Camera? {
        val lifecycleOwner = lifecycleOwner ?: return null
        val cameraProvider = mCameraProvider ?: return null
        val useCaseGroup = createUseCaseGroup() ?: return null

        return try {
            cameraProvider.bindToLifecycle(lifecycleOwner, mCameraSelector, useCaseGroup)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException(
                "The selected camera does not support the enabled use cases.", e
            )
        }
    }

    override fun createUseCaseGroup(): UseCaseGroup? {
        val useCaseGroup = super.createUseCaseGroup() ?: return null

        if (!isVideoCaptureEnabled || useCaseGroup.viewPort == null) {
            return useCaseGroup
        }

        return UseCaseGroup.Builder().apply {
            useCaseGroup.useCases.forEach { addUseCase(it) }
            useCaseGroup.effects.forEach { addEffect(it) }
        }.build()
    }
}

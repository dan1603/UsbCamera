package com.example.cameraapp

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceHolder.*
import android.view.View.OnClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.serenegiant.usb.CameraDialog
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener
import com.serenegiant.usb.USBMonitor.UsbControlBlock
import com.serenegiant.usb.UVCCamera
import com.serenegiant.utils.ThreadPool.queueEvent
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), CameraDialog.CameraDialogParent {

    private val tag = "MainActivity"

    private val sync = Any()

    private var usbMonitor: USBMonitor? = null
    private var uvcCamera: UVCCamera? = null
    private var previewSurface: Surface? = null
    private var isActive = false
    private var isPreview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraButton.setOnClickListener(onClickListener)
        cameraSurfaceView.holder.addCallback(surfaceViewCallback)
        usbMonitor = USBMonitor(this, onDeviceConnectListener)
    }

    override fun onStart() {
        super.onStart()
        Log.v(tag, "onStart:")
        synchronized(sync) {
            if (usbMonitor != null) {
                usbMonitor?.register()
            }
        }
    }

    override fun onStop() {
        Log.v(tag, "onStop:")
        synchronized(sync) {
            if (usbMonitor != null) {
                usbMonitor?.unregister()
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        Log.v(tag, "onDestroy:")
        synchronized(sync) {
            isPreview = false
            isActive = isPreview
            if (uvcCamera != null) {
                uvcCamera?.destroy()
                uvcCamera = null
            }
            if (usbMonitor != null) {
                usbMonitor?.destroy()
                usbMonitor = null
            }
        }
        super.onDestroy()
    }

    private val onClickListener: OnClickListener = OnClickListener {
        if (uvcCamera == null) {
            CameraDialog.showDialog(this@MainActivity)
        } else {
            synchronized(sync) {
                uvcCamera?.destroy()
                uvcCamera = null
                isPreview = false
                isActive = isPreview
            }
        }
    }

    private val onDeviceConnectListener: OnDeviceConnectListener =
        object : OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {
                Log.v(tag, "onAttach:")
                Toast.makeText(this@MainActivity, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show()
            }

            override fun onConnect(
                device: UsbDevice?,
                ctrlBlock: UsbControlBlock?,
                createNew: Boolean
            ) {
                Log.v(tag, "onConnect:")
                synchronized(sync) {
                    if (uvcCamera != null) {
                        uvcCamera?.destroy()
                    }
                    isPreview = false
                    isActive = isPreview
                }
                queueEvent(Runnable {
                    synchronized(sync) {
                        val camera =
                            UVCCamera()
                        camera.open(ctrlBlock)
                        Log.i(
                            tag,
                            "supportedSize:" + camera.supportedSize
                        )
                        try {
                            camera.setPreviewSize(
                                UVCCamera.DEFAULT_PREVIEW_WIDTH,
                                UVCCamera.DEFAULT_PREVIEW_HEIGHT,
                                UVCCamera.FRAME_FORMAT_MJPEG
                            )
                        } catch (e: IllegalArgumentException) {
                            try {
                                // fallback to YUV mode
                                camera.setPreviewSize(
                                    UVCCamera.DEFAULT_PREVIEW_WIDTH,
                                    UVCCamera.DEFAULT_PREVIEW_HEIGHT,
                                    UVCCamera.DEFAULT_PREVIEW_MODE
                                )
                            } catch (e1: IllegalArgumentException) {
                                camera.destroy()
                                return@Runnable
                            }
                        }
                        previewSurface = cameraSurfaceView.holder.surface
                        if (previewSurface != null) {
                            isActive = true
                            camera.setPreviewDisplay(previewSurface)
                            camera.startPreview()
                            isPreview = true
                        }
                        synchronized(sync) { uvcCamera = camera }
                    }
                })
            }

            override fun onDisconnect(device: UsbDevice?, ctrlBlock: UsbControlBlock?) {
                Log.v(tag, "onDisconnect:")
                queueEvent {
                    synchronized(sync) {
                        if (uvcCamera != null) {
                            uvcCamera?.close()
                            if (previewSurface != null) {
                                previewSurface?.release()
                                previewSurface = null
                            }
                            isPreview = false
                            isActive = isPreview
                        }
                    }
                }
            }

            override fun onDettach(device: UsbDevice?) {
                Log.v(tag, "onDettach:")
                Toast.makeText(this@MainActivity, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show()
            }

            override fun onCancel(device: UsbDevice?) {}
        }

    override fun getUSBMonitor(): USBMonitor? {
        return usbMonitor
    }

    override fun onDialogResult(canceled: Boolean) {
        if (canceled) {
            runOnUiThread {

            }
        }
    }

    private val surfaceViewCallback: Callback =
        object : Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.v(tag, "surfaceCreated:")
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                if (width == 0 || height == 0) return
                Log.v(tag, "surfaceChanged:")
                previewSurface = holder.surface
                synchronized(sync) {
                    if (isActive && !isPreview && uvcCamera != null) {
                        uvcCamera?.setPreviewDisplay(previewSurface)
                        uvcCamera?.startPreview()
                        isPreview = true
                    }
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.v(tag, "surfaceDestroyed:")
                synchronized(sync) {
                    if (uvcCamera != null) {
                        uvcCamera?.stopPreview()
                    }
                    isPreview = false
                }
                previewSurface = null
            }
        }
}
package com.rakinda.thermal256

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Surface
import com.rakinda.thermal256.databinding.ActivityMainBinding
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    lateinit var mCameraHandler: UVCCameraHandler
    lateinit var mUSBMonitor: USBMonitor
    private var job: Job? = null

    private var isTempMeasuring = false
    private var isThermalPreviewing = false

    lateinit var cursorYellow: Bitmap
    lateinit var cursorRed: Bitmap
    lateinit var cursorBlue: Bitmap
    lateinit var cursorGreen: Bitmap
    lateinit var waterMarkLogo: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        initThermalRes()

    }

    override fun onStart() {
        super.onStart()
        mUSBMonitor.register()
    }

    override fun onResume() {
        super.onResume()
        if (!mUSBMonitor.isRegistered) {
            mUSBMonitor.register()
        }
    }

    override fun onStop() {
        super.onStop()
        job?.cancel()
        mUSBMonitor.unregister()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseThermalDevice()
    }

    private fun releaseThermalDevice() {
        mCameraHandler.close()
        mUSBMonitor.destroy()
    }

    private fun initThermalRes() {
        cursorYellow = BitmapFactory.decodeResource(resources, R.drawable.cursoryellow)
        cursorRed = BitmapFactory.decodeResource(resources, R.drawable.cursorred)
        cursorBlue = BitmapFactory.decodeResource(resources, R.drawable.cursorblue)
        cursorGreen = BitmapFactory.decodeResource(resources, R.drawable.cursorgreen)
        waterMarkLogo = BitmapFactory.decodeResource(resources, R.drawable.xtherm)


        binding.thermalPreviewView.rotationY = 180f
        mCameraHandler = UVCCameraHandler.createHandler(
            this, binding.thermalPreviewView, 1, 256, 192, 0,
            UVCCamera.DEFAULT_BANDWIDTH, null, 0
        )

        mUSBMonitor = USBMonitor(this, ThermalDeviceConnectListener(this))

    }

    private fun onThermalDeviceAttach(device: UsbDevice) {
        if (device.deviceClass == 239 && device.deviceSubclass == 2) {
            mUSBMonitor.requestPermission(device)
        }
    }

    private fun onThermalDeviceConnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock, createNew: Boolean) {
        mCameraHandler.open(ctrlBlock)
        if (job?.isActive == true) {
            job?.cancel()
            job = null
        }

        job = CoroutineScope(Dispatchers.IO).launch {
            delay(500)
            withContext(Dispatchers.Main) {
                val left = binding.thermalPreviewView.left
                val top = binding.thermalPreviewView.top
                val bottom = binding.thermalPreviewView.bottom
                val right = binding.thermalPreviewView.right
                binding.thermalPreviewView.iniTempBitmap(right - left, bottom - top)
                binding.thermalPreviewView.setBitmap(cursorRed, cursorGreen, cursorBlue, cursorYellow, waterMarkLogo)
                mCameraHandler.startPreview(Surface(binding.thermalPreviewView.surfaceTexture))
            }

            delay(300)
            withContext(Dispatchers.Main) {
                mCameraHandler.setValue(UVCCamera.CTRL_ZOOM_ABS, 0x8004)
            }

            delay(300)
            withContext(Dispatchers.Main) {
                mCameraHandler.changePalette(2)
            }

            delay(1000)
            withContext(Dispatchers.Main) {
                mCameraHandler.startTemperaturing()
                isTempMeasuring = true
                isThermalPreviewing = true
            }

            delay(500)
            withContext(Dispatchers.Main) {
                // todo hide loading dialog.
            }
        }
    }

    private fun onThermalDeviceDisconnect() {

    }

    class ThermalDeviceConnectListener(activity: MainActivity) : USBMonitor.OnDeviceConnectListener {
        private var ref: WeakReference<MainActivity>? = null

        init {
            ref = WeakReference(activity)
        }

        override fun onAttach(device: UsbDevice?) {
            if (device != null) {
                ref?.get()?.onThermalDeviceAttach(device)
            }
        }

        override fun onDettach(device: UsbDevice?) {
        }

        override fun onConnect(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean
        ) {
            if (device != null && ctrlBlock != null) {
                ref?.get()?.onThermalDeviceConnect(device, ctrlBlock, createNew)
            }
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            ref?.get()?.onThermalDeviceDisconnect()
        }

        override fun onCancel(device: UsbDevice?) {
        }

    }

}
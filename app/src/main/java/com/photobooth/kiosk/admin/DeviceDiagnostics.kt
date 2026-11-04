package com.photobooth.kiosk.admin

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

data class CameraOption(
    val id: String,
    val facing: String,
    val megapixels: String,
    val maxResolution: String,
    val hardwareLevel: String,
)

data class UsbDeviceOption(
    val name: String,
    val vendorId: String,
    val productId: String,
)

data class NetworkAddress(
    val interfaceName: String,
    val address: String,
)

data class DeviceSummary(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdk: Int,
    val screen: String,
    val density: String,
    val appVersion: String,
)

object DeviceDiagnostics {

    fun cameras(context: Context): List<CameraOption> = runCatching {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        manager.cameraIdList.map { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            val facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "Frontal"
                CameraCharacteristics.LENS_FACING_BACK -> "Trasera"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "Externa / USB"
                else -> "Desconocida"
            }
            val maxSize = characteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.JPEG)
                ?.maxByOrNull { it.width.toLong() * it.height.toLong() }
            val megapixels = maxSize?.let {
                "%.1f MP".format(it.width.toLong() * it.height / 1_000_000.0)
            } ?: "—"
            val resolution = maxSize?.let { "${it.width} × ${it.height}" } ?: "—"
            val level = when (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
                else -> "Desconocido"
            }
            CameraOption(
                id = id,
                facing = facing,
                megapixels = megapixels,
                maxResolution = resolution,
                hardwareLevel = level,
            )
        }
    }.getOrDefault(emptyList())

    fun usbDevices(context: Context): List<UsbDeviceOption> = runCatching {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        manager.deviceList.values.map { device ->
            UsbDeviceOption(
                name = runCatching { device.productName }.getOrNull()
                    ?: runCatching { device.manufacturerName }.getOrNull()
                    ?: device.deviceName,
                vendorId = "0x%04X".format(device.vendorId),
                productId = "0x%04X".format(device.productId),
            )
        }
    }.getOrDefault(emptyList())

    fun networkType(context: Context): String = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            ?: return@runCatching "Sin conexión"
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Otra red"
        }
    }.getOrDefault("Desconocida")

    fun ipv4Addresses(): List<NetworkAddress> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { network ->
                Collections.list(network.inetAddresses)
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                    .map { NetworkAddress(network.name, it.hostAddress ?: "") }
            }
            .filter { it.address.isNotBlank() }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    fun summary(context: Context): DeviceSummary {
        val metrics = context.resources.displayMetrics
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        return DeviceSummary(
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            screen = "${metrics.widthPixels} × ${metrics.heightPixels} px",
            density = "${metrics.densityDpi} dpi (${"%.2f".format(metrics.density)}x)",
            appVersion = packageInfo?.versionName ?: "—",
        )
    }
}

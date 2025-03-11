package ru.Niyaz.btsticker.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.UUID

/**
 * Класс для управления Bluetooth-соединениями
 */
class BluetoothManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BluetoothManager"
        val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Стандартный UUID для SPP
        const val FILTER_ADDRESS_PREFIX = "42:21:BB:2C" // Префикс адреса устройства для фильтра
    }
    
    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    
    /**
     * Проверить, поддерживается ли Bluetooth на устройстве
     * @return true если Bluetooth поддерживается, иначе false
     */
    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }
    
    /**
     * Проверить, включен ли Bluetooth
     * @return true если Bluetooth включен, иначе false
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Начать поиск Bluetooth-устройств
     * @return true если поиск начат успешно, иначе false
     */
    fun startDiscovery(): Boolean {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No BLUETOOTH_SCAN permission")
            return false
        }
        
        // Останавливаем текущий поиск, если он активен
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
        
        return bluetoothAdapter?.startDiscovery() == true
    }
    
    /**
     * Остановить поиск Bluetooth-устройств
     */
    fun stopDiscovery() {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No BLUETOOTH_SCAN permission")
            return
        }
        
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
    }
    
    /**
     * Подключиться к Bluetooth-устройству
     * @param device устройство для подключения
     * @return true если соединение установлено успешно, иначе false
     */
    fun connectToDevice(device: BluetoothDevice): Boolean {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No BLUETOOTH_CONNECT permission")
            return false
        }
        
        // Закрываем текущее соединение, если оно активно
        disconnectFromDevice()
        
        // Останавливаем поиск для более стабильного соединения
        stopDiscovery()
        
        try {
            Log.d(TAG, "Connecting to device: ${device.name}")
            
            // Установка PIN-кода (если необходимо)
            try {
                val pin = BluetoothDevice::class.java.getMethod("convertPinToBytes", String::class.java)
                    .invoke(BluetoothDevice::class.java, "0000") as ByteArray
                device.setPin(pin)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting PIN: ${e.message}")
            }
            
            // Создаем сокет и подключаемся
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            bluetoothSocket?.connect()
            
            return isConnected()
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device: ${e.message}")
            disconnectFromDevice()
            return false
        }
    }
    
    /**
     * Отключиться от текущего Bluetooth-устройства
     */
    fun disconnectFromDevice() {
        try {
            bluetoothSocket?.close()
            bluetoothSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        }
    }
    
    /**
     * Проверить, установлено ли соединение с устройством
     * @return true если соединение установлено, иначе false
     */
    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
    }
    
    /**
     * Отправить данные на подключенное устройство
     * @param data массив байтов для отправки
     * @return true если данные отправлены успешно, иначе false
     */
    fun sendData(data: ByteArray): Boolean {
        if (!isConnected()) {
            Log.e(TAG, "Not connected to any device")
            return false
        }
        
        return try {
            bluetoothSocket?.outputStream?.write(data)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data: ${e.message}")
            false
        }
    }
    
    /**
     * Получить адаптер Bluetooth
     * @return текущий адаптер Bluetooth
     */
    fun getBluetoothAdapter(): BluetoothAdapter? {
        return bluetoothAdapter
    }
}

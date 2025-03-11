package ru.Niyaz.btsticker

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.CompoundButton
import ru.Niyaz.btsticker.bluetooth.BluetoothManager
import ru.Niyaz.btsticker.processors.DitherProcessor
import ru.Niyaz.btsticker.processors.ImageConverter
import ru.Niyaz.btsticker.processors.ImageProcessor
import ru.Niyaz.btsticker.processors.ImageScaler

class MainActivity : AppCompatActivity() {
    
    private lateinit var mImageView: ImageView
    private lateinit var btn_prepare_image_BW: Button
    private lateinit var btn_prepare_image_dithering: Button
    private lateinit var btn_convert_size: Button
    private lateinit var spin_bt: Spinner
    private lateinit var btnRotate: Button
    private lateinit var btnRealSize: Button
    private lateinit var btnRestore: Button
    
    // Компоненты интерфейса для обработки изображений
    private lateinit var spinnerDitherAlgorithm: Spinner
    private lateinit var switchSobel: SwitchMaterial
    private lateinit var seekBarSobel: SeekBar
    private lateinit var seekBarBrightness: SeekBar
    private lateinit var seekBarContrast: SeekBar
    private lateinit var switchInverse: SwitchMaterial
    private lateinit var switchAutoPreview: SwitchMaterial
    private lateinit var seekBarMatrix: SeekBar
    private lateinit var sliderThreshold: Slider
    
    // Переменные для работы с изображениями
    private lateinit var originalBitmap: Bitmap
    private lateinit var preprocessedBitmap: Bitmap
    private var currentScaleType = ImageScaler.Companion.ScaleType.FIT
    
    // Переменные для работы с Bluetooth
    private lateinit var bluetoothManager: BluetoothManager
    private var isDiscovering = false
    private var deviceList = ArrayList<BluetoothDevice>()
    private val deviceNamesMap = HashMap<String, BluetoothDevice>()
    
    // Настройки для алгоритмов дизеринга и масштабирования
    private val DITHER_ALGORITHMS = arrayOf(
        "Floyd-Steinberg",
        "Ordered (Bayer)",
        "Atkinson",
        "Jarvis",
        "Stucki",
        "Burkes",
        "Sierra",
        "Sierra-Lite"
    )
    
    private val SCALE_TYPES = arrayOf(
        "Scale (keep ratio)",
        "Fit (whole image)",
        "Crop (fill target)",
        "Stretch (no ratio)"
    )
    
    private var selectedDitherAlgorithm = 0
    private lateinit var savedUriImage: Uri
    
    // Receiver для обнаружения Bluetooth-устройств
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    
                    device?.let {
                        // Проверяем, соответствует ли устройство фильтру
                        if (it.address.startsWith(BluetoothManager.FILTER_ADDRESS_PREFIX)) {
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                val deviceName = it.name ?: "Unknown Device"
                                val deviceAddress = it.address
                                val deviceInfo = "'$deviceAddress' - '$deviceName'"
                                
                                // Добавляем устройство в список только если его еще нет
                                if (!deviceNamesMap.containsKey(deviceInfo)) {
                                    deviceList.add(it)
                                    deviceNamesMap[deviceInfo] = it
                                    
                                    // Обновляем spinner
                                    val deviceNames = deviceNamesMap.keys.toList()
                                    val adapter = ArrayAdapter(
                                        context,
                                        android.R.layout.simple_spinner_item,
                                        deviceNames
                                    )
                                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                    spin_bt.adapter = adapter
                                }
                            }
                        }
                    }
                }
                android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isDiscovering = false
                    updateDiscoveryButtonText()
                }
            }
        }
    }
    
    // Обработчик результата выбора изображения
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                Log.d(TAG, "Selected image URI: $uri")
                savedUriImage = uri
                
                try {
                    // Загружаем изображение
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    
                    // Автоматически подготавливаем и отображаем изображение
                    preprocessAndShowImage()
                    
                    // Активируем кнопки обработки
                    enableImageProcessingButtons()
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading image: ${e.message}")
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Автоматическая подготовка и отображение изображения
     * Включает масштабирование, авто-поворот и предобработку
     */
    private fun preprocessAndShowImage() {
        if (!::originalBitmap.isInitialized) {
            return
        }
        
        // Размеры принтера: 240x96
        val targetWidth = 240
        val targetHeight = 96
        
        try {
            // Авто-поворот в зависимости от соотношения сторон
            val rotatedBitmap = ImageScaler.autoRotate(originalBitmap, targetWidth, targetHeight)
            
            // Масштабирование согласно выбранному типу
            val scaledBitmap = ImageScaler.scaleImage(rotatedBitmap, targetWidth, targetHeight, currentScaleType)
            
            // Предобработка (яркость, контрастность, Собель)
            val brightnessValue = seekBarBrightness.progress / 100f
            val contrastValue = seekBarContrast.progress / 100f
            var processedBitmap = ImageProcessor.adjustBrightnessContrast(scaledBitmap, brightnessValue, contrastValue)
            
            if (switchSobel.isChecked) {
                val sobelIntensity = seekBarSobel.progress / 100f
                processedBitmap = ImageProcessor.applySobelEdgeDetection(processedBitmap, sobelIntensity)
            }
            
            // Сохраняем обработанное изображение как промежуточное
            preprocessedBitmap = processedBitmap
            
            // Если включен автопросмотр, сразу применяем дизеринг
            if (switchAutoPreview.isChecked) {
                applyDithering()
            } else {
                // Иначе просто показываем предобработанное изображение
                mImageView.setImageBitmap(preprocessedBitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preprocessing image: ${e.message}")
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
        }
    }
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Инициализация BluetoothManager
        bluetoothManager = BluetoothManager(this)
        
        // Настраиваем интент-фильтр для обнаружения Bluetooth-устройств
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
            addAction(android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(bluetoothReceiver, filter)
        
        // Проверяем поддержку Bluetooth
        if (!bluetoothManager.isBluetoothSupported()) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Запрашиваем разрешения Bluetooth
        requestBluetoothPermissions()
        
        // Инициализация UI компонентов
        initializeUI()
        
        // Настраиваем обработчики событий
        setupEventHandlers()
        
        // Изначально отключаем кнопки, требующие изображения или Bluetooth
        disableProcessingButtons()
    }
    
    // Инициализация UI компонентов
    private fun initializeUI() {
        // Основные элементы управления
        mImageView = findViewById(R.id.imageView)
        val ivRealSize = findViewById<ImageView>(R.id.iv_real_size)
        spin_bt = findViewById(R.id.bt_spinner)
        
        // Кнопки
        val btn_open_img = findViewById<Button>(R.id.btn_open_img)
        val btn_send = findViewById<Button>(R.id.btn_send)
        val btn_close = findViewById<Button>(R.id.btn_close)
        val btn_find_device = findViewById<Button>(R.id.btn_find_device)
        val btn_connect = findViewById<Button>(R.id.btn_connect)
        btn_prepare_image_BW = findViewById(R.id.btn_prepare_BW)
        btn_prepare_image_dithering = findViewById(R.id.btn_prepare_dithering)
        btn_convert_size = findViewById(R.id.btn_convert_size)
        btnRotate = findViewById(R.id.btn_rotate)
        btnRealSize = findViewById(R.id.btn_realsize)
        btnRestore = findViewById(R.id.btnRestore)
        
        // Элементы управления обработкой изображений
        spinnerDitherAlgorithm = findViewById(R.id.spinner_dither_algorithm)
        switchSobel = findViewById(R.id.switchSobel)
        seekBarSobel = findViewById(R.id.seekBarSobel)
        seekBarBrightness = findViewById(R.id.seekBarBrightness)
        seekBarContrast = findViewById(R.id.seekBarContrast)
        switchInverse = findViewById(R.id.switchInverse)
        seekBarMatrix = findViewById(R.id.seekBarMatrix)
        sliderThreshold = findViewById(R.id.slider_threshold)
        switchAutoPreview = findViewById(R.id.switchAutoPreview)
        
        // Настройка выпадающего списка алгоритмов дизеринга
        val ditherAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            DITHER_ALGORITHMS
        )
        ditherAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDitherAlgorithm.adapter = ditherAdapter
        
        // Настройка выпадающего списка типов масштабирования
        val scaleTypeSpinner = findViewById<Spinner>(R.id.spinner_scale_type)
        val scaleAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            SCALE_TYPES
        )
        scaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        scaleTypeSpinner.adapter = scaleAdapter
        
        // Скрываем изображение "реального размера"
        ivRealSize.visibility = View.INVISIBLE
    }
    
    // Настройка обработчиков событий
    private fun setupEventHandlers() {
        // Обработчик выбора алгоритма дизеринга
        spinnerDitherAlgorithm.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDitherAlgorithm = position
                if (::preprocessedBitmap.isInitialized && switchAutoPreview.isChecked) {
                    applyDithering()
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedDitherAlgorithm = 0
            }
        }
        
        // Обработчик выбора типа масштабирования
        findViewById<Spinner>(R.id.spinner_scale_type).onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentScaleType = when (position) {
                    0 -> ImageScaler.Companion.ScaleType.SCALE
                    1 -> ImageScaler.Companion.ScaleType.FIT
                    2 -> ImageScaler.Companion.ScaleType.CROP
                    3 -> ImageScaler.Companion.ScaleType.STRETCH
                    else -> ImageScaler.Companion.ScaleType.FIT
                }
                
                if (::originalBitmap.isInitialized && switchAutoPreview.isChecked) {
                    preprocessAndShowImage()
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                currentScaleType = ImageScaler.Companion.ScaleType.FIT
            }
        }
        
        // Обработчики изменения параметров с автообновлением при необходимости
        setupSliderChangeListeners()
        
        // Открытие изображения
        findViewById<Button>(R.id.btn_open_img).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
            }
            imagePickerLauncher.launch(intent)
        }
        
        // Поиск Bluetooth-устройств
        findViewById<Button>(R.id.btn_find_device).setOnClickListener {
            toggleDiscovery()
        }
        
        // Подключение к устройству
        findViewById<Button>(R.id.btn_connect).setOnClickListener {
            connectToSelectedDevice()
        }
        
        // Отключение от устройства
        findViewById<Button>(R.id.btn_close).setOnClickListener {
            disconnectFromDevice()
        }
        
        // Отправка данных
        findViewById<Button>(R.id.btn_send).setOnClickListener {
            sendImageToPrinter()
        }
        
        // Преобразование в черно-белое
        btn_prepare_image_BW.setOnClickListener {
            applyBlackAndWhiteFilter()
        }
        
        // Применение дизеринга
        btn_prepare_image_dithering.setOnClickListener {
            applyDithering()
        }
        
        // Изменение размера
        btn_convert_size.setOnClickListener {
            resizeImage()
        }
        
        // Поворот изображения
        btnRotate.setOnClickListener {
            rotateImage()
        }
        
        // Переключение между обычным и реальным размером
        btnRealSize.setOnClickListener {
            toggleRealSize()
        }
        
        // Восстановление исходного изображения
        btnRestore.setOnClickListener {
            restoreOriginalImage()
        }
    }
    
    // Настройка слушателей изменения параметров
    private fun setupSliderChangeListeners() {
        // Слушатели для ползунков с автоматическим обновлением
        val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && ::preprocessedBitmap.isInitialized && switchAutoPreview.isChecked) {
                    when (seekBar.id) {
                        R.id.seekBarBrightness, R.id.seekBarContrast, R.id.seekBarSobel -> {
                            // Для яркости, контрастности и собеля нужно заново применить предобработку
                            preprocessAndShowImage()
                        }
                        R.id.seekBarMatrix -> {
                            // Для размера матрицы достаточно применить дизеринг
                            applyDithering()
                        }
                    }
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }
        
        // Назначаем слушатель для всех ползунков
        seekBarBrightness.setOnSeekBarChangeListener(seekBarChangeListener)
        seekBarContrast.setOnSeekBarChangeListener(seekBarChangeListener)
        seekBarSobel.setOnSeekBarChangeListener(seekBarChangeListener)
        seekBarMatrix.setOnSeekBarChangeListener(seekBarChangeListener)
        
        // Слушатель для порога
        sliderThreshold.addOnChangeListener { _, value, fromUser ->
            if (fromUser && ::preprocessedBitmap.isInitialized && switchAutoPreview.isChecked) {
                applyDithering()
            }
        }
        
        // Слушатели для переключателей
        val switchChangeListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (::preprocessedBitmap.isInitialized && switchAutoPreview.isChecked) {
                when (buttonView.id) {
                    R.id.switchSobel -> {
                        // Для Собеля нужна предобработка
                        preprocessAndShowImage()
                    }
                    R.id.switchInverse -> {
                        // Для инверсии достаточно применить дизеринг
                        applyDithering()
                    }
                }
            }
        }
        
        switchSobel.setOnCheckedChangeListener(switchChangeListener)
        switchInverse.setOnCheckedChangeListener(switchChangeListener)
    }
    
    // Запрос разрешений для Bluetooth
    private fun requestBluetoothPermissions() {
        val requiredPermissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        ActivityCompat.requestPermissions(
            this,
            requiredPermissions.toTypedArray(),
            REQUEST_BLUETOOTH_PERMISSIONS
        )
    }
    
    // Отключение кнопок обработки
    private fun disableProcessingButtons() {
        btn_prepare_image_BW.isEnabled = false
        btn_prepare_image_dithering.isEnabled = false
        btn_convert_size.isEnabled = false
        btnRotate.isEnabled = false
        btnRealSize.isEnabled = false
        btnRestore.isEnabled = false
        
        findViewById<Button>(R.id.btn_close).isEnabled = false
        findViewById<Button>(R.id.btn_send).isEnabled = false
    }
    
    // Включение кнопок обработки изображения
    private fun enableImageProcessingButtons() {
        btn_prepare_image_BW.isEnabled = true
        btn_prepare_image_dithering.isEnabled = true
        btn_convert_size.isEnabled = true
        btnRotate.isEnabled = true
        btnRealSize.isEnabled = true
        btnRestore.isEnabled = true
    }
    
    // Включение/отключение поиска Bluetooth-устройств
    private fun toggleDiscovery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED) {
            requestBluetoothPermissions()
            return
        }
        
        if (isDiscovering) {
            bluetoothManager.stopDiscovery()
            isDiscovering = false
        } else {
            // Очищаем список устройств перед новым поиском
            deviceList.clear()
            deviceNamesMap.clear()
            
            // Сбрасываем адаптер
            val emptyAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                ArrayList<String>()
            )
            spin_bt.adapter = emptyAdapter
            
            // Запускаем поиск
            isDiscovering = bluetoothManager.startDiscovery()
        }
        
        updateDiscoveryButtonText()
    }
    
    // Обновление текста кнопки поиска устройств
    private fun updateDiscoveryButtonText() {
        val btnFindDevice = findViewById<Button>(R.id.btn_find_device)
        btnFindDevice.text = if (isDiscovering) "Stop scan" else "Find device"
    }
    
    // Подключение к выбранному устройству
    private fun connectToSelectedDevice() {
        if (deviceList.isEmpty() || spin_bt.selectedItemPosition < 0) {
            Toast.makeText(this, "No Bluetooth device selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedDeviceInfo = spin_bt.selectedItem.toString()
        val selectedDevice = deviceNamesMap[selectedDeviceInfo]
        
        if (selectedDevice == null) {
            Toast.makeText(this, "Invalid device selection", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Попытка подключения
        val connected = bluetoothManager.connectToDevice(selectedDevice)
        
        if (connected) {
            // Обновляем состояние UI
            findViewById<Button>(R.id.btn_send).isEnabled = true
            findViewById<Button>(R.id.btn_close).isEnabled = true
            findViewById<Button>(R.id.btn_connect).isEnabled = false
            findViewById<Button>(R.id.btn_find_device).isEnabled = false
            spin_bt.isEnabled = false
            
            // Останавливаем поиск
            bluetoothManager.stopDiscovery()
            isDiscovering = false
            updateDiscoveryButtonText()
            
            Toast.makeText(this, "Connected to device", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to connect to device", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Отключение от устройства
    private fun disconnectFromDevice() {
        bluetoothManager.disconnectFromDevice()
        
        // Обновляем состояние UI
        findViewById<Button>(R.id.btn_close).isEnabled = false
        findViewById<Button>(R.id.btn_send).isEnabled = false
        findViewById<Button>(R.id.btn_connect).isEnabled = true
        findViewById<Button>(R.id.btn_find_device).isEnabled = true
        spin_bt.isEnabled = true
        
        Toast.makeText(this, "Disconnected from device", Toast.LENGTH_SHORT).show()
    }
    
    // Отправка изображения на принтер
    private fun sendImageToPrinter() {
        val bitmap = (mImageView.drawable as? BitmapDrawable)?.bitmap
        
        if (bitmap == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Not connected to any device", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Убеждаемся, что изображение имеет нужный размер 240x96
            val finalBitmap: Bitmap
            if (bitmap.width != 240 || bitmap.height != 96) {
                finalBitmap = ImageProcessor.resizeImage(bitmap, 240, 96)
                Toast.makeText(this, "Image resized to fit printer format", Toast.LENGTH_SHORT).show()
            } else {
                finalBitmap = bitmap
            }
            
            // Преобразуем изображение в формат для принтера
            val data = ImageConverter.convertImageToProtocol(finalBitmap)
            
            // Отправляем данные
            val success = bluetoothManager.sendData(data)
            
            if (success) {
                Toast.makeText(this, "Image sent successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to send image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending image: ${e.message}")
            Toast.makeText(this, "Error sending image", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Применение черно-белого фильтра
    private fun applyBlackAndWhiteFilter() {
        if (!::preprocessedBitmap.isInitialized) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val resultBitmap = ImageProcessor.createBlackAndWhite(
                preprocessedBitmap,
                sliderThreshold.value,
                switchInverse.isChecked
            )
            
            mImageView.setImageBitmap(resultBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying B&W filter: ${e.message}")
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Применение дизеринга к предобработанному изображению
    private fun applyDithering() {
        if (!::preprocessedBitmap.isInitialized) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Применяем выбранный алгоритм дизеринга
            val resultBitmap = when (selectedDitherAlgorithm) {
                0 -> DitherProcessor.floydSteinbergDithering(preprocessedBitmap, sliderThreshold.value.toInt(), switchInverse.isChecked)
                1 -> DitherProcessor.orderedDithering(preprocessedBitmap, seekBarMatrix.progress, sliderThreshold.value.toInt(), switchInverse.isChecked)
                2 -> DitherProcessor.atkinsonDithering(preprocessedBitmap, sliderThreshold.value.toInt(), switchInverse.isChecked)
                3 -> DitherProcessor.jarvisDithering(preprocessedBitmap, sliderThreshold.value.toInt(), switchInverse.isChecked)
                4 -> DitherProcessor.stuckiDithering(preprocessedBitmap, sliderThreshold.value.toInt(), switchInverse.isChecked)
                5 -> DitherProcessor.burkesDithering(preprocessedBitmap, sliderThreshold.value.toInt(), switchInverse.isChecked)
                6 -> DitherProcessor.sierraDithering(preprocessedBitmap, sliderThreshold.value.toInt(), switchInverse.isChecked)
                7 -> DitherProcessor.sierraLiteDithering(preprocessedBitmap, sliderThreshold.value.toInt(), switchInverse.isChecked)
                else -> DitherProcessor.dithering(
                    preprocessedBitmap,
                    sliderThreshold.value.toInt(),
                    seekBarMatrix.progress,
                    switchInverse.isChecked
                )
            }
            
            mImageView.setImageBitmap(resultBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying dithering: ${e.message}")
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Изменение размера изображения до размеров стикера (240x96)
    private fun resizeImage() {
        if (!::originalBitmap.isInitialized) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Изменяем размер до 240x96 (размер стикера 12x30мм)
            val targetWidth = 240
            val targetHeight = 96
            
            // Сбрасываем обработку и применяем масштабирование
            preprocessedBitmap = ImageScaler.scaleImage(originalBitmap, targetWidth, targetHeight, currentScaleType)
            
            // Показываем результат
            if (switchAutoPreview.isChecked) {
                preprocessAndShowImage()
            } else {
                mImageView.setImageBitmap(preprocessedBitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resizing image: ${e.message}")
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Поворот изображения на 90 градусов
    private fun rotateImage() {
        if (!::originalBitmap.isInitialized) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Поворачиваем исходное изображение на 90 градусов
            originalBitmap = ImageProcessor.rotateImage(originalBitmap, 90f)
            
            // Запускаем подготовку изображения заново
            preprocessAndShowImage()
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating image: ${e.message}")
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Переключение между обычным и реальным размером
    private fun toggleRealSize() {
        var ivRealSize = findViewById<ImageView>(R.id.iv_real_size)
        
        if (btnRealSize.text == "Real size") {
            // Переключаемся на реальный размер
            ivRealSize.visibility = View.VISIBLE
            mImageView.visibility = View.INVISIBLE
            ivRealSize.setImageBitmap((mImageView.drawable as BitmapDrawable).bitmap)
            
            // Переназначаем переменные
            val tempImageView = mImageView
            mImageView = ivRealSize
            
            btnRealSize.text = "Normal size"
        } else {
            // Переключаемся на обычный размер
            val tempBitmap = (mImageView.drawable as BitmapDrawable).bitmap
            mImageView = findViewById(R.id.imageView)
            ivRealSize = findViewById(R.id.iv_real_size)
            
            mImageView.setImageBitmap(tempBitmap)
            ivRealSize.visibility = View.INVISIBLE
            mImageView.visibility = View.VISIBLE
            
            btnRealSize.text = "Real size"
        }
    }
    
    // Восстановление исходного изображения
    private fun restoreOriginalImage() {
        if (!::savedUriImage.isInitialized) {
            Toast.makeText(this, "No original image to restore", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Перезагружаем изображение из URI
            val inputStream = contentResolver.openInputStream(savedUriImage)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            
            // Запускаем подготовку заново
            preprocessAndShowImage()
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring image: ${e.message}")
            Toast.makeText(this, "Failed to restore original image", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Обработка результатов запроса разрешений
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Разрешения получены
                    Log.d(TAG, "Bluetooth permissions granted")
                } else {
                    // Разрешения не получены
                    Toast.makeText(this, "Bluetooth permissions required for this app", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // Очистка ресурсов при уничтожении активности
    override fun onDestroy() {
        super.onDestroy()
        
        // Останавливаем поиск устройств
        bluetoothManager.stopDiscovery()
        
        // Отключаемся от устройства
        bluetoothManager.disconnectFromDevice()
        
        // Отменяем регистрацию ресивера
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }
}

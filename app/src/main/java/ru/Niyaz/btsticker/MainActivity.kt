package ru.Niyaz.btsticker

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.UUID


class MainActivity : AppCompatActivity() {

    private lateinit var mImageView: ImageView
    private lateinit var btn_prepare_image_BW: Button
    private lateinit var btn_prepare_image_dithering: Button
    private lateinit var btn_convert_size: Button
    private lateinit var spin_bt: Spinner
    private lateinit var btnRotate: Button
    private lateinit var btnRealSize: Button
    private lateinit var btnRestore: Button

    private var btAdapter: BluetoothAdapter? = null
    private var btSocket: BluetoothSocket? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // store a paired devices
    private var ArrDevice: ArrayList<BluetoothDevice> = ArrayList()

    private lateinit var savedUriImage: Uri
    // monitoring any activity returns
    private val someActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    Log.d("TAG", "Selected file URI: $uri")
                    savedUriImage = uri
                    // set image to imageView
                    val inputStream = this.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    mImageView.setImageBitmap(bitmap)
                    btn_prepare_image_BW.isEnabled = true
                    btn_convert_size.isEnabled = true
                    btnRotate.isEnabled = true
                    btnRealSize.isEnabled = true
                    btnRestore.isEnabled = true
                }
            }
        }

    private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH) }?.also {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }
        btAdapter = BluetoothAdapter.getDefaultAdapter()

        val btn_open_img = findViewById<Button>(R.id.btn_open_img)
        val btn_send = findViewById<Button>(R.id.btn_send)
        val btn_close = findViewById<Button>(R.id.btn_close)
        val btn_find_device = findViewById<Button>(R.id.btn_find_device)
        val btn_connect = findViewById<Button>(R.id.btn_connect)
        var ivRealSize = findViewById<ImageView>(R.id.iv_real_size)


        val seekBarMatrix = findViewById<SeekBar>(R.id.seekBarMatrix)
        val sliderThreshold = findViewById<Slider>(R.id.slider_threshold)
        val switchInverse = findViewById<SwitchMaterial>(R.id.switchInverse)

        btn_prepare_image_BW = findViewById(R.id.btn_prepare_BW)
        btn_prepare_image_dithering = findViewById(R.id.btn_prepare_dithering)
        mImageView = findViewById(R.id.imageView)
        spin_bt = findViewById(R.id.bt_spinner)
        btn_convert_size = findViewById(R.id.btn_convert_size)
        btnRotate = findViewById(R.id.btn_rotate)
        btnRealSize = findViewById(R.id.btn_realsize)
        btnRestore = findViewById<Button>(R.id.btnRestore)


        // disable buttons
        btn_close.isEnabled = false
        btn_send.isEnabled = false
        btn_prepare_image_BW.isEnabled = false
        btn_convert_size.isEnabled = false
        btnRotate.isEnabled = false
        btnRealSize.isEnabled = false
        btnRestore.isEnabled = false
        ivRealSize.visibility = View.INVISIBLE

        // set onClickListener to open Image button
        btn_open_img.setOnClickListener(View.OnClickListener { view ->
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*" // or "image/*", "audio/*", etc.

            // Start activity with the ActivityResultLauncher
            someActivityResultLauncher.launch(intent)


        })

        // find Bluetooth device and fill scroll view with data
        btn_find_device.setOnClickListener(View.OnClickListener { view ->
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 2)
                }
            }
            val arrDeviceString: ArrayList<String> = ArrayList()
            ArrDevice = ArrayList()
            val pairedDevices: Set<BluetoothDevice>? = btAdapter?.bondedDevices
            pairedDevices?.forEach { device ->
                val deviceName = device.name
                val deviceHardwareAddress = device.address // MAC address

                ArrDevice.add(device)
                arrDeviceString.add("'$deviceHardwareAddress' - '$deviceName'")


            }
            val adapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item, arrDeviceString)

            spin_bt.adapter = adapter
        })

        // connect to selected device by spinner
        btn_connect.setOnClickListener(View.OnClickListener { view ->
            if(ArrDevice.isEmpty()) {
                Toast.makeText(this,"not selected bt device",Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            Log.d("data",spin_bt.selectedItem.toString())
            val status = connectToBluetoothDevice(ArrDevice[spin_bt.selectedItemPosition])
            if(status) {
                btn_send.isEnabled = true
                btn_close.isEnabled = true
                btn_connect.isEnabled = false
                btn_find_device.isEnabled = false
                spin_bt.isEnabled = false
            }
            else
                Toast.makeText(this,"Can't connect to device!!!",Toast.LENGTH_SHORT).show()
        })

        // close BT connection and unblock buttons
        btn_close.setOnClickListener( View.OnClickListener {view ->
            disconnectFromBluetoothDevice()
            btn_close.isEnabled = false
            btn_send.isEnabled = false
            btn_connect.isEnabled = true
            btn_find_device.isEnabled = true
            spin_bt.isEnabled = true
        })

        // send data to BT device
        btn_send.setOnClickListener( View.OnClickListener {view ->
            val mOutputStream = btSocket?.outputStream
            val bitmap = (mImageView.getDrawable() as BitmapDrawable?)?.bitmap
            if(bitmap == null) {
                Toast.makeText(this,"image not selected!",Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            Log.d("btn_send","size img:${bitmap.height}*${bitmap.width}")
            val data = convertImageToProtocol(bitmap)
            mOutputStream?.write(data)
        })

        // prepare image by dithering (Black and white by algorithm dithering)
        btn_prepare_image_dithering.setOnClickListener(View.OnClickListener { view ->
            val bitmap = (mImageView.getDrawable() as BitmapDrawable?)?.bitmap
            if(bitmap == null) {
                Toast.makeText(this,"image not selected!",Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            Log.d("btn_prepare_image","size img:${bitmap.height}*${bitmap.width}")

            val bitmap_monocrome = dithering(
                bitmap,
                sliderThreshold.value.toInt(),
                seekBarMatrix.progress,
                switchInverse.isChecked
            )
            mImageView.setImageBitmap(bitmap_monocrome)

        })

        // prepare image by cast monochrome filter on image
        btn_prepare_image_BW.setOnClickListener(View.OnClickListener { view ->
            val bitmap = (mImageView.getDrawable() as BitmapDrawable?)?.bitmap
            if(bitmap == null) {
                Toast.makeText(this,"image not selected!",Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            Log.d("btn_prepare_image","size img:${bitmap.height}*${bitmap.width}")
            val bitmap_monocrome = createBlackAndWhite(bitmap,sliderThreshold.value,switchInverse.isChecked)
            // commented because added rotate button
//            if(bitmap_monocrome?.height!! > bitmap_monocrome.width) {
//                val matrix = Matrix()
//
//                matrix.postRotate(90F)
//                bitmap_monocrome = Bitmap.createBitmap(bitmap_monocrome, 0, 0, bitmap_monocrome.getWidth(), bitmap_monocrome.getHeight(), matrix, true);
//            }
            mImageView.setImageBitmap(bitmap_monocrome)

        })

        // rotate image to 90 degrees
        btnRotate.setOnClickListener(View.OnClickListener { view ->
            var bitmap = (mImageView.getDrawable() as BitmapDrawable?)?.bitmap
            if(bitmap == null) {
                Toast.makeText(this,"image not selected!",Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            Log.d("btnRotate","size img:${bitmap.height}*${bitmap.width}")
            val matrix = Matrix()

            matrix.postRotate(90F)
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            mImageView.setImageBitmap(bitmap)
        })

        // prepare image by convert size to 96*240px(size of sticker 12*30mm)
        btn_convert_size.setOnClickListener(View.OnClickListener {view ->
            val bitmap = (mImageView.getDrawable() as BitmapDrawable?)?.bitmap
            if(bitmap == null) {
                Toast.makeText(this,"image not selected!",Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            Log.d("btn_convert_size","size img:${bitmap.height}*${bitmap.width}")
            mImageView.setImageBitmap(Bitmap.createScaledBitmap(bitmap, 240, 96, false));
        })

        // selected other imageView
        btnRealSize.setOnClickListener(View.OnClickListener { view ->
            if(btnRealSize.text == "Real size") {
                ivRealSize.visibility = View.VISIBLE
                mImageView.visibility = View.INVISIBLE
                ivRealSize.setImageBitmap((mImageView.getDrawable() as BitmapDrawable?)?.bitmap!!)
                mImageView = findViewById(R.id.iv_real_size)
                ivRealSize = findViewById(R.id.imageView)
                btnRealSize.text = "Normal size"
            } else {
                ivRealSize.setImageBitmap((mImageView.getDrawable() as BitmapDrawable?)?.bitmap!!)
                mImageView = findViewById(R.id.imageView)
                ivRealSize = findViewById(R.id.iv_real_size)
                btnRealSize.text = "Real size"
                ivRealSize.visibility = View.INVISIBLE
                mImageView.visibility = View.VISIBLE
            }
        })

        btnRestore.setOnClickListener(View.OnClickListener{view ->
            val inputStream = this.contentResolver.openInputStream(savedUriImage)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            mImageView.setImageBitmap(bitmap)
        })
    }

    fun connectToBluetoothDevice(device: BluetoothDevice): Boolean {
        try {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 2)
                }
            }

            Log.d("device", device.name.toString())
            val pin = BluetoothDevice::class.java.getMethod("convertPinToBytes", String::class.java)
                .invoke(
                    BluetoothDevice::class.java, "0000"
                ) as ByteArray

            device.setPin(pin)
            btSocket = device.createRfcommSocketToServiceRecord(uuid)
            btSocket?.connect()
        } catch (e: Exception) {
            // Handle exceptions
            Log.e("ERROR",e.message.toString())
            return false
        }
        return true
    }

    fun disconnectFromBluetoothDevice() {
        try {
            btSocket?.close()
        } catch (e: Exception) {
            // Handle exceptions
        }
    }

    fun createBlackAndWhite(src: Bitmap,factor: Float = 255f, isinverted: Boolean = false, briColor: Array<Float> = arrayOf<Float>(0.299f,0.587f,0.114f)): Bitmap? {
        val width = src.width
        val height = src.height
        val bmOut = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // val redBri = 0.2126f
        // val greenBri = 0.2126f
        // val blueBri = 0.0722f
        val length = width * height
        val inpixels = IntArray(length)
        val oupixels = IntArray(length)
        src.getPixels(inpixels, 0, width, 0, 0, width, height)
        var point = 0
        for (pix in inpixels) {
            val R = pix shr 16 and 0xFF
            val G = pix shr 8 and 0xFF
            val B = pix and 0xFF
            val lum = briColor[0] * R / factor + briColor[1] * G / factor + briColor[2] * B / factor
            if (lum > 0.4) {
                oupixels[point] = -0x1
            } else {
                oupixels[point] = -0x1000000
            }
            if(isinverted) {
                oupixels[point] = if (oupixels[point] == -0x1){
                    -0x1000000
                } else {
                    -0x1
                }
            }
            point++
        }
        bmOut.setPixels(oupixels, 0, width, 0, 0, width, height)
        return bmOut
    }

    fun dithering(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val binaryBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val redBri = 0.2989f
        val greenBri = 0.5870f
        val blueBri = 0.1140f
        // матрица дизеринга 2x2
        val ditherMatrix = arrayOf(
            intArrayOf(0, 2),
            intArrayOf(3, 1)
        )

        // проходим по каждому пикселю картинки
        for (y in 0 until height) {
            for (x in 0 until width) {
                // получаем RGB значения пикселя
                val color = bitmap.getPixel(x, y)
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)

                // преобразуем в оттенки серого
                val gray = (redBri * r + greenBri * g + blueBri * b).toInt()

                // применяем матрицу дизеринга
                if (gray > ditherMatrix[x % 2][y % 2] * 255 / 4) {
                    binaryBitmap.setPixel(x, y, Color.WHITE)
                } else {
                    binaryBitmap.setPixel(x, y, Color.BLACK)
                }
            }
        }

        return binaryBitmap
    }

    fun dithering(bitmap: Bitmap, threshold: Int = 255, matrixSize: Int = 8, isinverted: Boolean = false, briColor: Array<Float> = arrayOf<Float>(0.299f,0.587f,0.114f)): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val matrix = Array(matrixSize) { FloatArray(matrixSize) }
        for (i in 0 until matrixSize) {
            for (j in 0 until matrixSize) {
                matrix[i][j] = (i * matrixSize + j) / (matrixSize * matrixSize).toFloat()
            }
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val oldPixel = bitmap.getPixel(x, y)
                val oldPixelGray = (briColor[0] * Color.red(oldPixel) +
                        briColor[1] * Color.green(oldPixel) +
                        briColor[2] * Color.blue(oldPixel)).toInt()

                val matrixX = x % matrixSize
                val matrixY = y % matrixSize
                var newPixel: Int
                if (oldPixelGray + matrix[matrixY][matrixX] * 255 - threshold >= 0) {
                    newPixel = Color.WHITE
                } else {
                    newPixel = Color.BLACK
                }
                if(isinverted) {
                    newPixel = if(newPixel == Color.BLACK) {
                         Color.WHITE
                    } else {
                        Color.BLACK
                    }
                }
                resultBitmap.setPixel(x, y, newPixel)
            }
        }
        return resultBitmap
    }




    fun convertImageToProtocol(src: Bitmap): ByteArray {
        var ret: String = ""

        val sizeHex = "0b44" // size in bytes 96*240px + 4 byte
        val header = "dd000102${sizeHex}000c0100"
        val end = "00dd"


        val width = src.width
        val height = src.height
        var resultByteArray = header.decodeHex()

        // Я не знаю почему, но изображение получается перевернутным... Виноват алгоритм, но мне лень его чинить, по этому пускай будет так

        // отзеркалим изображение
        val matrix = Matrix()
        matrix.setScale(-1f, 1f)
        val miroredSrc = Bitmap.createBitmap(src,0, 0, src.getWidth(), src.getHeight(), matrix, true)
        val byteArray = bitmapToByteArray(miroredSrc)

        resultByteArray = resultByteArray.plus(byteArray).plus(end.decodeHex())
        return resultByteArray
    }

    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val byteArray = ByteArray(bitmap.width * bitmap.height / 8)

        var index = 0
        var bitIndex = 0
        var byte = 0

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)

                // Так как изображение черно-белое, то красный компонент RGB
                // является наиболее значимым для определения уровня яркости.
                // Получим значение красного компонента и проверим, является ли оно
                // больше 128 (половина 255, максимального значения комопонента).
                // Если да, то бит в позиции bitIndex должен быть установлен в 1.
                // В противном случае бит должен оставаться нулевым.
                val grayScale = Color.red(pixel)
                val bit = if (grayScale > 128) 1 else 0

                // Установим бит в позиции bitIndex в круглый байт byte
                byte = byte or (bit shl 7 - bitIndex)

                // Если мы заполнили 8 битов в байте, то поместим его в массив
                // и перейдем к следующему байту.
                if (++bitIndex == 8) {
                    byteArray[index++] = byte.toByte()
                    byte = 0
                    bitIndex = 0
                }
            }
        }

        return byteArray
    }

    fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
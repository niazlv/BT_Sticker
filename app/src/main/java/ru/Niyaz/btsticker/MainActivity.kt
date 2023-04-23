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
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.PinnedPositions.pin
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.get
import java.nio.ByteBuffer
import java.util.UUID


class MainActivity : AppCompatActivity() {

    private lateinit var mImageView: ImageView
    private lateinit var btn_prepare_image: Button
    private lateinit var btn_convert_size: Button
    private lateinit var spin_bt: Spinner
    private lateinit var label: TextView

    private var btAdapter: BluetoothAdapter? = null
    private var btSocket: BluetoothSocket? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // store a paired devices
    private var ArrDevice: ArrayList<BluetoothDevice> = ArrayList()

    // monitoring any activity returns
    private val someActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    Log.d("TAG", "Selected file URI: $uri")
                    // set image to imageView
                    val inputStream = this.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    mImageView.setImageBitmap(bitmap)
                    btn_prepare_image.isEnabled = true
                    btn_convert_size.isEnabled = true
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

        btn_prepare_image = findViewById(R.id.btn_prepare)
        mImageView = findViewById(R.id.imageView)
        spin_bt = findViewById(R.id.bt_spinner)
        btn_convert_size = findViewById(R.id.btn_convert_size)
        label = findViewById(R.id.label)


        // disable buttons
        btn_close.isEnabled = false
        btn_send.isEnabled = false
        btn_prepare_image.isEnabled = false
        btn_convert_size.isEnabled = false

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

        // prepare image by cast monochrome filter on image
        btn_prepare_image.setOnClickListener(View.OnClickListener { view ->
            val bitmap = (mImageView.getDrawable() as BitmapDrawable?)?.bitmap
            if(bitmap == null) {
                Toast.makeText(this,"image not selected!",Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            Log.d("btn_prepare_image","size img:${bitmap.height}*${bitmap.width}")
            var bitmap_monocrome = createBlackAndWhite(bitmap)
            if(bitmap_monocrome?.height!! > bitmap_monocrome.width) {
                val matrix = Matrix()

                matrix.postRotate(90F)
                bitmap_monocrome = Bitmap.createBitmap(bitmap_monocrome, 0, 0, bitmap_monocrome.getWidth(), bitmap_monocrome.getHeight(), matrix, true);
            }
            mImageView.setImageBitmap(bitmap_monocrome)

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

    fun createBlackAndWhite(src: Bitmap): Bitmap? {
        val width = src.width
        val height = src.height
        val bmOut = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val factor = 255f
        val redBri = 0.2126f
        val greenBri = 0.2126f
        val blueBri = 0.0722f
        val length = width * height
        val inpixels = IntArray(length)
        val oupixels = IntArray(length)
        src.getPixels(inpixels, 0, width, 0, 0, width, height)
        var point = 0
        for (pix in inpixels) {
            val R = pix shr 16 and 0xFF
            val G = pix shr 8 and 0xFF
            val B = pix and 0xFF
            val lum = redBri * R / factor + greenBri * G / factor + blueBri * B / factor
            if (lum > 0.4) {
                oupixels[point] = -0x1
            } else {
                oupixels[point] = -0x1000000
            }
            point++
        }
        bmOut.setPixels(oupixels, 0, width, 0, 0, width, height)
        return bmOut
    }

    fun convertImageToProtocol(src: Bitmap): ByteArray {
        var ret: String = ""

        val sizeHex = "0b44" // size in bytes 96*240px + 4 byte
        val header = "dd000102${sizeHex}000c0100"
        val end = "00dd"


        val width = src.width
        val height = src.height
        var resultByteArray = header.decodeHex()
        val matrix = Matrix()
        matrix.setScale(-1f, 1f)
        val miroredSrc = Bitmap.createBitmap(src,0, 0, src.getWidth(), src.getHeight(), matrix, true)
        val byteArray = bitmapToByteArray(miroredSrc)
        resultByteArray = resultByteArray.plus(byteArray).plus(end.decodeHex())
        ret = resultByteArray.toUByteArray().contentToString()

        Log.i("convertImageToProtocol",ret)
        label.text = ret
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
package ru.Niyaz.btsticker.processors

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix

/**
 * Класс для конвертации изображений в формат для отправки на принтер
 */
class ImageConverter {
    companion object {
        /**
         * Конвертировать изображение в формат протокола принтера
         * @param bitmap исходное изображение (должно быть черно-белым)
         * @return массив байтов в формате протокола принтера
         */
        fun convertImageToProtocol(bitmap: Bitmap): ByteArray {
            // Заголовок протокола
            val sizeHex = "0b44" // размер в байтах 96*240px + 4 байта
            val header = "dd000102${sizeHex}000c0100"
            val end = "00dd"
            
            // Начинаем с заголовка
            var resultByteArray = header.decodeHex()
            
            // Отзеркаливаем изображение
            val matrix = Matrix()
            matrix.setScale(-1f, 1f)
            val mirroredBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            
            // Преобразуем изображение в массив байтов
            val imageBytes = bitmapToByteArray(mirroredBitmap)
            
            // Добавляем данные изображения и окончание протокола
            resultByteArray = resultByteArray.plus(imageBytes).plus(end.decodeHex())
            
            return resultByteArray
        }
        
        /**
         * Преобразовать изображение в массив байтов (1 бит на пиксель)
         * @param bitmap исходное черно-белое изображение
         * @return массив байтов с битовым представлением изображения
         */
        private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
            val width = bitmap.width
            val height = bitmap.height
            val byteArray = ByteArray(width * height / 8)
            
            var index = 0
            var bitIndex = 0
            var byte = 0
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val pixel = bitmap.getPixel(x, y)
                    
                    // Определяем яркость пикселя и преобразуем в бит
                    val grayScale = Color.red(pixel)
                    val bit = if (grayScale > 128) 1 else 0
                    
                    // Устанавливаем соответствующий бит в байте
                    byte = byte or (bit shl 7 - bitIndex)
                    
                    // Если заполнили 8 бит, добавляем байт в массив
                    if (++bitIndex == 8) {
                        byteArray[index++] = byte.toByte()
                        byte = 0
                        bitIndex = 0
                    }
                }
            }
            
            return byteArray
        }
        
        /**
         * Преобразовать шестнадцатеричную строку в массив байтов
         * @param hexString шестнадцатеричная строка
         * @return массив байтов
         */
        private fun String.decodeHex(): ByteArray {
            check(length % 2 == 0) { "Must have an even length" }
            
            return chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }
    }
}

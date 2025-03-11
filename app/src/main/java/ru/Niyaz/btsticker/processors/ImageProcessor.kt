package ru.Niyaz.btsticker.processors

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.roundToInt

/**
 * Класс для предварительной обработки изображений
 */
class ImageProcessor {
    companion object {
        /**
         * Отрегулировать яркость и контрастность изображения
         * @param src исходное изображение
         * @param brightness значение яркости (0.0-2.0, 1.0 = без изменений)
         * @param contrast значение контрастности (0.0-2.0, 1.0 = без изменений)
         * @return обработанное изображение
         */
        fun adjustBrightnessContrast(src: Bitmap, brightness: Float, contrast: Float): Bitmap {
            val width = src.width
            val height = src.height
            val bmOut = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            val paint = Paint()
            val canvas = Canvas(bmOut)
            
            // Настраиваем матрицу цветокоррекции
            val cm = ColorMatrix()
            
            // Настройка яркости
            cm.set(floatArrayOf(
                1f, 0f, 0f, 0f, brightness * 255 - 128,
                0f, 1f, 0f, 0f, brightness * 255 - 128,
                0f, 0f, 1f, 0f, brightness * 255 - 128,
                0f, 0f, 0f, 1f, 0f
            ))
            
            // Применяем контрастность
            val scale = contrast * 2
            val translate = (-.5f * scale + .5f) * 255f
            
            val contrastMatrix = ColorMatrix()
            contrastMatrix.set(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            
            // Объединяем матрицы
            cm.postConcat(contrastMatrix)
            
            // Применяем матрицу к изображению
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(src, 0f, 0f, paint)
            
            return bmOut
        }
        
        /**
         * Применить алгоритм обнаружения краев (Собель)
         * @param src исходное изображение
         * @param intensity интенсивность эффекта (0.0-1.0)
         * @return изображение с выделенными краями
         */
        fun applySobelEdgeDetection(src: Bitmap, intensity: Float): Bitmap {
            val width = src.width
            val height = src.height
            val bmOut = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Преобразуем в оттенки серого
            val grayBitmap = toGrayscale(src)
            
            // Ядра фильтра Собеля
            val kernelX = arrayOf(
                intArrayOf(-1, 0, 1),
                intArrayOf(-2, 0, 2),
                intArrayOf(-1, 0, 1)
            )
            
            val kernelY = arrayOf(
                intArrayOf(-1, -2, -1),
                intArrayOf(0, 0, 0),
                intArrayOf(1, 2, 1)
            )
            
            // Массивы для хранения пикселей
            val pixelsIn = IntArray(width * height)
            val pixelsOut = IntArray(width * height)
            grayBitmap.getPixels(pixelsIn, 0, width, 0, 0, width, height)
            
            // Применяем фильтр Собеля
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    var sumX = 0
                    var sumY = 0
                    
                    // Применяем ядра
                    for (ky in -1..1) {
                        for (kx in -1..1) {
                            val pixel = pixelsIn[(y + ky) * width + (x + kx)]
                            val gray = Color.red(pixel) // R=G=B для grayscale
                            
                            sumX += gray * kernelX[ky + 1][kx + 1]
                            sumY += gray * kernelY[ky + 1][kx + 1]
                        }
                    }
                    
                    // Вычисляем градиент
                    val gradient = kotlin.math.sqrt((sumX * sumX + sumY * sumY).toDouble()).toInt()
                    val clampedGradient = if (gradient > 255) 255 else gradient
                    
                    // Текущий пиксель
                    val currentPixel = pixelsIn[y * width + x]
                    
                    // Смешиваем исходное изображение с выделенными краями
                    val r = (Color.red(currentPixel) * (1 - intensity) + clampedGradient * intensity).toInt()
                    val g = (Color.green(currentPixel) * (1 - intensity) + clampedGradient * intensity).toInt()
                    val b = (Color.blue(currentPixel) * (1 - intensity) + clampedGradient * intensity).toInt()
                    
                    pixelsOut[y * width + x] = Color.rgb(r, g, b)
                }
            }
            
            // Копируем границы изображения
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (y == 0 || y == height - 1 || x == 0 || x == width - 1) {
                        pixelsOut[y * width + x] = pixelsIn[y * width + x]
                    }
                }
            }
            
            bmOut.setPixels(pixelsOut, 0, width, 0, 0, width, height)
            return bmOut
        }
        
        /**
         * Преобразовать изображение в черно-белое
         * @param src исходное изображение
         * @param threshold порог для преобразования (0-255)
         * @param isInverted инвертировать ли цвета
         * @param briColor коэффициенты преобразования RGB в яркость
         * @return черно-белое изображение
         */
        fun createBlackAndWhite(src: Bitmap, threshold: Float = 128f, isInverted: Boolean = false, briColor: Array<Float> = arrayOf(0.299f, 0.587f, 0.114f)): Bitmap {
            val width = src.width
            val height = src.height
            val bmOut = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            val length = width * height
            val inpixels = IntArray(length)
            val oupixels = IntArray(length)
            src.getPixels(inpixels, 0, width, 0, 0, width, height)
            
            var point = 0
            for (pix in inpixels) {
                val R = pix shr 16 and 0xFF
                val G = pix shr 8 and 0xFF
                val B = pix and 0xFF
                val lum = briColor[0] * R / threshold + briColor[1] * G / threshold + briColor[2] * B / threshold
                
                if (lum > 0.4) {
                    oupixels[point] = Color.WHITE
                } else {
                    oupixels[point] = Color.BLACK
                }
                
                if (isInverted) {
                    oupixels[point] = if (oupixels[point] == Color.WHITE) {
                        Color.BLACK
                    } else {
                        Color.WHITE
                    }
                }
                point++
            }
            
            bmOut.setPixels(oupixels, 0, width, 0, 0, width, height)
            return bmOut
        }
        
        /**
         * Изменить размер изображения
         * @param src исходное изображение
         * @param newWidth новая ширина
         * @param newHeight новая высота
         * @param filter использовать ли фильтр для лучшего качества
         * @return изображение с новыми размерами
         */
        fun resizeImage(src: Bitmap, newWidth: Int, newHeight: Int, filter: Boolean = false): Bitmap {
            return Bitmap.createScaledBitmap(src, newWidth, newHeight, filter)
        }
        
        /**
         * Повернуть изображение на заданный угол
         * @param src исходное изображение
         * @param degrees угол поворота в градусах
         * @return повернутое изображение
         */
        fun rotateImage(src: Bitmap, degrees: Float): Bitmap {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(degrees)
            return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        }
        
        /**
         * Преобразовать изображение в оттенки серого
         * @param src исходное изображение
         * @return изображение в оттенках серого
         */
        fun toGrayscale(src: Bitmap): Bitmap {
            val width = src.width
            val height = src.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = src.getPixel(x, y)
                    val gray = (Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f).roundToInt()
                    result.setPixel(x, y, Color.rgb(gray, gray, gray))
                }
            }
            
            return result
        }
    }
}

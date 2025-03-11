package ru.Niyaz.btsticker.processors

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/**
 * Класс для обработки изображений с использованием различных алгоритмов дизеринга
 */
class DitherProcessor {
    companion object {
        /**
         * Дизеринг методом Флойда-Стейнберга
         * @param src исходное изображение
         * @param threshold порог для преобразования в черно-белое (0-255)
         * @param isInverted инвертировать ли цвета
         * @return черно-белое изображение с дизерингом
         */
        fun floydSteinbergDithering(src: Bitmap, threshold: Int = 128, isInverted: Boolean = false): Bitmap {
            val width = src.width
            val height = src.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Создаем массив оттенков серого
            val grayPixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = src.getPixel(x, y)
                    val gray = Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f
                    grayPixels[y * width + x] = gray.roundToInt()
                }
            }
            
            // Алгоритм Флойда-Стейнберга
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    val oldPixel = grayPixels[index]
                    val newPixel = if (oldPixel < threshold) 0 else 255
                    
                    result.setPixel(x, y, if ((newPixel == 0) xor isInverted) 
                        Color.BLACK else Color.WHITE)
                    
                    val quantError = oldPixel - newPixel
                    
                    // Распространение ошибки на соседние пиксели
                    if (x + 1 < width)
                        grayPixels[index + 1] += (quantError * 7 / 16)
                    if (y + 1 < height) {
                        if (x > 0)
                            grayPixels[index + width - 1] += (quantError * 3 / 16)
                        grayPixels[index + width] += (quantError * 5 / 16)
                        if (x + 1 < width)
                            grayPixels[index + width + 1] += (quantError * 1 / 16)
                    }
                }
            }
            
            return result
        }
        
        /**
         * Дизеринг упорядоченный (матрица Байера)
         * @param src исходное изображение
         * @param matrixSize размер матрицы дизеринга (2, 4, 8)
         * @param threshold порог для преобразования в черно-белое (0-255)
         * @param isInverted инвертировать ли цвета
         * @return черно-белое изображение с дизерингом
         */
        fun orderedDithering(src: Bitmap, matrixSize: Int = 4, threshold: Int = 128, isInverted: Boolean = false): Bitmap {
            val width = src.width
            val height = src.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Создаем матрицу Байера соответствующего размера
            val size = when (matrixSize) {
                2 -> 2
                4 -> 4
                8 -> 8
                else -> 4
            }
            
            val bayerMatrix = generateBayerMatrix(size)
            
            // Применяем матрицу дизеринга
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = src.getPixel(x, y)
                    val gray = (Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f).roundToInt()
                    
                    val matrixX = x % size
                    val matrixY = y % size
                    val bayerValue = (bayerMatrix[matrixY][matrixX] * 255f / (size * size)).roundToInt()
                    
                    val newPixel = if (gray + bayerValue - threshold/2 >= threshold) Color.WHITE else Color.BLACK
                    
                    result.setPixel(x, y, if (isInverted) invertColor(newPixel) else newPixel)
                }
            }
            
            return result
        }
        
        /**
         * Дизеринг по методу Аткинсона
         * @param src исходное изображение
         * @param threshold порог для преобразования в черно-белое (0-255)
         * @param isInverted инвертировать ли цвета
         * @return черно-белое изображение с дизерингом
         */
        fun atkinsonDithering(src: Bitmap, threshold: Int = 128, isInverted: Boolean = false): Bitmap {
            val width = src.width
            val height = src.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Создаем массив оттенков серого
            val grayPixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = src.getPixel(x, y)
                    val gray = Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f
                    grayPixels[y * width + x] = gray.roundToInt()
                }
            }
            
            // Алгоритм Аткинсона
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    val oldPixel = grayPixels[index]
                    val newPixel = if (oldPixel < threshold) 0 else 255
                    
                    result.setPixel(x, y, if ((newPixel == 0) xor isInverted) 
                        Color.BLACK else Color.WHITE)
                    
                    val quantError = (oldPixel - newPixel) / 8
                    
                    // Распространение ошибки по Аткинсону (1/8 на каждый из 6 соседних пикселей)
                    if (x + 1 < width)
                        grayPixels[index + 1] += quantError
                    if (x + 2 < width)
                        grayPixels[index + 2] += quantError
                    if (y + 1 < height) {
                        if (x > 0)
                            grayPixels[index + width - 1] += quantError
                        grayPixels[index + width] += quantError
                        if (x + 1 < width)
                            grayPixels[index + width + 1] += quantError
                    }
                    if (y + 2 < height)
                        grayPixels[index + width * 2] += quantError
                }
            }
            
            return result
        }
        
        /**
         * Дизеринг по методу Джарвиса, Джадайса и Нинка
         * @param src исходное изображение
         * @param threshold порог для преобразования в черно-белое (0-255)
         * @param isInverted инвертировать ли цвета
         * @return черно-белое изображение с дизерингом
         */
        fun jarvisDithering(src: Bitmap, threshold: Int = 128, isInverted: Boolean = false): Bitmap {
            val width = src.width
            val height = src.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Создаем массив оттенков серого
            val grayPixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = src.getPixel(x, y)
                    val gray = Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f
                    grayPixels[y * width + x] = gray.roundToInt()
                }
            }
            
            // Матрица Джарвиса
            val coefficients = arrayOf(
                intArrayOf(0, 0, 0, 7, 5),
                intArrayOf(3, 5, 7, 5, 3),
                intArrayOf(1, 3, 5, 3, 1)
            )
            
            // Алгоритм Джарвиса
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    val oldPixel = grayPixels[index]
                    val newPixel = if (oldPixel < threshold) 0 else 255
                    
                    result.setPixel(x, y, if ((newPixel == 0) xor isInverted) 
                        Color.BLACK else Color.WHITE)
                    
                    val quantError = oldPixel - newPixel
                    
                    // Распространение ошибки по Джарвису
                    for (ky in 0 until 3) {
                        for (kx in 0 until 5) {
                            val nx = x + kx - 2
                            val ny = y + ky
                            
                            if (nx >= 0 && nx < width && ny < height) {
                                val factor = coefficients[ky][kx]
                                if (factor > 0) {
                                    grayPixels[ny * width + nx] += quantError * factor / 48
                                }
                            }
                        }
                    }
                }
            }
            
            return result
        }
        
        /**
         * Дизеринг по методу Штуки
         * @param src исходное изображение
         * @param threshold порог для преобразования в черно-белое (0-255)
         * @param isInverted инвертировать ли цвета
         * @return черно-белое изображение с дизерингом
         */
        fun stuckiDithering(src: Bitmap, threshold: Int = 128, isInverted: Boolean = false): Bitmap {
            val width = src.width
            val height = src.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Создаем массив оттенков серого
            val grayPixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = src.getPixel(x, y)
                    val gray = Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f
                    grayPixels[y * width + x] = gray.roundToInt()
                }
            }
            
            // Матрица Штуки
            val coefficients = arrayOf(
                intArrayOf(0, 0, 0, 8, 4),
                intArrayOf(2, 4, 8, 4, 2),
                intArrayOf(1, 2, 4, 2, 1)
            )
            
            // Алгоритм Штуки
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    val oldPixel = grayPixels[index]
                    val newPixel = if (oldPixel < threshold) 0 else 255
                    
                    result.setPixel(x, y, if ((newPixel == 0) xor isInverted) 
                        Color.BLACK else Color.WHITE)
                    
                    val quantError = oldPixel - newPixel
                    
                    // Распространение ошибки по Штуки
                    for (ky in 0 until 3) {
                        for (kx in 0 until 5) {
                            val nx = x + kx - 2
                            val ny = y + ky
                            
                            if (nx >= 0 && nx < width && ny < height) {
                                val factor = coefficients[ky][kx]
                                if (factor > 0) {
                                    grayPixels[ny * width + nx] += quantError * factor / 42
                                }
                            }
                        }
                    }
                }
            }
            
            return result
        }
        
        /**
         * Дизеринг по методу Берка
         * @param src исходное изображение
         * @param threshold порог для преобразования в черно-белое (0-255)
         * @param isInverted инвертировать ли цвета
         * @return черно-белое изображение с дизерингом
         */
        fun burkesDithering(src: Bitmap, threshold: Int = 128, isInverted: Boolean = false): Bitmap {
            val width = src.width
            val height = src.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Создаем массив оттенков серого
            val grayPixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = src.getPixel(x, y)
                    val gray = Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f
                    grayPixels[y * width + x] = gray.roundToInt()
                }
            }
            
            // Алгоритм Берка
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    val oldPixel = grayPixels[index]
                    val newPixel = if (oldPixel < threshold) 0 else 255
                    
                    result.setPixel(x, y, if ((newPixel == 0) xor isInverted) 
                        Color.BLACK else Color.WHITE)
                    
                    val quantError = oldPixel - newPixel
                    
                    // Распространение ошибки по Берку
                    if (x + 1 < width)
                        grayPixels[index + 1] += quantError * 8 / 32
                    if (x + 2 < width)
                        grayPixels[index + 2] += quantError * 4 / 32
                    
                    if (y + 1 < height) {
                        if (x - 2 >= 0)
                            grayPixels[index + width - 2] += quantError * 1 / 32
                        if (x - 1 >= 0)
                            grayPixels[index + width - 1] += quantError * 2 / 32
                        grayPixels[index + width] += quantError * 4 / 32
                        if (x + 1 < width)
                            grayPixels[index + width + 1] += quantError * 2 / 32
                        if (x + 2 < width)
                            grayPixels[index + width + 2] += quantError * 1 / 32
                    }
                }
            }
            
            return result
        }
        
        /**
         * Дизеринг по методу Сьерра
         * @param src исходное изображение
         * @param threshold порог для преобразования в черно-белое (0-255)
         * @param isInverted инвертировать ли цвета
         * @return черно-белое изображение с дизерингом
         */
        fun sierraDithering(src: Bitmap, threshold: Int = 128, isInverted: Boolean = false): Bitmap {
            val width = src.width
            val height = src.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Создаем массив оттенков серого
            val grayPixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = src.getPixel(x, y)
                    val gray = Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f
                    grayPixels[y * width + x] = gray.roundToInt()
                }
            }
            
            // Матрица Сьерра
            val coefficients = arrayOf(
                intArrayOf(0, 0, 0, 5, 3),
                intArrayOf(2, 4, 5, 4, 2),
                intArrayOf(0, 2, 3, 2, 0)
            )
            
            // Алгоритм Сьерра
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    val oldPixel = grayPixels[index]
                    val newPixel = if (oldPixel < threshold) 0 else 255
                    
                    result.setPixel(x, y, if ((newPixel == 0) xor isInverted) 
                        Color.BLACK else Color.WHITE)
                    
                    val quantError = oldPixel - newPixel
                    
                    // Распространение ошибки по Сьерра
                    for (ky in 0 until 3) {
                        for (kx in 0 until 5) {
                            val nx = x + kx - 2
                            val ny = y + ky
                            
                            if (nx >= 0 && nx < width && ny < height) {
                                val factor = coefficients[ky][kx]
                                if (factor > 0) {
                                    grayPixels[ny * width + nx] += quantError * factor / 32
                                }
                            }
                        }
                    }
                }
            }
            
            return result
        }
        
        /**
         * Дизеринг по методу Сьерра-Лайт
         * @param src исходное изображение
         * @param threshold порог для преобразования в черно-белое (0-255)
         * @param isInverted инвертировать ли цвета
         * @return черно-белое изображение с дизерингом
         */
        fun sierraLiteDithering(src: Bitmap, threshold: Int = 128, isInverted: Boolean = false): Bitmap {
            val width = src.width
            val height = src.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Создаем массив оттенков серого
            val grayPixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = src.getPixel(x, y)
                    val gray = Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f
                    grayPixels[y * width + x] = gray.roundToInt()
                }
            }
            
            // Алгоритм Сьерра-Лайт (облегчённая Сьерра)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    val oldPixel = grayPixels[index]
                    val newPixel = if (oldPixel < threshold) 0 else 255
                    
                    result.setPixel(x, y, if ((newPixel == 0) xor isInverted) 
                        Color.BLACK else Color.WHITE)
                    
                    val quantError = oldPixel - newPixel
                    
                    // Распространение ошибки по Сьерра-Лайт
                    if (x + 1 < width)
                        grayPixels[index + 1] += quantError * 2 / 4
                    
                    if (y + 1 < height) {
                        if (x - 1 >= 0)
                            grayPixels[index + width - 1] += quantError * 1 / 4
                        grayPixels[index + width] += quantError * 1 / 4
                    }
                }
            }
            
            return result
        }
        
        /**
         * Оригинальный дизеринг с регулируемой матрицей из исходного приложения
         * @param bitmap исходное изображение
         * @param threshold порог для преобразования в черно-белое (0-255)
         * @param matrixSize размер матрицы дизеринга
         * @param isInverted инвертировать ли цвета
         * @param briColor коэффициенты для преобразования RGB в оттенки серого
         * @return черно-белое изображение с дизерингом
         */
        fun dithering(bitmap: Bitmap, threshold: Int = 255, matrixSize: Int = 8, isInverted: Boolean = false, briColor: Array<Float> = arrayOf(0.299f, 0.587f, 0.114f)): Bitmap {
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
                    if(isInverted) {
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
        
        /**
         * Сгенерировать матрицу Байера нужного размера
         * @param size размер матрицы (должен быть степенью 2)
         * @return матрица Байера указанного размера
         */
        private fun generateBayerMatrix(size: Int): Array<IntArray> {
            // Начальная матрица Байера 2×2
            val bayer2 = arrayOf(
                intArrayOf(0, 2),
                intArrayOf(3, 1)
            )
            
            if (size == 2) return bayer2
            
            // Рекурсивно строим матрицу большего размера
            val result = Array(size) { IntArray(size) }
            val halfSize = size / 2
            
            // Если нужная матрица больше 2×2, строим её рекурсивно
            if (halfSize > 1) {
                val smallerMatrix = generateBayerMatrix(halfSize)
                
                for (y in 0 until halfSize) {
                    for (x in 0 until halfSize) {
                        // Левый верхний квадрант
                        result[y][x] = smallerMatrix[y][x] * 4
                        
                        // Правый верхний квадрант
                        result[y][x + halfSize] = smallerMatrix[y][x] * 4 + 2
                        
                        // Левый нижний квадрант
                        result[y + halfSize][x] = smallerMatrix[y][x] * 4 + 3
                        
                        // Правый нижний квадрант
                        result[y + halfSize][x + halfSize] = smallerMatrix[y][x] * 4 + 1
                    }
                }
            } else {
                // Если нужна матрица 2×2, просто возвращаем базовую
                return bayer2
            }
            
            return result
        }
        
        /**
         * Инвертировать цвет пикселя (чёрный -> белый, белый -> чёрный)
         * @param color цвет пикселя
         * @return инвертированный цвет
         */
        private fun invertColor(color: Int): Int {
            return if (color == Color.BLACK) Color.WHITE else Color.BLACK
        }
    }
}

package ru.Niyaz.btsticker.processors

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

/**
 * Класс для масштабирования и трансформаций изображений
 */
class ImageScaler {

    companion object {
        // Типы масштабирования
        enum class ScaleType {
            SCALE,  // Сохранение пропорций
            FIT,    // Вместить изображение целиком в размеры
            CROP,   // Обрезать до соотношения
            STRETCH // Растянуть без сохранения пропорций
        }

        /**
         * Масштабировать изображение в соответствии с выбранным типом масштабирования
         * @param bitmap Исходное изображение
         * @param targetWidth Целевая ширина
         * @param targetHeight Целевая высота
         * @param scaleType Тип масштабирования
         * @return Масштабированное изображение
         */
        fun scaleImage(bitmap: Bitmap, targetWidth: Int, targetHeight: Int, scaleType: ScaleType): Bitmap {
            when (scaleType) {
                ScaleType.SCALE -> return scaleKeepAspectRatio(bitmap, targetWidth, targetHeight)
                ScaleType.FIT -> return fitImage(bitmap, targetWidth, targetHeight)
                ScaleType.CROP -> return cropImage(bitmap, targetWidth, targetHeight)
                ScaleType.STRETCH -> return stretchImage(bitmap, targetWidth, targetHeight)
            }
        }

        /**
         * Масштабировать с сохранением соотношения сторон
         */
        private fun scaleKeepAspectRatio(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            val sourceWidth = bitmap.width
            val sourceHeight = bitmap.height

            // Вычисляем соотношение сторон исходного изображения
            val sourceRatio = sourceWidth.toFloat() / sourceHeight.toFloat()

            // Вычисляем соотношение сторон целевого размера
            val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()

            // Определяем новые размеры с сохранением пропорций
            val newWidth: Int
            val newHeight: Int

            if (sourceRatio > targetRatio) {
                // Ограничиваем по ширине
                newWidth = targetWidth
                newHeight = (targetWidth / sourceRatio).toInt()
            } else {
                // Ограничиваем по высоте
                newHeight = targetHeight
                newWidth = (targetHeight * sourceRatio).toInt()
            }

            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }

        /**
         * Вместить изображение в указанные размеры с сохранением пропорций
         */
        private fun fitImage(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            val sourceWidth = bitmap.width
            val sourceHeight = bitmap.height

            val scaleX = targetWidth.toFloat() / sourceWidth.toFloat()
            val scaleY = targetHeight.toFloat() / sourceHeight.toFloat()

            // Выбираем меньший масштаб, чтобы изображение поместилось целиком
            val scale = min(scaleX, scaleY)

            val newWidth = (sourceWidth * scale).toInt()
            val newHeight = (sourceHeight * scale).toInt()

            // Создаем новый битмап нужного размера
            val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            
            // Заливаем фон белым (опционально)
            canvas.drawColor(android.graphics.Color.WHITE)
            
            // Центрируем изображение
            val left = (targetWidth - newWidth) / 2f
            val top = (targetHeight - newHeight) / 2f
            
            // Создаем матрицу трансформации
            val matrix = Matrix()
            matrix.postScale(scale, scale)
            matrix.postTranslate(left, top)
            
            // Рисуем изображение с применением матрицы
            val paint = Paint().apply {
                isFilterBitmap = true
            }
            canvas.drawBitmap(bitmap, matrix, paint)
            
            return output
        }

        /**
         * Обрезать изображение до нужного соотношения сторон
         */
        private fun cropImage(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            val sourceWidth = bitmap.width
            val sourceHeight = bitmap.height
            
            val sourceRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
            val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
            
            // Определяем какую часть изображения нужно обрезать
            val rectSrc: RectF
            
            if (sourceRatio > targetRatio) {
                // Обрезаем по бокам
                val newWidth = sourceHeight * targetRatio
                val offset = (sourceWidth - newWidth) / 2f
                rectSrc = RectF(offset, 0f, sourceWidth - offset, sourceHeight.toFloat())
            } else {
                // Обрезаем сверху и снизу
                val newHeight = sourceWidth / targetRatio
                val offset = (sourceHeight - newHeight) / 2f
                rectSrc = RectF(0f, offset, sourceWidth.toFloat(), sourceHeight - offset)
            }
            
            // Создаем битмап с целевыми размерами
            val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            
            // Определяем целевую область для рисования
            val rectDst = RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())
            
            // Создаем матрицу трансформации
            val matrix = Matrix()
            matrix.setRectToRect(rectSrc, rectDst, Matrix.ScaleToFit.FILL)
            
            // Рисуем обрезанное изображение
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }
            
            canvas.drawBitmap(bitmap, matrix, paint)
            
            return result
        }

        /**
         * Растянуть изображение без сохранения пропорций
         */
        private fun stretchImage(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        }

        /**
         * Автоматически повернуть изображение, если оно имеет неправильную ориентацию
         * для заданных целевых размеров
         */
        fun autoRotate(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            val sourceWidth = bitmap.width
            val sourceHeight = bitmap.height
            
            val sourceIsLandscape = sourceWidth > sourceHeight
            val targetIsLandscape = targetWidth > targetHeight
            
            // Если ориентации не совпадают, нужно повернуть
            if (sourceIsLandscape != targetIsLandscape) {
                val matrix = Matrix()
                matrix.postRotate(90f)
                return Bitmap.createBitmap(bitmap, 0, 0, sourceWidth, sourceHeight, matrix, true)
            }
            
            return bitmap
        }
    }
}

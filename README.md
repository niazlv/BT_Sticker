# BT_Sticker
Приложение для печати стикеров на принтерах MHT-P13.
## Как с ним работать?
1. зайдите в настройки->Bluetooth и подключитесь к вашему принтеру. Достаточно того, чтобы он запомнился в "Подключенные устройства"
2. Откройте приложение, нажмите "Find device". Выберите из списка ваш принтер "MHT-P13"
3. Подключитесь к нему кнопкой "Connect"
4. Откройте нужную вам картинку внопкой "Open image"
5. Если картинка вертикальная, то разверните её кнопкой "Rotate button"
6. !!!ВАЖНО!!! Нажмите "Convert image size" и ваша картинка ужмется до размеров 96*240px(размер стикера 12*30мм)
7. Если ваша картинка была не подготовлена, и не была превращена в монохромную, то можно её в такую превратить кнопкой "Black and White" или же, если картинка с отенками, то можно использовать "Dithering".
8. Чтобы посмотреть как будет выглядеть стикер до печати, можно нажать кнопку "Real size" и вы увидите максимально приблеженную картинку к реальной
9. Нажимаем кнопку "Send" для отправки на печать
10. по окончании работы с принтером, нажимаем "Close connection" иначе приложение будет жить в фоне и поддерживать свою работу 

У Dithering'а есть настройки, например как Matrix size. По умолчанию стоит размер матрицы 2. Получаются самые детальные фото.  
Так же есть параметр threshold. Это "пороговые значения", изменяет порог при котором отсекаются цвета. Работает и к "Dithering" и к "Black and White".  
Ещё есть параметр "inversion". Инвертирует цвета
## Как оно работает? Могу ли я сам собрать приложение?
Да, конечно. Я лишь разобрал работу приложения MSticker через журнал HCL Bluetooth.
Минимальный протокол таков(больше просто не разибрался)  
Общается в RAW режиме(бинарном) Команды буду писать используя HEX  
dd - начало и конец пакета  

команда          размер  служебный блок       данные    размер стикера \
dd  00 01 02     0b 44   00 0c 01 00          ...00dd       12\*30  \
dd  00 01 02     0f 04   00 0c 01 00          ...00dd       12\*40  \
dd  00 01 02     1b b8   00 0c 01 00          ...00dd       12\*109  


в размер указывается размер данных(96\*240/8) + 4 байта из служебного блока.
Как посчитать разрешение?  
DPI = 203px/in  
pixX = 203\*30/25.4  
pixY = 203\*12/25.4  

Данные слать нужно через RFCOMM(BT serial port). На этом пока что все.

## Others

Принтер покупался тут: [aliexpress](https://aliexpress.ru/item/1005004188980640.html?srcSns=sns_Telegram&businessType=ProductDetail&spreadType=socialShare&tt=MG&utm_medium=sharing&sku_id=12000030186776817)

# peas-cli

## Системный анализ продукта

<a href="./misc/Системный анализ продукта.pdf">Посмотреть PDF файл</a>

## Области применения

1. Оптимизация механизма репликации баз данных (например в Cassandra, там есть только режимы single, ring)
2. Ускорение отправки snapshot версий на несколько backup серверов (например результаты аналитики big data)
3. Выпуск большого обновления клиентской части какой-либо игры (например вышло обновления на halloween)
4. Быстрая загрузка первичного ПО на только что купленные хосты с ansible (например для установки защитного обеспечения)

## Команды:

peas \[-hu\] \[<файлы>...\]<br>
&nbsp;&nbsp;&nbsp;&nbsp;Загрузить/выгрузить файл<br>
&nbsp;&nbsp;&nbsp;&nbsp;\[<файлы>...\]   Файлы, которые нужно загрузить/выгрузить<br>
&nbsp;&nbsp;&nbsp;&nbsp;-h, --help         Показать это сообщение<br>
&nbsp;&nbsp;&nbsp;&nbsp;-u, --upload       Режим выгрузки<br>

peas create \[-h\] \[-s=SIZE\] \[-w=OWNERS\]... \<файл\><br>
&nbsp;&nbsp;&nbsp;&nbsp;Создать .peas файл<br>
&nbsp;&nbsp;&nbsp;&nbsp;\<файл\>             Файл, для которого должен быть создан .peas файл<br>
&nbsp;&nbsp;&nbsp;&nbsp;-h, --help             Показать это сообщение<br>
&nbsp;&nbsp;&nbsp;&nbsp;-s, --part-size=SIZE   Размер блока<br>
&nbsp;&nbsp;&nbsp;&nbsp;-w, --owners=OWNERS    Владельцы файла (трекеры по умолчанию)<br>

![upload](./misc/upload.jpg)
![upload](./misc/downloading.jpg)
![upload](./misc/downloaded.jpg)
![upload](./misc/help.jpg)
![upload](./misc/help_create.jpg)

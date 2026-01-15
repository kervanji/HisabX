# دليل ملف Log للـ Installer

## موقع ملف Log

عند تشغيل الـ installer، يتم إنشاء ملف log تلقائياً في:

```
C:\Users\[USERNAME]\AppData\Local\Temp\Setup Log YYYY-MM-DD #XXX.txt
```

حيث:
- `[USERNAME]` = اسم المستخدم على Windows
- `YYYY-MM-DD` = تاريخ التثبيت
- `#XXX` = رقم تسلسلي

## كيفية الوصول لملف Log

### الطريقة 1: أثناء التثبيت
1. عند ظهور مشكلة أو تجميد
2. اضغط `Ctrl + C` لنسخ مسار ملف Log
3. افتح File Explorer والصق المسار

### الطريقة 2: بعد التثبيت
1. افتح File Explorer
2. اذهب إلى: `%TEMP%`
3. ابحث عن ملفات تبدأ بـ `Setup Log`
4. افتح أحدث ملف

### الطريقة 3: باستخدام PowerShell
```powershell
# عرض آخر ملف log
Get-ChildItem -Path $env:TEMP -Filter "Setup Log*.txt" | 
    Sort-Object LastWriteTime -Descending | 
    Select-Object -First 1 | 
    Get-Content -Tail 50
```

## محتوى ملف Log

الملف يحتوي على معلومات مفصلة عن:

### معلومات النظام
```
Setup version: Inno Setup version 6.3.3
Windows version: 10.0.19045 (Windows 10)
Processor architecture: x64
```

### خطوات التثبيت
```
Starting the installation process.
Creating directory: C:\Program Files\HisabX
Extracting file: HisabX.jar
Extracting file: HisabX.bat
Extracting file: runtime\bin\java.exe
...
```

### الأخطاء (إن وجدت)
```
Error: Access denied to file: ...
Error: Disk full
Error: File in use: ...
```

## تحليل المشاكل الشائعة

### 1. التجميد في "Preparing to Install"
**السبب**: استخراج ملفات كبيرة
**الحل**: انتظر بضع دقائق - العملية طبيعية

في ملف Log ستجد:
```
Extracting file: runtime\lib\modules (50+ MB)
```

### 2. خطأ "Access Denied"
**السبب**: صلاحيات غير كافية
**الحل**: تشغيل الـ installer كـ Administrator

في ملف Log ستجد:
```
Error: Access denied to directory: C:\Program Files\HisabX
```

### 3. خطأ "Disk Full"
**السبب**: مساحة غير كافية
**الحل**: توفير 200 MB على الأقل

في ملف Log ستجد:
```
Error: Not enough disk space
Required: 150 MB, Available: 50 MB
```

### 4. خطأ "File in Use"
**السبب**: البرنامج قيد التشغيل
**الحل**: إغلاق البرنامج قبل التثبيت

في ملف Log ستجد:
```
Error: File in use: C:\Program Files\HisabX\HisabX.jar
```

## التحسينات المطبقة

تم تطبيق التحسينات التالية لتقليل التجميد:

### 1. تفعيل Logging
```pascal
SetupLogging=yes
```
- يسجل كل خطوة في التثبيت
- يساعد في تشخيص المشاكل

### 2. تقليل مستوى الضغط
```pascal
Compression=lzma2/fast
SolidCompression=no
```
- **قبل**: `lzma2/max` + `SolidCompression=yes` = بطيء جداً
- **بعد**: `lzma2/fast` + `SolidCompression=no` = أسرع بكثير
- **النتيجة**: زيادة 4 MB في الحجم مقابل سرعة أكبر

### 3. تحسين الأداء
```pascal
InternalCompressLevel=fast
DiskSpanning=no
```
- استخراج أسرع للملفات
- تقليل استهلاك الذاكرة

## مقارنة الأداء

| الإعداد | الحجم | وقت البناء | وقت التثبيت |
|---------|-------|------------|-------------|
| **قبل** (max compression) | 122.59 MB | 20 ثانية | 2-3 دقائق |
| **بعد** (fast compression) | 126.87 MB | 13.8 ثانية | 30-60 ثانية |

## نصائح للمستخدمين

### للتثبيت السريع:
1. ✅ أغلق برامج الحماية مؤقتاً
2. ✅ تأكد من وجود 200 MB مساحة حرة
3. ✅ شغل الـ installer كـ Administrator
4. ✅ انتظر بصبر - لا تلغي العملية

### إذا حدث تجميد:
1. ⏱️ انتظر 2-3 دقائق أولاً
2. 📊 افتح Task Manager لمراقبة استخدام القرص
3. 📝 اقرأ ملف Log لمعرفة الخطوة الحالية
4. 🔄 إذا استمر التجميد أكثر من 5 دقائق، ألغي وأعد المحاولة

## أوامر مفيدة للمطورين

### عرض آخر 100 سطر من Log
```powershell
Get-ChildItem -Path $env:TEMP -Filter "Setup Log*.txt" | 
    Sort-Object LastWriteTime -Descending | 
    Select-Object -First 1 | 
    Get-Content -Tail 100
```

### نسخ ملف Log إلى سطح المكتب
```powershell
Get-ChildItem -Path $env:TEMP -Filter "Setup Log*.txt" | 
    Sort-Object LastWriteTime -Descending | 
    Select-Object -First 1 | 
    Copy-Item -Destination "$env:USERPROFILE\Desktop\HisabX-Install-Log.txt"
```

### مراقبة Log في الوقت الفعلي
```powershell
Get-ChildItem -Path $env:TEMP -Filter "Setup Log*.txt" | 
    Sort-Object LastWriteTime -Descending | 
    Select-Object -First 1 | 
    Get-Content -Wait -Tail 10
```

## الدعم الفني

إذا واجهت مشاكل:
1. احفظ ملف Log
2. أرسله مع وصف المشكلة
3. اذكر نظام التشغيل والمساحة المتوفرة

---

**ملاحظة**: ملف Log يُحذف تلقائياً بعد 30 يوم من مجلد Temp

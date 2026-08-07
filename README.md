# ممنوع تكبس هون — Android Offline Game v1.0.1

لعبة ألغاز/خدع عربية تعمل محليًا بالكامل، بدون سيرفر وبدون صلاحية إنترنت.

## المتطلبات
- Android 11+ (minSdk 30)
- JDK 17 للبناء
- يمكن البناء بالكامل عبر GitHub Actions

## البناء عبر GitHub
المشروع يحتوي الآن `.github/workflows/android.yml`. راجع `GITHUB_BUILD.md`.
الـWorkflow يبني `assembleDebug`، والـDebug APK موقّع تلقائيًا وجاهز للتثبيت.

## إصلاحات v1.0.1
- جعل Fullscreen/WindowInsets غير قادر على إسقاط التطبيق إذا فشل على جهاز OEM معين.
- تأخير تهيئة الصوت حتى أول مرة يحتاجها اللاعب بدل تشغيل Audio resource عند الإقلاع.
- حماية تسجيل Accelerometer من أخطاء الأجهزة/التعريفات.
- إزالة تبديل Software Layer من داخل `onDraw` واستبدال الظل برسم آمن وخفيف.
- حماية مسار الرسم: في حال خطأ Java بالرسم تظهر شاشة أمان بدل إغلاق التطبيق.
- إضافة CrashReporter محلي بالكامل، بدون شبكة أو Analytics.
- إضافة GitHub Actions ثابت يستخدم Gradle 8.9 صراحةً، بدون الاعتماد على Gradle مثبت مسبقًا في الـRunner.
- إضافة Java 17 compileOptions صراحةً.

## ما تم تنفيذه
- 40 مرحلة موزعة على 5 فصول.
- لمس، ضغط مطوّل، دبل كبسة، سحب، Swipe، Multi-touch.
- Accelerometer للهز والميل والثبات مع fallback إذا الحساس غير متوفر.
- ذاكرة محلية لسلوك اللاعب والتقدم باستخدام SharedPreferences.
- أسرار محلية وEaster Eggs.
- لا يوجد INTERNET permission.
- لا توجد إعلانات، حسابات، Analytics أو اتصال خارجي.

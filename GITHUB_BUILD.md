# البناء عبر GitHub Actions

هذه النسخة تحتوي Workflow جاهزًا في:

`.github/workflows/android.yml`

## كيف تبني APK

1. ارفع **محتويات مجلد المشروع نفسه** إلى جذر مستودع GitHub، بحيث يظهر `app/` و`build.gradle` و`.github/` مباشرة في الصفحة الرئيسية للمستودع.
2. افتح تبويب **Actions**.
3. اختر **Build Android APK**.
4. اضغط **Run workflow**.
5. بعد نجاح البناء، نزّل Artifact باسم **DontPressHere-Android-APK**.
6. فك ضغط الـArtifact وثبّت الملف `DontPressHere-v1.0.1-debug.apk`.

## لماذا Debug APK؟

`assembleDebug` ينتج APK موقّعًا تلقائيًا بمفتاح Debug، لذلك يكون جاهزًا للتثبيت والاختبار مباشرة. هذه الطريقة مناسبة الآن حتى نثبت أن اللعبة مستقرة على جهازك.

## تحذير Play Protect

وجود تحذير عند تثبيت APK من GitHub لا يعني تلقائيًا وجود فيروس. التطبيق مثبت من خارج Google Play، وقد يفحصه Play Protect أو يظهر تحذير مطور غير موثق. إزالة هذا التحذير نهائيًا ليست إعداد Gradle؛ للتوزيع العام استخدم مفتاح Release ثابت وسجّل المطور/اسم الحزمة ضمن مسار Android المناسب.

## إذا أغلق التطبيق رغم الإصلاح

نفّذ هذا والجهاز موصول بالكمبيوتر مع USB debugging:

```bash
adb logcat -c
adb shell am force-stop com.dontpress.here
adb shell monkey -p com.dontpress.here -c android.intent.category.LAUNCHER 1
adb logcat -d -v time AndroidRuntime:E DontPressHere:E '*:S'
```

أرسل ناتج آخر أمر. النسخة الجديدة أيضًا تحفظ أخطاء Java محليًا بدل أن تترك بعض أخطاء البدء بدون تشخيص.

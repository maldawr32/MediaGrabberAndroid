# MediaGrabber Android — Play Ready v2

نسخة جديدة من الصفر، مهيأة للبناء والنشر على Google Play بدل المشروع القديم.

## ما الذي يفعله التطبيق؟

- تنزيل ملفات الوسائط من روابط HTTPS مباشرة يملك المستخدم حق تنزيلها.
- حفظ الملفات في `Downloads/MediaGrabber` عبر DownloadManager في Android.
- اختيار فيديو محلي وفتحه بدون رفعه إلى أي خادم تابع للتطبيق.
- حظر مصادر YouTube داخل التطبيق لتجنب بناء ميزة تتعارض مع سياسات تنزيل محتوى YouTube.
- واجهة Jetpack Compose + Material 3 مع دعم العربية والإنجليزية والوضع الداكن.

## مواصفات البناء

- Package: `com.maldawr.mediagrabber`
- minSdk: 29
- targetSdk: 36 (Android 16)
- compileSdk: 36
- JDK: 17
- Gradle: 8.13
- Android Gradle Plugin: 8.13.2
- Kotlin: 2.3.21
- Release: R8 + resource shrinking
- Google Play output: Android App Bundle (`.aab`)

## البناء محليًا

المشروع لا يحتوي Gradle Wrapper binary حتى لا ننسخ ملفًا تنفيذيًا غير ضروري في المستودع. استخدم Gradle 8.13 أو افتح المشروع في Android Studio حديث ثم أنشئ الـwrapper مرة واحدة:

```bash
gradle wrapper --gradle-version 8.13
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease
```

## CI

`.github/workflows/android-ci.yml` يشغل الاختبارات وLint ويبني Debug APK على فرع `play-ready-v2`.

## إصدار Google Play موقّع

Workflow: `.github/workflows/play-release.yml`

أضف الأسرار التالية في GitHub Actions قبل أول إصدار:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `PLAY_SERVICE_ACCOUNT_JSON` (مطلوب فقط للنشر التلقائي إلى Play Console)

بعدها شغّل **Play Release AAB** يدويًا. يمكنك الاكتفاء بتوليد AAB موقّع أو رفعه كـDraft إلى internal/alpha/beta/production.

راجع `docs/PLAY_PUBLISHING.md` قبل أول نشر.

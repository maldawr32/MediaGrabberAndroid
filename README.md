# MediaGrabber Studio — Android

تطبيق Android متقدم لاستخراج **جزء زمني محدد** من الفيديو بدل تنزيل/حفظ الفيديو كاملًا كفكرة أساسية.

المستخدم يختار مصدر الفيديو، يعاينه داخل التطبيق، يحدد وقت البداية والنهاية، يعاين المقطع المحدد، ثم يصدره كفيديو أو كصوت فقط.

## ما تم تنفيذه الآن

- اختيار فيديو من الجهاز عبر Android Document Picker.
- معاينة الفيديو داخل التطبيق باستخدام AndroidX Media3 / ExoPlayer.
- قراءة مدة الفيديو تلقائيًا.
- تحديد Start / End عبر Range Slider.
- ضبط دقيق للبداية والنهاية بمقدار `±1s` و`±0.1s`.
- تشغيل المقطع المحدد ومعاينته بشكل متكرر Loop.
- تصدير الجزء المحدد فقط باستخدام Media3 Transformer.
- إخراج فيديو `MP4 (H.264 + AAC)`.
- إخراج صوت فقط `M4A (AAC)` بإزالة مسار الفيديو.
- Progress وإلغاء عملية التصدير.
- حفظ الناتج عبر MediaStore داخل `Movies/MediaGrabber` أو `Music/MediaGrabber`.
- مشاركة الملف الناتج من داخل Android.
- أساس لمعاينة روابط HTTP/HTTPS وHLS/DASH باستخدام ExoPlayer.

## مصادر الإنترنت

روابط الملفات والـstreams المباشرة هي طبقة المصدر الأولى.

روابط صفحات الخدمات مثل YouTube / Facebook / TikTok ليست عادة رابط ملف وسائط مباشر، ولذلك ستُعامل في المعمارية كـ **Source Adapters** منفصلة تقوم بتحويل رابط الصفحة إلى معلومات مصدر قابلة للمعاينة والمعالجة عندما يكون ذلك مسموحًا تقنيًا وقانونيًا.

الهدف المعماري هو إبقاء المحرر مستقلًا عن المصدر:

`Source -> Preview -> Time Selection -> Export`

بحيث يبقى نفس محرر Start/End ونفس نظام التصدير مستخدمًا سواء كان المصدر محليًا أو من الإنترنت.

## الخطوات المتقدمة المخطط لها

- Timeline بصور مصغرة Thumbnails مع Zoom.
- إدخال Start/End يدويًا حتى مستوى millisecond.
- اختيار الجودة والدقة وbitrate.
- Fast Cut عند حدود keyframes لتجنب إعادة الترميز عندما يكون ممكنًا.
- Exact Cut للقص الدقيق.
- MP3/WAV وخيارات صوت إضافية.
- تقدير حجم الملف قبل التصدير.
- سجل عمليات ومشاريع حديثة.
- Source Adapter layer للمصادر الشبكية المختلفة.
- تنزيل/قراءة الجزء المطلوب فقط من المصدر الشبكي عندما يسمح نوع الـstream والخادم بذلك.

## التقنية

- Kotlin
- Jetpack Compose + Material 3
- AndroidX Media3 1.11.0
- ExoPlayer
- Media3 Transformer
- HLS / DASH playback modules
- MediaStore / Scoped Storage

## مواصفات البناء

- Package: `com.maldawr.mediagrabber`
- minSdk: 29
- targetSdk: 36
- compileSdk: 36
- JDK: 17
- Gradle: 8.13
- Android Gradle Plugin: 8.13.2
- Kotlin: 2.3.21
- Release: R8 + resource shrinking
- Google Play output: Android App Bundle (`.aab`)

## CI

`.github/workflows/android-ci.yml` يشغل:

1. Unit tests
2. Android Lint
3. Debug APK build
4. رفع `mediagrabber-debug` كـGitHub Actions artifact

آخر نسخة وظيفية من محرر المقاطع تم التحقق منها بنجاح عبر CI على الفرع `play-ready-v2`.

## Release / Google Play

Workflow الإصدار موجود في:

`.github/workflows/play-release.yml`

ويستخدم أسرار GitHub للتوقيع بدل تخزين الـkeystore داخل المستودع:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `PLAY_SERVICE_ACCOUNT_JSON` للنشر التلقائي عند تفعيله

راجع `docs/PLAY_PUBLISHING.md` قبل إصدار Production.

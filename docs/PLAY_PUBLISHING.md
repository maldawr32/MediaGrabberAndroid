# Google Play Publishing Checklist

## 1. Play App Signing

فعّل Play App Signing في Play Console وأنشئ Upload Key منفصلًا. لا تضع ملف keystore أو كلمات المرور داخل Git.

حوّل Upload Key إلى Base64 واحفظه في GitHub Secret باسم `ANDROID_KEYSTORE_BASE64`، وأضف بقية أسرار التوقيع المذكورة في README.

## 2. Android requirements

المشروع يستهدف Android 16 / API 36، وهو المستوى المطلوب للتطبيقات الجديدة والتحديثات في Google Play اعتبارًا من 31 أغسطس 2026.

Google Play يعتمد Android App Bundle للتطبيقات الجديدة؛ Workflow الإصدار يولد `app-release.aab`.

## 3. Store listing

اقتراح اسم: `MediaGrabber — Direct Media Saver`

وصف قصير مقترح:

> نزّل ملفات الوسائط المصرح لك بها من روابط HTTPS مباشرة واحفظها على جهازك بأمان.

تجنب في صور المتجر والوصف كلمات مثل YouTube Downloader أو Instagram Downloader أو أي ادعاء بتجاوز حماية منصة خارجية.

## 4. Data Safety

التصميم الحالي:

- لا يوجد حساب مستخدم.
- لا توجد إعلانات.
- لا توجد Analytics أو SDKs تتبع.
- لا يرسل التطبيق روابط المستخدم إلى خادم تابع لنا؛ Android DownloadManager يتصل مباشرة بعنوان URL الذي اختاره المستخدم.
- اختيار الفيديو المحلي يتم عبر System Document Picker.

تحقق من هذه الإجابات مجددًا إذا أضفت لاحقًا Analytics أو Crash reporting أو Ads أو backend.

## 5. Privacy policy

يمكن استخدام `docs/PRIVACY_POLICY.md` كأساس لصفحة سياسة الخصوصية العامة. Play Console يحتاج رابط HTTPS عام عند الطلب، لذلك انشرها لاحقًا على موقعك أو GitHub Pages.

## 6. Release flow

1. مرّر Android CI بنجاح.
2. شغّل Play Release AAB من GitHub Actions.
3. أدخل Version Name.
4. اختر internal للاختبار الأول.
5. اترك upload_to_play=false إذا أردت تنزيل AAB يدويًا.
6. بعد تهيئة حساب الخدمة وصلاحيات Play Console، فعّل upload_to_play لرفع Draft تلقائيًا.
7. اختبر عبر Internal testing قبل Production.

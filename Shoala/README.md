# شعلة (Shoala)

## البناء على الكمبيوتر (Android Studio)
1. افتح المجلد في Android Studio (File > Open) وانتظر Gradle Sync.
2. Build > Build Bundle(s) / APK(s) > Build APK(s).
3. الملف: app/build/outputs/apk/debug/app-debug.apk

## البناء بدون كمبيوتر (GitHub Actions)
1. ارفع محتوى هذا المجلد كاملاً (بما فيه .github) على مستودع GitHub جديد.
2. تبويب Actions > Build APK > Run workflow.
3. بعد النجاح نزّل Shoala-debug-apk من Artifacts وفك الضغط.

## التثبيت على الموبايل
- انسخ app-debug.apk للموبايل وافتحه (اسمح بالتثبيت من مصدر غير معروف).
- أندرويد 13+: من معلومات التطبيق (App info) اختر "السماح بالإعدادات المقيدة" ثم فعّل خدمة "شعلة" من إعدادات إمكانية الوصول.
- استثنِ التطبيق من توفير البطارية.

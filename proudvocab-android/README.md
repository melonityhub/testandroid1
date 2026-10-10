# ProudVocab Android

نسخهٔ اندروید (Kotlin + Jetpack Compose) برای یادگیری زبان با ویدیو و زیرنویس. همهٔ امکانات **رایگان و بدون لایسنس/پریمیوم** هستند.

- اندروید ۹ به بالا (`minSdk 28`)، تست‌شده برای POCO X3 Pro
- پخش ویدیوی محلی با ExoPlayer (MP4, MKV, HEVC, WebM …) + زیرنویس SRT / VTT / ASS
- کلیک روی هر کلمه: ترجمه، سطح CEFR (A1–C2)، تلفظ، تعریف و مترادف، ذخیره
- لغت‌ها: جستجو، فیلتر، ویرایش، «یادگرفته/سخت»، خروجی CSV و Anki، پشتیبان و بازیابی JSON
- مرور با SRS (دوباره / سخت / خوب / آسان) و ۶ بازی (چهارگزینه‌ای، جای خالی، حروف درهم، جفت‌کردن، دیکته، کارت حافظه)
- فارسی و انگلیسی، RTL کامل، تم روشن/تیره
- فقط یک مجوز: `INTERNET` (فایل‌ها با انتخابگر سیستم باز می‌شوند، پس مجوز ذخیره‌سازی لازم نیست)

## قراردادن در ریپو

این بسته را در **ریشهٔ ریپو** (کنار `pretty/` و `proudvocab-desktop/`) باز کن تا این ساختار بسازد:

```
.github/workflows/android.yml            ← بیلد و ریلیز خودکار
.github/workflows/generate-keystore.yml  ← ساخت کلید امضا (یک‌بار)
proudvocab-android/                      ← خود پروژه
```

اگر پروژه را در ریپوی جدا می‌گذاری، محتویات `proudvocab-android/` را به ریشه ببر، `.github` را هم کنارش بگذار و در `android.yml` همهٔ `proudvocab-android/` ها و `working-directory` را حذف کن.

## بیلد و ریلیز با GitHub

۱. در ریپو: **Settings ← Actions ← General ← Workflow permissions ← Read and write permissions** را فعال کن (برای ساخت Release).

۲. **یک‌بار** کلید امضای دائمی بساز تا نسخه‌های بعدی روی هم نصب شوند:
   - Actions ← *Generate signing keystore (run once)* ← Run workflow
   - از پایین صفحهٔ اجرا، آرتیفکت `keystore-and-secrets` را دانلود کن
   - چهار مقدار داخل `secrets.txt` را در **Settings ← Secrets and variables ← Actions** به‌صورت Secret ثبت کن: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
   - فایل `release.jks` را جای امن نگه دار و آرتیفکت را حذف کن

   (اگر این مرحله را نکنی بیلد باز هم کار می‌کند ولی با کلید موقت امضا می‌شود؛ هر نسخه را باید بعد از حذف نسخهٔ قبلی نصب کنی.)

۳. **انتشار نسخه:**
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   یا Actions ← *Android build & release* ← Run workflow و نسخه (مثلاً `1.0.0`) را وارد کن.
   بعد از چند دقیقه APK در **Releases** است. هر push به `main` هم فقط یک APK آزمایشی در Artifacts می‌سازد.

۴. روی گوشی: APK را دانلود و نصب کن (اجازهٔ نصب از منبع ناشناس برای مرورگر/فایل‌منیجر).

## بیلد محلی (اختیاری)

JDK 17 + Android SDK (Android Studio):

```bash
./gradlew testDebugUnitTest      # تست‌ها
./gradlew assembleRelease        # APK در app/build/outputs/apk/release/
python3 tools/check_resources.py # چک سریع منابع/مجوزها
```

## معماری (خلاصه)

- `core/` — منطق خالص Kotlin بدون وابستگی به اندروید (پارسر زیرنویس، CEFR، SRS، بازی‌ها، خروجی‌ها، JSON) و کاملاً تست‌شده
- `data/` — ذخیره‌سازی اتمیک (`AtomicFile`)، تنظیمات، ترجمه/دیکشنری (HTTPS)، TTS، لاگ کرش
- `ui/` — Compose: کتابخانه، پلیر، لغت‌ها، تمرین، تنظیمات
- بدون ViewModel/DI سنگین: یک `AppContainer` و `StateFlow` در Repositoryها (جریان داده یک‌طرفه)

## داده‌ها و حریم خصوصی

لغت‌ها فقط روی گوشی ذخیره می‌شوند (`filesDir`). فقط کلمه/جمله‌ای که ترجمه می‌کنی برای Google Translate و dictionaryapi.dev فرستاده می‌شود. لیست سطح CEFR (۸٬۸۳۳ کلمه) از پروژهٔ اصلی گرفته شده و داخل اپ است.

# Brahma Mobile (ব্রহ্ম মোবাইল)

Kotlin + Jetpack Compose দিয়ে বানানো voice-first AI ফোন অ্যাসিস্ট্যান্ট।
UI: **Glassmorphism** (স্বচ্ছ কাচের প্যানেল + উজ্জ্বল কিনারা + ভেসে চলা aurora আলো) এবং **Claymorphism** (ফোলা, নরম, 3D বাটন ও অরব)।

## কী কী পারে
| ক্ষমতা | কীভাবে |
|---|---|
| ভয়েস ও চ্যাট (বাংলা / English / हिन्दी) | Android SpeechRecognizer + TextToSpeech, কথা বললে সাথে সাথে TTS থামে |
| AI ব্রেইন | Gemini (ডিফল্ট) + OpenRouter ফলব্যাক, টুল কলিং, সর্বোচ্চ ১২ ধাপের লুপ |
| অ্যাপ খোলা, URL, ওয়েব সার্চ, আবহাওয়া | `open_app`, `open_url`, `web_search`, `weather` |
| টর্চ, ভলিউম, সেটিংস পেজ, অ্যালার্ম, টাইমার | `flashlight`, `set_volume`, `open_settings`, `set_alarm`, `set_timer` |
| কল, SMS, WhatsApp (নামে বা নম্বরে) | `call_phone`, `send_sms`, `send_whatsapp` (প্রতিবার আপনার অনুমতি চায়) |
| **স্ক্রিন কন্ট্রোল** | AccessibilityService: `screen_read`, `screen_click`, `screen_tap`, `screen_type`, `screen_scroll`, `press_key` (back/home/recents/notifications/quick settings/lock/screenshot/power menu) |
| নোটিফিকেশন পড়া ও সারাংশ | NotificationListenerService |
| ক্লিপবোর্ড, ব্যাটারি, সময় | `get_clipboard`, `set_clipboard`, `battery_status`, `current_time` |
| মেমোরি ও স্থায়ী নিয়ম | ফোনেই সংরক্ষিত, Settings থেকে দেখা/মোছা যায় |

"ফোন কন্ট্রোল" মানে: AI স্ক্রিন পড়ে → ট্যাপ/টাইপ/স্ক্রল করে → আবার স্ক্রিন দেখে যাচাই করে। যেমন "WhatsApp খুলে রহিমকে বলো আসছি" বললে অ্যাপ খুলবে, মেসেজ বসাবে, তারপর Send চাপবে (অনুমতি নিয়ে)।

## নিরাপত্তা (ইচ্ছাকৃত)
- ব্যাংক, bKash/Nagad, wallet, পাসওয়ার্ড ম্যানেজার, Authenticator অ্যাপে স্ক্রিন কন্ট্রোল **ব্লক** করা আছে।
- কল/SMS/WhatsApp-এর আগে কনফার্মেশন ডায়ালগ আসে (Settings থেকে বন্ধ করা যায়, তবে বন্ধ না রাখাই ভালো)।
- স্ক্রিন, নোটিফিকেশন, ওয়েব, ক্লিপবোর্ড থেকে পড়া লেখা `<untrusted_content>` হিসেবে AI-কে দেওয়া হয়: ওই লেখার ভেতরের "নির্দেশ" AI মানবে না (prompt-injection সুরক্ষা)।
- API key এনক্রিপ্টেড (Android Keystore) স্টোরেজে। কোনো অ্যানালিটিক্স/টেলিমেট্রি নেই।
- যেকোনো সময় Home স্ক্রিনের **Stop** বাটনে সব থামে।

## বিল্ড করার পথ

### পথ A: GitHub Actions (সবচেয়ে সহজ, সুপারিশকৃত)
```bash
pkg update -y && pkg install -y git gh
cd BrahmaMobile
git init && git add -A && git commit -m "Brahma Mobile"
gh auth login
gh repo create brahma-mobile --private --source=. --push
```
GitHub → Actions → **Build APK** → সবচেয়ে সাম্প্রতিক run → Artifacts থেকে `brahma-mobile-debug` নামান।

### পথ B: সরাসরি Termux-এ
Android SDK + `aapt2` ARM-এ ঝামেলার, পথ A-ই নিরাপদ। চেষ্টা করতে চাইলে:
```bash
pkg install -y openjdk-17 aapt2 unzip
# SDK একটা ফোল্ডারে রেখে (platforms;android-35, build-tools) ANDROID_HOME সেট করুন
export ANDROID_HOME=$HOME/android-sdk
./build_termux.sh
```

## প্রথমবার ব্যবহার
1. APK ইনস্টল (Unknown sources অনুমতি দিয়ে)।
2. **Settings → AI brain**: Gemini key বসান (aistudio.google.com থেকে ফ্রি key পাওয়া যায়) → Save।
3. **Settings → Phone control power**: Microphone, Contacts ইত্যাদি এবং **Accessibility** ও **Notification access** চালু করুন।
   - Android 13+ এ সাইডলোড অ্যাপে Accessibility ধূসর থাকলে: App info → ⋮ → "Allow restricted settings", তারপর আবার চালু করুন।
4. Home-এর অরবে ট্যাপ করে বলুন: "টর্চ জ্বালাও", "ব্যাটারি কত?", "স্ক্রিনে কী আছে পড়ো"।

## যা এখনো নেই (সৎ তালিকা)
- ওয়েক-ওয়ার্ড ("Hey Brahma") ও ভাসমান ওভারলে বাবল: নেই, অরবে ট্যাপ করতে হয়।
- Markdown রেন্ডারিং, ছবি/ক্যামেরা বিশ্লেষণ, পুশআপ/ক্যালরি কাউন্টার, ডকুমেন্ট জেনারেটর: নেই।
- "Glass blur" আসল ব্যাকড্রপ-ব্লার নয়; Compose-এ এটা সব ডিভাইসে স্থিতিশীল নয়, তাই স্বচ্ছ গ্রেডিয়েন্ট + কিনারার আলো + পেছনের গ্লো দিয়ে কাচের অনুভূতি আনা হয়েছে।
- কিছু ফোনে (Xiaomi/Oppo/Vivo) ব্যাকগ্রাউন্ডে অ্যাপ খোলা ও Accessibility ধরে রাখতে ব্যাটারি অপটিমাইজেশন বন্ধ রাখতে হয়।
- এই কোড এই পরিবেশে কম্পাইল করে পরীক্ষা করা যায়নি (Android SDK/ইন্টারনেট ছিল না)। প্রথম বিল্ডে ছোটখাটো কম্পাইল এরর আসতে পারে: error লগ AI-কে দিলে ঠিক করে দেবে।

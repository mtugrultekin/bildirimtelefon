# 📱 Bildirim Telefon - Android Localhost System

Android telefonu bildirim cihazı olarak kullanan localhost tabanlı bildirim sistemi. Web arayüzünden Android telefona gerçek zamanlı bildirimler gönderebilirsiniz.

## ✨ Özellikler

### 🖥️ Web Sunucu
- **Modern Web Arayüzü**: Bootstrap 5 ile responsive tasarım
- **WebSocket Bağlantısı**: Gerçek zamanlı iletişim
- **QR Kod Üretimi**: Android app bağlantısı için
- **Cihaz Yönetimi**: Bağlı Android cihazları görüntüleme
- **Bildirim Geçmişi**: Tüm gönderilen bildirimlerin kaydı

### 📱 Android Uygulaması
- **QR Kod Tarama**: Kolay sunucu bağlantısı
- **Gerçek Bildirimler**: Android sistem bildirimleri
- **Sesli Mesajlar**: Text-to-Speech ile sesli okuma
- **Acil Durum Modu**: Özel ses ve titreşim
- **Arka Plan Servisi**: Sürekli bağlantı
- **Otomatik Başlatma**: Telefon açıldığında otomatik başlat

### 🔄 İletişim Özellikleri
- **Tekil Bildirim**: Belirli cihaza bildirim gönderme
- **Toplu Bildirim**: Tüm bağlı cihazlara gönderme
- **Sesli Mesaj**: Android'de sesli okuma
- **Acil Durum**: Özel ses ve titreşim ile acil bildirim
- **Durum Takibi**: Bildirimlerin teslim durumu

## 🛠️ Kurulum

### 1. Web Sunucu Kurulumu

```bash
# Bağımlılıkları yükle
npm install

# Sunucuyu başlat
npm start
```

Sunucu `http://localhost:3000` adresinde çalışacak.

### 2. Android Uygulaması Kurulumu

#### Android Studio ile:
```bash
# Android Studio'da projeyi aç
cd android
# Android Studio ile build et ve cihaza yükle
```

#### Komut satırından:
```bash
# JAVA_HOME'u set et (Linux/macOS)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64  # Linux
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home  # macOS

cd android
./gradlew assembleDebug
# APK dosyası: android/app/build/outputs/apk/debug/app-debug.apk
```

#### Windows:
```cmd
# JAVA_HOME'u set et
set JAVA_HOME=C:\Program Files\Java\jdk-17

cd android
gradlew.bat assembleDebug
```

## 📋 Kullanım Adımları

### 1. Sistemi Başlat
1. Web sunucusunu çalıştır: `npm start`
2. Tarayıcıda `http://localhost:3000` adresini aç
3. Android uygulamasını telefonuna yükle

### 2. Android Telefonu Bağla
1. Android uygulamasını aç
2. "QR Kod Tarat" butonuna tıkla
3. Web arayüzünde "QR Kod Göster" butonuna tıkla
4. QR kodu Android uygulaması ile tarat
5. "Bağlan" butonuna tıkla
6. Gerekli izinleri ver (bildirim, kamera)

### 3. Bildirim Gönder
1. Web arayüzünde "Bildirim Gönder" bölümünü kullan
2. Başlık ve mesajı yaz
3. Hedef cihazı seç (veya tüm cihazlar)
4. Seçenekleri işaretle (acil, ses, titreşim)
5. "Bildirim Gönder" butonuna tıkla

### 4. Sesli Mesaj Gönder
1. "Sesli Mesaj Gönder" bölümünü kullan
2. Okunacak metni yaz
3. Hedef cihazı seç
4. "Sesli Mesaj Gönder" butonuna tıkla

### 5. Acil Durum Bildirimi
1. Kırmızı "ACİL DURUM GÖNDER" butonuna tıkla
2. Onay ver
3. Tüm bağlı cihazlara özel ses ve titreşimle bildirim gönderilir

## 🏗️ Sistem Mimarisi

```
┌─────────────────┐    WebSocket     ┌─────────────────┐
│   Web Browser   │◄────────────────►│  Node.js Server │
│  (localhost:3000)│                  │   (Express +    │
└─────────────────┘                  │   Socket.io)    │
                                     └─────────┬───────┘
                                               │
                                     WebSocket │
                                               ▼
                                     ┌─────────────────┐
                                     │  Android App    │
                                     │  (QR Scanner +  │
                                     │  Notifications) │
                                     └─────────────────┘
```

## 📂 Proje Yapısı

```
bildirimtelefon/
├── server/
│   └── index.js              # Node.js WebSocket sunucusu
├── web/
│   ├── index.html           # Web arayüzü
│   ├── app.js               # Frontend JavaScript
│   └── style.css            # CSS stilleri
├── android/
│   ├── app/
│   │   ├── src/main/java/   # Android Kotlin kodları
│   │   ├── src/main/res/    # Android kaynakları
│   │   └── build.gradle     # Android bağımlılıkları
│   ├── build.gradle         # Proje yapılandırması
│   └── settings.gradle      # Gradle ayarları
├── package.json             # Node.js bağımlılıkları
└── README.md                # Bu dosya
```

## 🔧 API Endpoints

### Bildirim API'leri
- `POST /api/send-notification` - Bildirim gönder
- `POST /api/send-voice` - Sesli mesaj gönder
- `POST /api/emergency` - Acil durum bildirimi

### Cihaz Yönetimi
- `GET /api/devices` - Bağlı cihazları listele
- `GET /api/notifications` - Bildirim geçmişi

### Sistem
- `GET /health` - Sistem durumu
- `GET /api/qr-code` - QR kod üret

## 📱 Android App Özellikleri

### İzinler
- **INTERNET**: WebSocket bağlantısı
- **POST_NOTIFICATIONS**: Bildirim gösterme
- **CAMERA**: QR kod tarama
- **VIBRATE**: Titreşim
- **FOREGROUND_SERVICE**: Arka plan servisi
- **WAKE_LOCK**: Ekran kilidi

### Servisler
- **NotificationService**: WebSocket bağlantısı ve bildirim alma
- **BootReceiver**: Otomatik başlatma
- **NotificationReceiver**: Bildirim işleme

## 🚀 Geliştirme

### Web Sunucu Geliştirme
```bash
npm run dev  # nodemon ile otomatik yeniden başlatma
```

### Android Geliştirme
```bash
cd android
./gradlew assembleDebug  # Debug APK oluştur
./gradlew installDebug   # Cihaza yükle
```

## 🔒 Güvenlik

- **Localhost Only**: Sadece yerel ağda çalışır
- **WebSocket Güvenliği**: Socket.io ile güvenli bağlantı
- **İzin Kontrolü**: Android'de gerekli izinler
- **Hata Yönetimi**: Kapsamlı hata yakalama

## 📊 Özellikler Detayı

### Web Arayüzü
- Gerçek zamanlı cihaz durumu
- Bildirim geçmişi ve istatistikler
- QR kod üretimi ve gösterimi
- Responsive tasarım (mobil uyumlu)
- Toast bildirimleri

### Android App
- Modern Material Design
- QR kod tarayıcısı (ZXing)
- Text-to-Speech (Türkçe/İngilizce)
- Ön plan servisi (sürekli çalışma)
- Bildirim kanalları (normal/acil)
- Ayarlar sayfası

## 🐛 Sorun Giderme

### Web Sunucu Çalışmıyor
1. Port 3000'in boş olduğunu kontrol et
2. `npm install` komutu çalıştırıldı mı?
3. Node.js sürümü 14+ olmalı

### Android App Bağlanmıyor
1. Telefon ve bilgisayar aynı WiFi'de mi?
2. QR kod doğru tarandı mı?
3. Bildirim izni verildi mi?
4. Sunucu çalışıyor mu? (`http://localhost:3000/health`)

### Bildirimler Gelmiyor
1. Android'de bildirim izni aktif mi?
2. Uygulama arka planda çalışıyor mu?
3. WebSocket bağlantısı aktif mi?
4. Cihaz listesinde telefon görünüyor mu?

## 📞 Test Etme

### 1. Sistem Testi
```bash
# Sunucu durumu
curl http://localhost:3000/health

# QR kod testi
curl http://localhost:3000/api/qr-code

# Cihaz listesi
curl http://localhost:3000/api/devices
```

### 2. Android App Testi
- Ana ekranda "Test Bildirimi Gönder" butonu
- "Test Sesli Mesaj" butonu
- Ayarlar menüsünden özelleştirme

## 📄 Lisans

MIT License

---

## 🎯 Kullanım Senaryoları

- **Ev Otomasyonu**: Akıllı ev sistemlerinden bildirim alma
- **İş Yerleri**: Personel bilgilendirme sistemi
- **Güvenlik**: Alarm ve güvenlik bildirimleri
- **Geliştirme**: Test bildirimleri ve debug mesajları
- **Acil Durum**: Hızlı uyarı sistemi

**Not**: Bu sistem localhost'ta çalışır, internet bağlantısı gerektirmez. Telefon ve bilgisayar aynı WiFi ağında olmalıdır.
const express = require('express');
const http = require('http');
const socketIo = require('socket.io');
const cors = require('cors');
const bodyParser = require('body-parser');
const QRCode = require('qrcode');
const fs = require('fs').promises;
const path = require('path');

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));
app.use(express.static('web'));

// Bağlı Android cihazları
let connectedDevices = new Map();
let notifications = [];

// Android cihaz bağlantısı
io.on('connection', (socket) => {
    console.log('🔌 Yeni bağlantı:', socket.id);
    console.log('📊 Toplam bağlantı sayısı:', io.engine.clientsCount);

    // Android cihaz kaydı
    socket.on('register-device', (deviceInfo) => {
        const device = {
            id: socket.id,
            ...deviceInfo,
            connectedAt: new Date(),
            lastSeen: new Date()
        };
        
        connectedDevices.set(socket.id, device);
        console.log('📱 Android cihaz kaydedildi:', deviceInfo.deviceName || 'Bilinmeyen Cihaz');
        console.log('📊 Kayıtlı cihaz sayısı:', connectedDevices.size);
        
        // Web arayüzüne cihaz listesini gönder
        const deviceList = Array.from(connectedDevices.values());
        console.log('📤 Cihaz listesi gönderiliyor:', deviceList.map(d => d.deviceName));
        io.emit('devices-updated', deviceList);
    });

    // Cihaz bağlantısı kesildiğinde
    socket.on('disconnect', () => {
        const device = connectedDevices.get(socket.id);
        if (device) {
            console.log('📱 Android cihaz bağlantısı kesildi:', device.deviceName);
            connectedDevices.delete(socket.id);
            io.emit('devices-updated', Array.from(connectedDevices.values()));
        }
    });

    // Bildirim durumu güncellemesi
    socket.on('notification-status', (data) => {
        console.log('📨 Bildirim durumu:', data);
        
        // Bildirim geçmişini güncelle
        const notification = notifications.find(n => n.id === data.notificationId);
        if (notification) {
            notification.status = data.status;
            notification.deliveredAt = data.status === 'delivered' ? new Date() : null;
        }
        
        // Web arayüzüne durumu bildir
        io.emit('notification-updated', data);
    });

    // Heartbeat
    socket.on('heartbeat', () => {
        const device = connectedDevices.get(socket.id);
        if (device) {
            device.lastSeen = new Date();
        }
    });
});

// API Routes

// Bildirim gönderme
app.post('/api/send-notification', async (req, res) => {
    try {
        const { message, title, urgent = false, deviceId, sound = true, vibrate = true } = req.body;

        if (!message) {
            return res.status(400).json({
                error: 'Mesaj gerekli'
            });
        }

        const notification = {
            id: Date.now() + Math.random().toString(36).substr(2, 9),
            title: title || 'Bildirim',
            message,
            urgent,
            sound,
            vibrate,
            timestamp: new Date(),
            status: 'pending',
            deviceId
        };

        notifications.unshift(notification);

        // Belirli cihaza gönder veya tüm cihazlara
        if (deviceId && connectedDevices.has(deviceId)) {
            io.to(deviceId).emit('new-notification', notification);
            console.log(`📤 Bildirim gönderildi (${deviceId}):`, message);
        } else {
            // Tüm bağlı cihazlara gönder
            const deviceCount = connectedDevices.size;
            console.log(`📤 Bildirim ${deviceCount} cihaza gönderiliyor:`, message);
            console.log('📱 Bağlı cihazlar:', Array.from(connectedDevices.values()).map(d => d.deviceName));
            io.emit('new-notification', notification);
            console.log('✅ Bildirim emit edildi');
        }

        res.json({
            success: true,
            message: 'Bildirim gönderildi',
            notification
        });

    } catch (error) {
        console.error('Bildirim gönderme hatası:', error);
        res.status(500).json({
            error: 'Bildirim gönderilemedi',
            details: error.message
        });
    }
});

// Sesli mesaj gönderme
app.post('/api/send-voice', async (req, res) => {
    try {
        const { message, deviceId } = req.body;

        if (!message) {
            return res.status(400).json({
                error: 'Mesaj gerekli'
            });
        }

        const voiceNotification = {
            id: Date.now() + Math.random().toString(36).substr(2, 9),
            type: 'voice',
            message,
            timestamp: new Date(),
            status: 'pending',
            deviceId
        };

        notifications.unshift(voiceNotification);

        // Sesli mesaj gönder
        if (deviceId && connectedDevices.has(deviceId)) {
            io.to(deviceId).emit('voice-message', voiceNotification);
            console.log(`🔊 Sesli mesaj gönderildi (${deviceId}):`, message);
        } else {
            io.emit('voice-message', voiceNotification);
            console.log('🔊 Sesli mesaj tüm cihazlara gönderildi:', message);
        }

        res.json({
            success: true,
            message: 'Sesli mesaj gönderildi',
            notification: voiceNotification
        });

    } catch (error) {
        console.error('Sesli mesaj gönderme hatası:', error);
        res.status(500).json({
            error: 'Sesli mesaj gönderilemedi',
            details: error.message
        });
    }
});

// Acil durum bildirimi
app.post('/api/emergency', async (req, res) => {
    try {
        const { message = 'ACİL DURUM!' } = req.body;

        const emergencyNotification = {
            id: Date.now() + Math.random().toString(36).substr(2, 9),
            title: '🚨 ACİL DURUM',
            message,
            urgent: true,
            sound: true,
            vibrate: true,
            emergency: true,
            timestamp: new Date(),
            status: 'pending'
        };

        notifications.unshift(emergencyNotification);

        // Tüm cihazlara acil durum bildirimi
        io.emit('emergency-notification', emergencyNotification);
        console.log('🚨 ACİL DURUM BİLDİRİMİ:', message);

        res.json({
            success: true,
            message: 'Acil durum bildirimi gönderildi',
            notification: emergencyNotification
        });

    } catch (error) {
        console.error('Acil durum bildirimi hatası:', error);
        res.status(500).json({
            error: 'Acil durum bildirimi gönderilemedi',
            details: error.message
        });
    }
});

// Bağlı cihazları listele
app.get('/api/devices', (req, res) => {
    res.json({
        success: true,
        devices: Array.from(connectedDevices.values())
    });
});

// Bildirim geçmişi
app.get('/api/notifications', (req, res) => {
    const { limit = 50, offset = 0 } = req.query;
    const start = parseInt(offset);
    const end = start + parseInt(limit);
    
    res.json({
        success: true,
        notifications: notifications.slice(start, end),
        total: notifications.length
    });
});

// QR kod oluşturma (Android app bağlantısı için)
app.get('/api/qr-code', async (req, res) => {
    try {
        const connectionInfo = {
            serverUrl: `http://localhost:${PORT}`,
            socketUrl: `ws://localhost:${PORT}`
        };
        
        const qrCodeDataURL = await QRCode.toDataURL(JSON.stringify(connectionInfo));
        
        res.json({
            success: true,
            qrCode: qrCodeDataURL,
            connectionInfo
        });
    } catch (error) {
        res.status(500).json({
            error: 'QR kod oluşturulamadı',
            details: error.message
        });
    }
});

// Sistem durumu
app.get('/health', (req, res) => {
    res.json({
        status: 'OK',
        message: 'Bildirim sistemi çalışıyor',
        connectedDevices: connectedDevices.size,
        totalNotifications: notifications.length,
        timestamp: new Date().toISOString()
    });
});

// Debug endpoint - cihaz detayları
app.get('/api/debug', (req, res) => {
    const deviceDetails = Array.from(connectedDevices.values()).map(device => ({
        id: device.id,
        deviceName: device.deviceName,
        deviceModel: device.deviceModel,
        androidVersion: device.androidVersion,
        connectedAt: device.connectedAt,
        lastSeen: device.lastSeen,
        timeSinceLastSeen: new Date() - device.lastSeen
    }));

    res.json({
        success: true,
        serverTime: new Date().toISOString(),
        connectedDevices: connectedDevices.size,
        socketConnections: io.engine.clientsCount,
        devices: deviceDetails,
        recentNotifications: notifications.slice(0, 5),
        webSocketUrl: `ws://localhost:${PORT}`
    });
});

// Ana sayfa
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, '../web/index.html'));
});

// Heartbeat kontrolü (bağlantı kesilmiş cihazları temizle)
setInterval(() => {
    const now = new Date();
    for (const [socketId, device] of connectedDevices.entries()) {
        const timeDiff = now - device.lastSeen;
        if (timeDiff > 60000) { // 1 dakika
            console.log('📱 Cihaz bağlantısı zaman aşımı:', device.deviceName);
            connectedDevices.delete(socketId);
            io.emit('devices-updated', Array.from(connectedDevices.values()));
        }
    }
}, 30000); // 30 saniyede bir kontrol

server.listen(PORT, () => {
    console.log('🚀 Bildirim sunucusu çalışıyor');
    console.log(`📱 Web Arayüz: http://localhost:${PORT}`);
    console.log(`🔌 WebSocket: ws://localhost:${PORT}`);
    console.log(`📊 Bağlı cihaz sayısı: ${connectedDevices.size}`);
});
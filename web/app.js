// Android Localhost Bildirim Sistemi - Web Arayüzü
class AndroidNotificationSystem {
    constructor() {
        this.socket = null;
        this.connectedDevices = [];
        this.notifications = [];
        this.init();
    }

    init() {
        this.connectWebSocket();
        this.bindEvents();
        this.loadNotifications();
        this.loadQRCode();
        
        // Otomatik yenileme
        setInterval(() => {
            this.loadNotifications();
        }, 10000); // 10 saniyede bir
    }

    connectWebSocket() {
        this.socket = io();
        
        this.socket.on('connect', () => {
            console.log('🔌 WebSocket bağlantısı kuruldu');
            this.updateConnectionStatus(true);
        });

        this.socket.on('disconnect', () => {
            console.log('❌ WebSocket bağlantısı kesildi');
            this.updateConnectionStatus(false);
        });

        // Cihaz listesi güncellemesi
        this.socket.on('devices-updated', (devices) => {
            console.log('📱 Cihaz listesi güncellendi:', devices);
            this.connectedDevices = devices;
            this.updateDevicesList();
            this.updateDeviceSelectors();
        });

        // Bildirim durumu güncellemesi
        this.socket.on('notification-updated', (data) => {
            console.log('📨 Bildirim durumu güncellendi:', data);
            this.updateNotificationStatus(data);
        });

        // Gerçek zamanlı bildirim eklemesi
        this.socket.on('notification-sent', (notification) => {
            this.notifications.unshift(notification);
            this.renderNotifications();
            this.updateStats();
        });
    }

    bindEvents() {
        // Bildirim gönderme formu
        document.getElementById('notification-form').addEventListener('submit', (e) => {
            e.preventDefault();
            this.sendNotification();
        });

        // Sesli mesaj formu
        document.getElementById('voice-form').addEventListener('submit', (e) => {
            e.preventDefault();
            this.sendVoiceMessage();
        });

        // Acil durum butonu
        document.getElementById('emergency-btn').addEventListener('click', () => {
            this.sendEmergency();
        });

        // QR kod göster
        document.getElementById('show-qr-btn').addEventListener('click', () => {
            this.showQRModal();
        });

        // APK indir
        document.getElementById('download-apk-btn').addEventListener('click', () => {
            this.downloadAPK();
        });

        // Yenile butonu
        document.getElementById('refresh-notifications').addEventListener('click', () => {
            this.loadNotifications();
        });
    }

    async sendNotification() {
        const title = document.getElementById('notification-title').value;
        const message = document.getElementById('notification-message').value;
        const deviceId = document.getElementById('target-device').value;
        const urgent = document.getElementById('urgent-notification').checked;
        const sound = document.getElementById('sound-notification').checked;
        const vibrate = document.getElementById('vibrate-notification').checked;

        if (!message.trim()) {
            this.showToast('Mesaj gerekli!', 'error');
            return;
        }

        try {
            const response = await fetch('/api/send-notification', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    title,
                    message,
                    deviceId: deviceId || null,
                    urgent,
                    sound,
                    vibrate
                })
            });

            const result = await response.json();

            if (result.success) {
                this.showToast('Bildirim gönderildi!', 'success');
                document.getElementById('notification-form').reset();
                document.getElementById('notification-title').value = 'Yeni Bildirim';
                document.getElementById('urgent-notification').checked = true;
                document.getElementById('sound-notification').checked = true;
                document.getElementById('vibrate-notification').checked = true;
                this.loadNotifications();
            } else {
                this.showToast(result.error || 'Bildirim gönderilemedi', 'error');
            }
        } catch (error) {
            this.showToast('Bağlantı hatası: ' + error.message, 'error');
        }
    }

    async sendVoiceMessage() {
        const message = document.getElementById('voice-message').value;
        const deviceId = document.getElementById('voice-target-device').value;

        if (!message.trim()) {
            this.showToast('Sesli mesaj gerekli!', 'error');
            return;
        }

        try {
            const response = await fetch('/api/send-voice', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    message,
                    deviceId: deviceId || null
                })
            });

            const result = await response.json();

            if (result.success) {
                this.showToast('Sesli mesaj gönderildi!', 'success');
                document.getElementById('voice-form').reset();
                this.loadNotifications();
            } else {
                this.showToast(result.error || 'Sesli mesaj gönderilemedi', 'error');
            }
        } catch (error) {
            this.showToast('Bağlantı hatası: ' + error.message, 'error');
        }
    }

    async sendEmergency() {
        const message = document.getElementById('emergency-message').value;

        if (!confirm('ACİL DURUM BİLDİRİMİ gönderilsin mi?\n\nTüm bağlı Android cihazlara gönderilecek!')) {
            return;
        }

        try {
            const response = await fetch('/api/emergency', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ message })
            });

            const result = await response.json();

            if (result.success) {
                this.showToast('ACİL DURUM BİLDİRİMİ gönderildi!', 'success');
                this.loadNotifications();
            } else {
                this.showToast(result.error || 'Acil durum bildirimi gönderilemedi', 'error');
            }
        } catch (error) {
            this.showToast('Bağlantı hatası: ' + error.message, 'error');
        }
    }

    async loadNotifications() {
        try {
            const response = await fetch('/api/notifications?limit=20');
            const result = await response.json();

            if (result.success) {
                this.notifications = result.notifications;
                this.renderNotifications();
                this.updateStats();
            }
        } catch (error) {
            console.error('Bildirimler yüklenemedi:', error);
        }
    }

    async loadQRCode() {
        try {
            const response = await fetch('/api/qr-code');
            const result = await response.json();

            if (result.success) {
                const qrImage = document.getElementById('qr-code-image');
                const modalQrImage = document.getElementById('modal-qr-image');
                
                qrImage.src = result.qrCode;
                modalQrImage.src = result.qrCode;
            }
        } catch (error) {
            console.error('QR kod yüklenemedi:', error);
        }
    }

    updateDevicesList() {
        const container = document.getElementById('connected-devices');
        
        if (this.connectedDevices.length === 0) {
            container.innerHTML = `
                <div class="text-center text-muted">
                    <i class="fas fa-mobile-alt fa-3x mb-3 opacity-50"></i>
                    <p>Henüz bağlı cihaz yok</p>
                    <p class="small">Android uygulamasını yükleyip QR kodu taratın</p>
                </div>
            `;
            return;
        }

        const devicesHTML = this.connectedDevices.map(device => {
            const connectedTime = new Date(device.connectedAt).toLocaleString('tr-TR');
            const lastSeen = new Date(device.lastSeen).toLocaleString('tr-TR');
            
            return `
                <div class="device-card fade-in">
                    <div class="d-flex justify-content-between align-items-center">
                        <div>
                            <h6 class="mb-1">
                                <i class="fas fa-mobile-alt device-online me-2"></i>
                                ${device.deviceName || 'Android Cihaz'}
                            </h6>
                            <small class="text-muted">
                                <i class="fas fa-info-circle me-1"></i>
                                ${device.deviceModel || 'Bilinmeyen Model'}
                            </small>
                            <br>
                            <small class="text-muted">
                                <i class="fas fa-clock me-1"></i>
                                Bağlandı: ${connectedTime}
                            </small>
                        </div>
                        <div class="text-end">
                            <span class="badge bg-success">
                                <i class="fas fa-circle me-1"></i>
                                Aktif
                            </span>
                            <br>
                            <small class="text-muted">Son: ${lastSeen}</small>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        container.innerHTML = devicesHTML;
    }

    updateDeviceSelectors() {
        const selectors = ['target-device', 'voice-target-device'];
        
        selectors.forEach(selectorId => {
            const selector = document.getElementById(selectorId);
            const currentValue = selector.value;
            
            // Mevcut seçenekleri temizle (ilk seçenek hariç)
            while (selector.children.length > 1) {
                selector.removeChild(selector.lastChild);
            }
            
            // Cihazları ekle
            this.connectedDevices.forEach(device => {
                const option = document.createElement('option');
                option.value = device.id;
                option.textContent = device.deviceName || 'Android Cihaz';
                selector.appendChild(option);
            });
            
            // Önceki seçimi geri yükle
            selector.value = currentValue;
        });
    }

    renderNotifications() {
        const container = document.getElementById('recent-notifications');
        
        if (this.notifications.length === 0) {
            container.innerHTML = `
                <div class="text-center text-muted">
                    <i class="fas fa-inbox fa-2x mb-3 opacity-50"></i>
                    <p>Henüz bildirim yok</p>
                </div>
            `;
            return;
        }

        const notificationsHTML = this.notifications.slice(0, 10).map(notification => {
            const time = new Date(notification.timestamp).toLocaleString('tr-TR');
            const typeClass = notification.emergency ? 'emergency' : 
                             notification.type === 'voice' ? 'voice' : 'normal';
            const statusClass = notification.status || 'pending';
            
            const typeIcon = notification.emergency ? 'fas fa-exclamation-triangle' :
                           notification.type === 'voice' ? 'fas fa-volume-up' :
                           'fas fa-bell';

            return `
                <div class="notification-item ${typeClass} ${statusClass} fade-in">
                    <div class="d-flex justify-content-between align-items-start">
                        <div class="flex-grow-1">
                            <div class="d-flex align-items-center mb-1">
                                <span class="status-indicator status-${statusClass}"></span>
                                <i class="${typeIcon} me-2"></i>
                                <strong class="small">${notification.title || 'Bildirim'}</strong>
                            </div>
                            <div class="small mb-1" style="color: #666;">
                                ${notification.message}
                            </div>
                            <div class="d-flex justify-content-between align-items-center">
                                <small class="text-muted">${time}</small>
                                <small class="badge bg-secondary">${this.getStatusText(statusClass)}</small>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        container.innerHTML = notificationsHTML;
    }

    updateStats() {
        const total = this.notifications.length;
        const delivered = this.notifications.filter(n => n.status === 'delivered').length;
        
        document.getElementById('total-notifications').textContent = total;
        document.getElementById('delivered-notifications').textContent = delivered;
    }

    updateConnectionStatus(connected) {
        const statusEl = document.getElementById('connection-status');
        if (connected) {
            statusEl.innerHTML = `
                <i class="fas fa-circle text-success me-1"></i>
                Sunucu Aktif
            `;
        } else {
            statusEl.innerHTML = `
                <i class="fas fa-circle text-danger me-1"></i>
                Bağlantı Kesildi
            `;
        }
    }

    updateNotificationStatus(data) {
        const notification = this.notifications.find(n => n.id === data.notificationId);
        if (notification) {
            notification.status = data.status;
            this.renderNotifications();
            this.updateStats();
        }
    }

    showQRModal() {
        const qrContainer = document.getElementById('qr-code-container');
        qrContainer.classList.remove('d-none');
        
        const modal = new bootstrap.Modal(document.getElementById('qr-modal'));
        modal.show();
    }

    downloadAPK() {
        this.showToast('APK dosyası henüz hazır değil. Android Studio ile derlenecek.', 'warning');
    }

    getStatusText(status) {
        const statusTexts = {
            'pending': 'Bekliyor',
            'delivered': 'Teslim Edildi',
            'failed': 'Başarısız'
        };
        return statusTexts[status] || status;
    }

    showToast(message, type = 'info') {
        const toastEl = document.getElementById('notification-toast');
        const toastBody = document.getElementById('toast-message');
        
        toastBody.textContent = message;
        toastEl.className = `toast ${type}`;
        
        const toast = new bootstrap.Toast(toastEl, {
            autohide: true,
            delay: type === 'error' ? 5000 : 3000
        });
        
        toast.show();
    }
}

// Sayfa yüklendiğinde sistemi başlat
document.addEventListener('DOMContentLoaded', () => {
    new AndroidNotificationSystem();
    
    // Cihaz sayısını güncelle
    const updateDeviceCount = () => {
        fetch('/api/devices')
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    document.getElementById('connected-count').textContent = data.devices.length;
                }
            })
            .catch(error => console.error('Cihaz sayısı alınamadı:', error));
    };
    
    updateDeviceCount();
    setInterval(updateDeviceCount, 5000); // 5 saniyede bir güncelle
});
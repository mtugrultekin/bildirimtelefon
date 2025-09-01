// Ana JavaScript dosyası
class NotificationApp {
    constructor() {
        this.apiBase = '/api';
        this.init();
    }

    init() {
        this.bindEvents();
        this.loadStats();
        this.loadRecentActivities();
        this.setupCharCounters();
        this.checkSystemStatus();
        
        // Otomatik yenileme (30 saniyede bir)
        setInterval(() => {
            this.loadStats();
            this.loadRecentActivities();
        }, 30000);
    }

    bindEvents() {
        // SMS form
        document.getElementById('sms-form').addEventListener('submit', (e) => {
            e.preventDefault();
            this.sendSMS();
        });

        // Toplu SMS form
        document.getElementById('bulk-sms-form').addEventListener('submit', (e) => {
            e.preventDefault();
            this.sendBulkSMS();
        });

        // Arama form
        document.getElementById('call-form').addEventListener('submit', (e) => {
            e.preventDefault();
            this.makeCall();
        });

        // Acil durum butonu
        document.getElementById('emergency-btn').addEventListener('click', () => {
            this.sendEmergency();
        });

        // Yenile butonu
        document.getElementById('load-more-btn').addEventListener('click', () => {
            this.loadRecentActivities();
        });

        // Telefon numarası formatlaması
        const phoneInputs = document.querySelectorAll('input[type="tel"]');
        phoneInputs.forEach(input => {
            input.addEventListener('input', this.formatPhoneNumber);
        });
    }

    setupCharCounters() {
        // SMS karakter sayacı
        const smsMessage = document.getElementById('sms-message');
        const smsCounter = document.getElementById('sms-char-count');
        
        smsMessage.addEventListener('input', () => {
            const count = smsMessage.value.length;
            smsCounter.textContent = count;
            
            if (count > 140) {
                smsCounter.className = 'char-counter danger';
            } else if (count > 120) {
                smsCounter.className = 'char-counter warning';
            } else {
                smsCounter.className = 'char-counter';
            }
        });

        // Toplu SMS karakter sayacı
        const bulkMessage = document.getElementById('bulk-message');
        const bulkCounter = document.getElementById('bulk-char-count');
        
        bulkMessage.addEventListener('input', () => {
            const count = bulkMessage.value.length;
            bulkCounter.textContent = count;
            
            if (count > 140) {
                bulkCounter.className = 'char-counter danger';
            } else if (count > 120) {
                bulkCounter.className = 'char-counter warning';
            } else {
                bulkCounter.className = 'char-counter';
            }
        });
    }

    formatPhoneNumber(e) {
        let value = e.target.value.replace(/\D/g, '');
        
        if (value.startsWith('90')) {
            value = value.substring(2);
        }
        
        if (value.length > 0) {
            if (value.length <= 3) {
                value = value;
            } else if (value.length <= 6) {
                value = value.substring(0, 3) + ' ' + value.substring(3);
            } else if (value.length <= 8) {
                value = value.substring(0, 3) + ' ' + value.substring(3, 6) + ' ' + value.substring(6);
            } else {
                value = value.substring(0, 3) + ' ' + value.substring(3, 6) + ' ' + value.substring(6, 8) + ' ' + value.substring(8, 10);
            }
            
            if (value.length > 0) {
                value = '0' + value;
            }
        }
        
        e.target.value = value;
    }

    async sendSMS() {
        const phone = document.getElementById('sms-phone').value;
        const message = document.getElementById('sms-message').value;
        const urgent = document.getElementById('sms-urgent').checked;

        if (!phone || !message) {
            this.showToast('Telefon numarası ve mesaj gerekli!', 'error');
            return;
        }

        this.showLoading('SMS gönderiliyor...');

        try {
            const response = await fetch(`${this.apiBase}/notification/sms`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ phoneNumber: phone, message, urgent })
            });

            const result = await response.json();

            if (result.success) {
                this.showToast('SMS başarıyla gönderildi!', 'success');
                document.getElementById('sms-form').reset();
                this.loadStats();
                this.loadRecentActivities();
            } else {
                this.showToast(result.error || 'SMS gönderilemedi', 'error');
            }
        } catch (error) {
            this.showToast('Bağlantı hatası: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }

    async sendBulkSMS() {
        const phonesText = document.getElementById('bulk-phones').value;
        const message = document.getElementById('bulk-message').value;
        const urgent = document.getElementById('bulk-urgent').checked;

        if (!phonesText || !message) {
            this.showToast('Telefon numaraları ve mesaj gerekli!', 'error');
            return;
        }

        const phoneNumbers = phonesText.split('\n')
            .map(phone => phone.trim())
            .filter(phone => phone.length > 0);

        if (phoneNumbers.length === 0) {
            this.showToast('En az bir geçerli telefon numarası gerekli!', 'error');
            return;
        }

        this.showLoading(`${phoneNumbers.length} numaraya SMS gönderiliyor...`);

        try {
            const response = await fetch(`${this.apiBase}/notification/sms/bulk`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ phoneNumbers, message, urgent })
            });

            const result = await response.json();

            if (result.success) {
                this.showToast(result.message, 'success');
                document.getElementById('bulk-sms-form').reset();
                this.loadStats();
                this.loadRecentActivities();
            } else {
                this.showToast(result.error || 'Toplu SMS gönderilemedi', 'error');
            }
        } catch (error) {
            this.showToast('Bağlantı hatası: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }

    async makeCall() {
        const phone = document.getElementById('call-phone').value;
        const message = document.getElementById('call-message').value;
        const urgent = document.getElementById('call-urgent').checked;

        if (!phone || !message) {
            this.showToast('Telefon numarası ve mesaj gerekli!', 'error');
            return;
        }

        this.showLoading('Arama yapılıyor...');

        try {
            const response = await fetch(`${this.apiBase}/notification/call`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ phoneNumber: phone, message, urgent })
            });

            const result = await response.json();

            if (result.success) {
                this.showToast('Arama başarıyla yapıldı!', 'success');
                document.getElementById('call-form').reset();
                this.loadStats();
                this.loadRecentActivities();
            } else {
                this.showToast(result.error || 'Arama yapılamadı', 'error');
            }
        } catch (error) {
            this.showToast('Bağlantı hatası: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }

    async sendEmergency() {
        const message = document.getElementById('emergency-message').value || 'ACİL DURUM BİLDİRİMİ!';

        if (!confirm('Acil durum bildirimi gönderilsin mi?\n\nTüm kayıtlı numaralara SMS gönderilecek ve ilk numaraya arama yapılacak.')) {
            return;
        }

        this.showLoading('ACİL DURUM BİLDİRİMİ GÖNDERİLİYOR...');

        try {
            const response = await fetch(`${this.apiBase}/notification/emergency`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ message })
            });

            const result = await response.json();

            if (result.success) {
                this.showToast('Acil durum bildirimi gönderildi!', 'success');
                this.loadStats();
                this.loadRecentActivities();
            } else {
                this.showToast(result.error || 'Acil durum bildirimi gönderilemedi', 'error');
            }
        } catch (error) {
            this.showToast('Bağlantı hatası: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }

    async loadStats() {
        try {
            const response = await fetch(`${this.apiBase}/notification/history?limit=1000`);
            const result = await response.json();

            if (result.success) {
                const notifications = result.data.notifications;
                
                // Toplam bildirim
                document.getElementById('total-notifications').textContent = notifications.length;
                
                // Bugün gönderilen
                const today = new Date();
                today.setHours(0, 0, 0, 0);
                const todayCount = notifications.filter(n => 
                    new Date(n.timestamp) >= today
                ).length;
                document.getElementById('today-notifications').textContent = todayCount;
                
                // SMS sayısı
                const smsCount = notifications.filter(n => 
                    n.type === 'SMS' || n.type === 'EMERGENCY_SMS'
                ).length;
                document.getElementById('sms-count').textContent = smsCount;
                
                // Arama sayısı
                const callCount = notifications.filter(n => 
                    n.type === 'CALL' || n.type === 'EMERGENCY_CALL'
                ).length;
                document.getElementById('call-count').textContent = callCount;
            }
        } catch (error) {
            console.error('İstatistik yükleme hatası:', error);
        }
    }

    async loadRecentActivities() {
        try {
            const response = await fetch(`${this.apiBase}/notification/history?limit=10`);
            const result = await response.json();

            if (result.success) {
                const activities = result.data.notifications;
                this.renderActivities(activities);
            }
        } catch (error) {
            console.error('Aktivite yükleme hatası:', error);
            document.getElementById('recent-activities').innerHTML = `
                <div class="text-center text-danger">
                    <i class="fas fa-exclamation-triangle"></i>
                    Aktiviteler yüklenemedi
                </div>
            `;
        }
    }

    renderActivities(activities) {
        const container = document.getElementById('recent-activities');
        
        if (activities.length === 0) {
            container.innerHTML = `
                <div class="text-center text-muted">
                    <i class="fas fa-inbox"></i>
                    <p>Henüz aktivite yok</p>
                </div>
            `;
            return;
        }

        const html = activities.map(activity => {
            const time = new Date(activity.timestamp).toLocaleString('tr-TR');
            const typeIcon = this.getTypeIcon(activity.type);
            const statusClass = activity.status === 'sent' ? 'success' : 'failed';
            const typeClass = activity.type.toLowerCase().includes('emergency') ? 'emergency' : 
                              activity.type.toLowerCase().includes('sms') ? 'sms' : 'call';

            return `
                <div class="activity-item ${typeClass} fade-in">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <div class="d-flex align-items-center mb-1">
                                <span class="status-dot ${statusClass}"></span>
                                <i class="${typeIcon} me-2"></i>
                                <strong>${this.getTypeText(activity.type)}</strong>
                            </div>
                            <div class="text-muted small mb-1">
                                <i class="fas fa-phone me-1"></i>
                                ${activity.phoneNumber}
                            </div>
                            <div class="small text-truncate" style="max-width: 200px;" title="${activity.message}">
                                ${activity.message}
                            </div>
                            ${activity.error ? `<div class="text-danger small mt-1"><i class="fas fa-exclamation-triangle me-1"></i>${activity.error}</div>` : ''}
                        </div>
                        <small class="text-muted">${time}</small>
                    </div>
                </div>
            `;
        }).join('');

        container.innerHTML = html;
    }

    getTypeIcon(type) {
        const icons = {
            'SMS': 'fas fa-sms',
            'CALL': 'fas fa-phone',
            'EMERGENCY_SMS': 'fas fa-exclamation-triangle',
            'EMERGENCY_CALL': 'fas fa-phone-volume'
        };
        return icons[type] || 'fas fa-bell';
    }

    getTypeText(type) {
        const texts = {
            'SMS': 'SMS',
            'CALL': 'Arama',
            'EMERGENCY_SMS': 'Acil SMS',
            'EMERGENCY_CALL': 'Acil Arama'
        };
        return texts[type] || type;
    }

    async checkSystemStatus() {
        try {
            const response = await fetch('/health');
            const result = await response.json();
            
            if (result.status === 'OK') {
                document.getElementById('status-indicator').innerHTML = `
                    <i class="fas fa-circle text-success me-1"></i>
                    Sistem Aktif
                `;
            } else {
                throw new Error('Sistem yanıt vermiyor');
            }
        } catch (error) {
            document.getElementById('status-indicator').innerHTML = `
                <i class="fas fa-circle text-danger me-1"></i>
                Sistem Hatası
            `;
        }
    }

    showLoading(text = 'İşlem yapılıyor...') {
        document.getElementById('loading-text').textContent = text;
        const modal = new bootstrap.Modal(document.getElementById('loading-modal'));
        modal.show();
    }

    hideLoading() {
        const modal = bootstrap.Modal.getInstance(document.getElementById('loading-modal'));
        if (modal) {
            modal.hide();
        }
    }

    showToast(message, type = 'info') {
        const toastEl = document.getElementById('notification-toast');
        const toastBody = document.getElementById('toast-message');
        
        toastBody.textContent = message;
        
        // Toast tipine göre stil
        toastEl.className = `toast ${type}`;
        
        const toast = new bootstrap.Toast(toastEl, {
            autohide: true,
            delay: type === 'error' ? 5000 : 3000
        });
        
        toast.show();
    }
}

// Uygulama başlat
document.addEventListener('DOMContentLoaded', () => {
    new NotificationApp();
});
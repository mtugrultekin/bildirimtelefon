const fs = require('fs').promises;
const path = require('path');

const LOGS_DIR = path.join(__dirname, '../../logs');
const NOTIFICATIONS_FILE = path.join(LOGS_DIR, 'notifications.json');

// Logs klasörünü oluştur
async function ensureLogsDir() {
    try {
        await fs.access(LOGS_DIR);
    } catch {
        await fs.mkdir(LOGS_DIR, { recursive: true });
    }
}

// Bildirimi kaydet
async function saveNotification(notification) {
    try {
        await ensureLogsDir();
        
        const timestamp = new Date().toISOString();
        const notificationWithTimestamp = {
            id: Date.now() + Math.random().toString(36).substr(2, 9),
            timestamp,
            ...notification
        };

        let notifications = [];
        try {
            const data = await fs.readFile(NOTIFICATIONS_FILE, 'utf8');
            notifications = JSON.parse(data);
        } catch (error) {
            // Dosya yoksa boş array ile başla
            notifications = [];
        }

        notifications.unshift(notificationWithTimestamp); // En yenisi başta
        
        // Son 1000 bildirimi tut
        if (notifications.length > 1000) {
            notifications = notifications.slice(0, 1000);
        }

        await fs.writeFile(NOTIFICATIONS_FILE, JSON.stringify(notifications, null, 2));
        
        console.log(`📝 Bildirim kaydedildi: ${notification.type} -> ${notification.phoneNumber}`);
        
        return notificationWithTimestamp;
    } catch (error) {
        console.error('Bildirim kaydetme hatası:', error);
        throw error;
    }
}

// Bildirim geçmişini getir
async function getNotificationHistory(options = {}) {
    try {
        const { limit = 50, offset = 0, type, status } = options;
        
        let notifications = [];
        try {
            const data = await fs.readFile(NOTIFICATIONS_FILE, 'utf8');
            notifications = JSON.parse(data);
        } catch (error) {
            return {
                notifications: [],
                total: 0,
                limit,
                offset
            };
        }

        // Filtreleme
        let filtered = notifications;
        if (type) {
            filtered = filtered.filter(n => n.type === type);
        }
        if (status) {
            filtered = filtered.filter(n => n.status === status);
        }

        const total = filtered.length;
        const paged = filtered.slice(offset, offset + limit);

        return {
            notifications: paged,
            total,
            limit,
            offset,
            hasMore: offset + limit < total
        };
    } catch (error) {
        console.error('Geçmiş getirme hatası:', error);
        throw error;
    }
}

// İstatistikleri getir
async function getNotificationStats() {
    try {
        const data = await fs.readFile(NOTIFICATIONS_FILE, 'utf8');
        const notifications = JSON.parse(data);

        const today = new Date();
        today.setHours(0, 0, 0, 0);

        const stats = {
            total: notifications.length,
            today: notifications.filter(n => new Date(n.timestamp) >= today).length,
            byType: {},
            byStatus: {},
            recentActivity: notifications.slice(0, 10)
        };

        // Tip bazında sayım
        notifications.forEach(n => {
            stats.byType[n.type] = (stats.byType[n.type] || 0) + 1;
            stats.byStatus[n.status] = (stats.byStatus[n.status] || 0) + 1;
        });

        return stats;
    } catch (error) {
        return {
            total: 0,
            today: 0,
            byType: {},
            byStatus: {},
            recentActivity: []
        };
    }
}

module.exports = {
    saveNotification,
    getNotificationHistory,
    getNotificationStats
};
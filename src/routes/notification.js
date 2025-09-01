const express = require('express');
const twilioService = require('../services/twilioService');
const { saveNotification, getNotificationHistory, getNotificationStats } = require('../services/notificationLogger');

const router = express.Router();

// SMS gönderme endpoint'i
router.post('/sms', async (req, res) => {
    try {
        const { phoneNumber, message, urgent = false } = req.body;

        if (!phoneNumber || !message) {
            return res.status(400).json({
                error: 'Telefon numarası ve mesaj gerekli'
            });
        }

        // Telefon numarasını Türkiye formatına çevir
        let formattedPhone = phoneNumber.replace(/\s+/g, '');
        if (formattedPhone.startsWith('0')) {
            formattedPhone = '+90' + formattedPhone.substring(1);
        } else if (!formattedPhone.startsWith('+')) {
            formattedPhone = '+90' + formattedPhone;
        }

        const result = await twilioService.sendSMS(formattedPhone, message);
        
        // Bildirimi kaydet
        await saveNotification({
            type: 'SMS',
            phoneNumber: formattedPhone,
            message,
            urgent,
            status: 'sent',
            sid: result.sid
        });

        res.json({
            success: true,
            message: 'SMS başarıyla gönderildi',
            data: result
        });

    } catch (error) {
        console.error('SMS gönderme hatası:', error);
        
        // Hatalı bildirimi kaydet
        await saveNotification({
            type: 'SMS',
            phoneNumber: req.body.phoneNumber,
            message: req.body.message,
            urgent: req.body.urgent || false,
            status: 'failed',
            error: error.message
        });

        res.status(500).json({
            error: 'SMS gönderilemedi',
            details: error.message
        });
    }
});

// Toplu SMS gönderme
router.post('/sms/bulk', async (req, res) => {
    try {
        const { phoneNumbers, message, urgent = false } = req.body;

        if (!phoneNumbers || !Array.isArray(phoneNumbers) || phoneNumbers.length === 0) {
            return res.status(400).json({
                error: 'En az bir telefon numarası gerekli'
            });
        }

        if (!message) {
            return res.status(400).json({
                error: 'Mesaj gerekli'
            });
        }

        // Telefon numaralarını formatla
        const formattedPhones = phoneNumbers.map(phone => {
            let formatted = phone.replace(/\s+/g, '');
            if (formatted.startsWith('0')) {
                return '+90' + formatted.substring(1);
            } else if (!formatted.startsWith('+')) {
                return '+90' + formatted;
            }
            return formatted;
        });

        const result = await twilioService.sendBulkSMS(formattedPhones, message);
        
        // Tüm bildirimleri kaydet
        for (const phone of formattedPhones) {
            const phoneResult = result.results.find(r => r.to === phone);
            const phoneError = result.errors.find(e => e.phoneNumber === phone);
            
            await saveNotification({
                type: 'SMS',
                phoneNumber: phone,
                message,
                urgent,
                status: phoneResult ? 'sent' : 'failed',
                sid: phoneResult?.sid,
                error: phoneError?.error
            });
        }

        res.json({
            success: true,
            message: `${result.sent} SMS gönderildi, ${result.failed} başarısız`,
            data: result
        });

    } catch (error) {
        console.error('Toplu SMS gönderme hatası:', error);
        res.status(500).json({
            error: 'Toplu SMS gönderilemedi',
            details: error.message
        });
    }
});

// Arama yapma endpoint'i
router.post('/call', async (req, res) => {
    try {
        const { phoneNumber, message, urgent = false } = req.body;

        if (!phoneNumber || !message) {
            return res.status(400).json({
                error: 'Telefon numarası ve mesaj gerekli'
            });
        }

        // Telefon numarasını formatla
        let formattedPhone = phoneNumber.replace(/\s+/g, '');
        if (formattedPhone.startsWith('0')) {
            formattedPhone = '+90' + formattedPhone.substring(1);
        } else if (!formattedPhone.startsWith('+')) {
            formattedPhone = '+90' + formattedPhone;
        }

        const result = await twilioService.makeCall(formattedPhone, message);
        
        // Bildirimi kaydet
        await saveNotification({
            type: 'CALL',
            phoneNumber: formattedPhone,
            message,
            urgent,
            status: 'sent',
            sid: result.sid
        });

        res.json({
            success: true,
            message: 'Arama başarıyla yapıldı',
            data: result
        });

    } catch (error) {
        console.error('Arama hatası:', error);
        
        // Hatalı bildirimi kaydet
        await saveNotification({
            type: 'CALL',
            phoneNumber: req.body.phoneNumber,
            message: req.body.message,
            urgent: req.body.urgent || false,
            status: 'failed',
            error: error.message
        });

        res.status(500).json({
            error: 'Arama yapılamadı',
            details: error.message
        });
    }
});

// Bildirim geçmişi
router.get('/history', async (req, res) => {
    try {
        const { limit = 50, offset = 0, type, status } = req.query;
        const history = await getNotificationHistory({ 
            limit: parseInt(limit), 
            offset: parseInt(offset), 
            type, 
            status 
        });
        
        res.json({
            success: true,
            data: history
        });
    } catch (error) {
        console.error('Geçmiş getirme hatası:', error);
        res.status(500).json({
            error: 'Geçmiş getirilemedi',
            details: error.message
        });
    }
});

// İstatistikler
router.get('/stats', async (req, res) => {
    try {
        const stats = await getNotificationStats();
        
        res.json({
            success: true,
            data: stats
        });
    } catch (error) {
        console.error('İstatistik getirme hatası:', error);
        res.status(500).json({
            error: 'İstatistikler getirilemedi',
            details: error.message
        });
    }
});

// Hızlı acil durum bildirimi
router.post('/emergency', async (req, res) => {
    try {
        const { message = 'ACİL DURUM BİLDİRİMİ!' } = req.body;
        const adminPhones = process.env.ADMIN_PHONE_NUMBERS?.split(',') || [];

        if (adminPhones.length === 0) {
            return res.status(400).json({
                error: 'Acil durum telefon numaraları tanımlanmamış'
            });
        }

        const emergencyMessage = `🚨 ${message} - ${new Date().toLocaleString('tr-TR')}`;
        
        // Hem SMS hem arama yap
        const smsResult = await twilioService.sendBulkSMS(adminPhones, emergencyMessage);
        
        // İlk numarayı ara
        let callResult = null;
        if (adminPhones.length > 0) {
            try {
                callResult = await twilioService.makeCall(adminPhones[0], emergencyMessage);
            } catch (callError) {
                console.error('Acil arama hatası:', callError);
            }
        }

        // Bildirimleri kaydet
        for (const phone of adminPhones) {
            await saveNotification({
                type: 'EMERGENCY_SMS',
                phoneNumber: phone,
                message: emergencyMessage,
                urgent: true,
                status: 'sent'
            });
        }

        if (callResult) {
            await saveNotification({
                type: 'EMERGENCY_CALL',
                phoneNumber: adminPhones[0],
                message: emergencyMessage,
                urgent: true,
                status: 'sent',
                sid: callResult.sid
            });
        }

        res.json({
            success: true,
            message: 'Acil durum bildirimi gönderildi',
            data: {
                sms: smsResult,
                call: callResult
            }
        });

    } catch (error) {
        console.error('Acil durum bildirimi hatası:', error);
        res.status(500).json({
            error: 'Acil durum bildirimi gönderilemedi',
            details: error.message
        });
    }
});

module.exports = router;
const twilio = require('twilio');

class TwilioService {
    constructor() {
        this.accountSid = process.env.TWILIO_ACCOUNT_SID;
        this.authToken = process.env.TWILIO_AUTH_TOKEN;
        this.phoneNumber = process.env.TWILIO_PHONE_NUMBER;
        
        if (!this.accountSid || !this.authToken || !this.phoneNumber) {
            console.warn('⚠️  Twilio yapılandırması eksik. SMS gönderimi devre dışı.');
            this.client = null;
        } else {
            this.client = twilio(this.accountSid, this.authToken);
        }
    }

    async sendSMS(to, message) {
        if (!this.client) {
            throw new Error('Twilio yapılandırılmamış');
        }

        try {
            const result = await this.client.messages.create({
                body: message,
                from: this.phoneNumber,
                to: to
            });

            console.log(`📱 SMS gönderildi: ${to} - SID: ${result.sid}`);
            return {
                success: true,
                sid: result.sid,
                to: to,
                message: message
            };
        } catch (error) {
            console.error('❌ SMS gönderme hatası:', error);
            throw new Error(`SMS gönderilemedi: ${error.message}`);
        }
    }

    async sendBulkSMS(phoneNumbers, message) {
        if (!Array.isArray(phoneNumbers)) {
            throw new Error('Telefon numaraları dizi olmalı');
        }

        const results = [];
        const errors = [];

        for (const phoneNumber of phoneNumbers) {
            try {
                const result = await this.sendSMS(phoneNumber, message);
                results.push(result);
            } catch (error) {
                errors.push({
                    phoneNumber,
                    error: error.message
                });
            }
        }

        return {
            success: results.length > 0,
            sent: results.length,
            failed: errors.length,
            results,
            errors
        };
    }

    async makeCall(to, message) {
        if (!this.client) {
            throw new Error('Twilio yapılandırılmamış');
        }

        try {
            const call = await this.client.calls.create({
                twiml: `<Response><Say language="tr-TR">${message}</Say></Response>`,
                to: to,
                from: this.phoneNumber
            });

            console.log(`📞 Arama yapıldı: ${to} - SID: ${call.sid}`);
            return {
                success: true,
                sid: call.sid,
                to: to,
                message: message
            };
        } catch (error) {
            console.error('❌ Arama hatası:', error);
            throw new Error(`Arama yapılamadı: ${error.message}`);
        }
    }
}

module.exports = new TwilioService();
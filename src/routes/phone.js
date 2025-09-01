const express = require('express');
const router = express.Router();

// Twilio webhook'ları için endpoint'ler
router.post('/webhook/sms-status', (req, res) => {
    const { MessageSid, MessageStatus, To, ErrorCode, ErrorMessage } = req.body;
    
    console.log(`📱 SMS Durum Güncellemesi:`, {
        sid: MessageSid,
        status: MessageStatus,
        to: To,
        error: ErrorCode ? { code: ErrorCode, message: ErrorMessage } : null
    });
    
    // Burada veritabanında durumu güncelleyebilirsin
    
    res.status(200).send('OK');
});

router.post('/webhook/call-status', (req, res) => {
    const { CallSid, CallStatus, To, Duration, ErrorCode, ErrorMessage } = req.body;
    
    console.log(`📞 Arama Durum Güncellemesi:`, {
        sid: CallSid,
        status: CallStatus,
        to: To,
        duration: Duration,
        error: ErrorCode ? { code: ErrorCode, message: ErrorMessage } : null
    });
    
    // Burada veritabanında durumu güncelleyebilirsin
    
    res.status(200).send('OK');
});

// Gelen SMS'leri işle
router.post('/webhook/incoming-sms', (req, res) => {
    const { From, Body, MessageSid } = req.body;
    
    console.log(`📨 Gelen SMS:`, {
        from: From,
        message: Body,
        sid: MessageSid
    });
    
    // Otomatik yanıt gönderebilirsin
    const response = `
        <Response>
            <Message>
                Mesajınız alındı. Bildirim sistemi aktif.
            </Message>
        </Response>
    `;
    
    res.type('text/xml');
    res.send(response);
});

// Gelen aramaları işle
router.post('/webhook/incoming-call', (req, res) => {
    const { From, CallSid } = req.body;
    
    console.log(`📞 Gelen Arama:`, {
        from: From,
        sid: CallSid
    });
    
    // Otomatik yanıt
    const response = `
        <Response>
            <Say language="tr-TR">
                Merhaba. Bildirim telefon sistemine hoş geldiniz. 
                Mesajınız kaydedilmiştir.
            </Say>
            <Record timeout="30" transcribe="true" />
        </Response>
    `;
    
    res.type('text/xml');
    res.send(response);
});

module.exports = router;
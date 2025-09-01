const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
require('dotenv').config();

const notificationRoutes = require('./routes/notification');
const phoneRoutes = require('./routes/phone');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// Routes
app.use('/api/notification', notificationRoutes);
app.use('/api/phone', phoneRoutes);

// Health check endpoint
app.get('/health', (req, res) => {
    res.status(200).json({ 
        status: 'OK', 
        message: 'Bildirim Telefon sistemi çalışıyor',
        timestamp: new Date().toISOString()
    });
});

// Root endpoint
app.get('/', (req, res) => {
    res.json({
        message: 'Bildirim Telefon API',
        version: '1.0.0',
        endpoints: {
            health: '/health',
            notification: '/api/notification',
            phone: '/api/phone'
        }
    });
});

// Error handling middleware
app.use((err, req, res, next) => {
    console.error(err.stack);
    res.status(500).json({ 
        error: 'Bir hata oluştu!',
        message: process.env.NODE_ENV === 'development' ? err.message : 'Internal Server Error'
    });
});

// 404 handler
app.use('*', (req, res) => {
    res.status(404).json({ error: 'Endpoint bulunamadı' });
});

app.listen(PORT, () => {
    console.log(`🚀 Bildirim Telefon sunucusu ${PORT} portunda çalışıyor`);
    console.log(`📱 API URL: http://localhost:${PORT}`);
    console.log(`🏥 Health Check: http://localhost:${PORT}/health`);
});
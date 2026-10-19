const express = require('express');
const axios = require('axios');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');

// e.g. postgres://fleet:fleet@postgres:5432/fleet in Docker; unset = the database endpoints answer 503.
const pool = process.env.DATABASE_URL
    ? new Pool({ connectionString: process.env.DATABASE_URL, connectionTimeoutMillis: 3000 })
    : null;

const app = express();
app.use(cors());

// Jason mind-inspector base URL; override with AGENT_BASE_URL (e.g. http://mas:3272 in Docker).
const AGENT_BASE_URL = process.env.AGENT_BASE_URL || 'http://192.168.178.34:3272';
const AGENT_URLS = [
    'vehicle_agent1',
    'vehicle_agent2',
    'vehicle_agent3',
    'service_center_agent',
    'fleet_coordinator_agent'
].map((agent) => `${AGENT_BASE_URL}/agent-mind/${agent}/latest`);

app.get('/api/fleet', async (req, res) => {
    try {
        const responses = await Promise.all(AGENT_URLS.map(url => axios.get(url, { timeout: 3000 }).catch(() => ({ data: '' }))));
        const parsedData = responses.map((r, i) => {
            const html = r.data || '';
            const extract = (pattern) => html.match(pattern)?.[1] || 'N/A';
            
            if (i === 4) { // Fleet Coordinator Logic
                const oilAlerts = (html.match(/anomaly_detected\(vehicle_agent\d+,oil_pressure/g) || []).length;
                const brakeAlerts = (html.match(/anomaly_detected\(vehicle_agent\d+,brake_wear/g) || []).length;
                const fleetMatch = html.match(/fleet_size\((\d+)/);
                const fleetSize = fleetMatch ? fleetMatch[1] : '8';
                
                return {
                    agent: "Fleet Coordinator",
                    oilAnomalies: oilAlerts,
                    brakeAnomalies: brakeAlerts,
                    totalRegistered: (html.match(/vehicle_registered/g) || []).length,
                    fleetSize: fleetSize
                };
            }
            
            if (i === 3) { // Service Center Logic
                const clean = (val) => val ? val.replace(/<[^>]*>/g, '').trim() : '0';

                return {
                    agent: "Service Center",
                    records: clean(html.match(/record_counter\((.*?)\)/)?.[1]),
                    oil: clean(html.match(/parts_inventory\(oil_filter,(.*?)\)/)?.[1]),
                    brake: clean(html.match(/parts_inventory\(brake_pad,(.*?)\)/)?.[1]),
                    overloaded: extract(/is_overloaded\((.*?)\)/)
                };
            }
            
            // Vehicle Logic
            return {
                agent: `Vehicle ${i + 1}`,
                vin: extract(/vin\(<span style="color: rgb\(0, 0, 250\)">"(.*?)"<\/span>\)/),
                mileage: extract(/mileage\((.*?)\)/).replace(/<i>|<\/i>/g, ''),
                temp: extract(/current_temperature\((.*?)\)/).replace(/<i>|<\/i>/g, ''),
                status: extract(/engine_status\((.*?)\)/),
                urgency: extract(/urgency_level\((.*?)\)/).toUpperCase()
            };
        });
        res.json(parsedData);
    } catch (e) { res.status(500).send(e.message); }
});
app.get('/api/ml-prediction', (req, res) => {
    try {
        // Use absolute path inside the container where the volume is mounted
        const logPath = process.env.PREDICTIONS_LOG || 'final_predictions_log.txt';
        
        if (!fs.existsSync(logPath)) {
            return res.status(404).json({ error: `Prediction log file not found at: ${logPath}` });
        }

        const fileContent = fs.readFileSync(logPath, 'utf8');
        const blocks = fileContent.split('========================================').map(b => b.trim()).filter(Boolean);
        
        if (blocks.length === 0) {
            return res.json({ status: "No prediction data available" });
        }

        const latestBlock = blocks[blocks.length - 1];
        const getField = (regex) => latestBlock.match(regex)?.[1]?.trim() || 'N/A';

        const predictionData = {
            instanceNumber: getField(/Instance Number:\s*(.*)/),
            instanceData: getField(/Instance Data:\s*(.*)/),
            rawPrediction: getField(/Weka Raw Prediction:\s*(.*)/),
            confidence: getField(/Model Confidence:\s*(.*)/),
            decision: getField(/Final Adjusted Decision:\s*(.*)/)
        };

        res.json(predictionData);
    } catch (e) {
        res.status(500).send(e.message);
    }
});
app.get('/api/service-records', async (req, res) => {
    if (!pool) {
        return res.status(503).json({ error: 'DATABASE_URL is not set' });
    }
    try {
        const { rows } = await pool.query(
            `SELECT id, vehicle_id, part, technician, cost, endorser_id, approved, decided_at
               FROM service_record
              ORDER BY id DESC
              LIMIT 500`
        );
        res.json(rows);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});
app.get('/api/ml-predictions', async (req, res) => {
    if (!pool) {
        return res.status(503).json({ error: 'DATABASE_URL is not set' });
    }
    try {
        const { rows } = await pool.query(
            `SELECT id, instance_number, instance_data, raw_prediction,
                    confidence::float AS confidence, decision, predicted_at
               FROM maintenance_prediction
              ORDER BY id DESC
              LIMIT 500`
        );
        res.json(rows);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});
app.listen(3000, () => console.log('Proxy active on port 3000'));
const express = require("express");
const crypto = require("crypto");
const db = require("../database/database");

const router = express.Router();

router.post("/uplink", (req, res) => {
    try {
        const {
            id,
            senderId,
            senderName,
            title,
            content,
            priority,
            location,
            timestamp
        } = req.body;

        if (!id || !title || !content || !priority) {
            return res.status(400).json({
                success: false,
                message: "Missing required incident fields"
            });
        }

        const allowedPriorities = ["CRITICAL", "HIGH", "NORMAL", "INFO"];

        if (!allowedPriorities.includes(priority)) {
            return res.status(400).json({
                success: false,
                message: "Invalid incident priority"
            });
        }

        const existing = db.prepare(
            "SELECT id FROM incidents WHERE id = ?"
        ).get(id);

        if (existing) {
            return res.status(200).json({
                success: true,
                duplicate: true,
                incidentId: id,
                message: "Incident already received"
            });
        }

        const latitude = location?.latitude ?? null;
        const longitude = location?.longitude ?? null;
        const accuracy = location?.accuracy ?? null;

        if (
            (latitude !== null &&
                (typeof latitude !== "number" || latitude < -90 || latitude > 90)) ||
            (longitude !== null &&
                (typeof longitude !== "number" || longitude < -180 || longitude > 180)) ||
            (accuracy !== null &&
                (typeof accuracy !== "number" || accuracy < 0))
        ) {
            return res.status(400).json({
                success: false,
                message: "Invalid location data"
            });
        }

        const receivedAt = new Date().toISOString();

        db.prepare(`
            INSERT INTO incidents (
                id, sender_id, sender_name, title, content,
                priority, latitude, longitude, accuracy,
                occurred_at, received_at, status, payload_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `).run(
            id,
            senderId || null,
            senderName || null,
            title,
            content,
            priority,
            latitude,
            longitude,
            accuracy,
            timestamp || null,
            receivedAt,
            "RECEIVED",
            JSON.stringify(req.body)
        );

        return res.status(201).json({
            success: true,
            duplicate: false,
            incidentId: id,
            receivedAt,
            message: "Incident stored successfully"
        });

    } catch (error) {
        console.error("Uplink error:", error.message);

        return res.status(500).json({
            success: false,
            message: "Unable to process incident"
        });
    }
});

router.get("/incidents", (req, res) => {
    try {
        const incidents = db.prepare(`
            SELECT id, sender_id, sender_name, title, content,
                   priority, latitude, longitude, accuracy,
                   occurred_at, received_at, status
            FROM incidents
            ORDER BY received_at DESC
        `).all();

        return res.json({
            success: true,
            count: incidents.length,
            incidents
        });

    } catch (error) {
        console.error("Incident retrieval error:", error.message);

        return res.status(500).json({
            success: false,
            message: "Unable to retrieve incidents"
        });
    }
});

module.exports = router;
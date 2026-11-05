
require("dotenv").config();

const express = require("express");
const cors = require("cors");
const incidentRoutes = require("./routes/incidentRoutes");

const app = express();

// Middleware
app.use(cors());
app.use(express.json({ limit: "100kb" }));

// Health check API
app.get("/api/health", (req, res) => {
    res.status(200).json({
        success: true,
        service: "ResQMesh Backend",
        status: "ONLINE",
        timestamp: new Date().toISOString()
    });
});

// Incident and emergency uplink APIs
app.use("/api", incidentRoutes);

// Handle unknown API endpoints
app.use("/api", (req, res) => {
    res.status(404).json({
        success: false,
        message: "API endpoint not found"
    });
});

// Global error handler
app.use((err, req, res, next) => {
    console.error("Server error:", err.message);

    res.status(500).json({
        success: false,
        message: "Internal server error"
    });
});

// Start server
const PORT = process.env.PORT || 3000;

app.listen(PORT, () => {
    console.log(`ResQMesh backend running on port ${PORT}`);
});

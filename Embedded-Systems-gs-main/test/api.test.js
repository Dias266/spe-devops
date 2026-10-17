// Starts the real control unit (no Arduino, no MQTT broker) and checks its HTTP API.
const { test, before, after } = require("node:test");
const assert = require("node:assert");
const { spawn } = require("node:child_process");
const path = require("node:path");

const PORT = 3902;
const BASE = `http://127.0.0.1:${PORT}`;
let server;

before(async () => {
  server = spawn(process.execPath, [path.join(__dirname, "..", "control-unit-iot.js")], {
    env: {
      ...process.env,
      HTTP_PORT: String(PORT),
      SERIAL_PORT: "/dev/no-arduino-in-ci",
      BROKER_URL: "mqtt://127.0.0.1:1",
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("server did not start")), 10000);
    server.stdout.on("data", (chunk) => {
      if (chunk.toString().includes("HTTP Server")) {
        clearTimeout(timer);
        resolve();
      }
    });
    server.on("exit", (code) => reject(new Error(`server exited early (${code})`)));
  });
});

after(async () => {
  const exited = new Promise((resolve) => server.on("exit", resolve));
  server.kill("SIGTERM");
  const code = await exited;
  assert.strictEqual(code, 0, "server should exit cleanly on SIGTERM");
});

test("latest telemetry is empty before any data arrives", async () => {
  const res = await fetch(`${BASE}/api/telemetry/latest`);
  assert.strictEqual(res.status, 200);
  const body = await res.json();
  assert.strictEqual(body.success, true);
  assert.strictEqual(body.data, null);
  assert.strictEqual(body.state, "NORMAL");
});

test("telemetry history starts empty", async () => {
  const res = await fetch(`${BASE}/api/telemetry/history`);
  const body = await res.json();
  assert.deepStrictEqual(body.data, []);
});

test("statistics endpoint answers", async () => {
  const res = await fetch(`${BASE}/api/telemetry/statistics`);
  assert.strictEqual(res.status, 200);
  assert.strictEqual((await res.json()).success, true);
});

test("publishing without required fields is rejected with 400", async () => {
  const res = await fetch(`${BASE}/api/publish-telemetry`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ vin: "VIN1" }),
  });
  assert.strictEqual(res.status, 400);
});

test("publishing while MQTT is down returns 503", async () => {
  const res = await fetch(`${BASE}/api/publish-telemetry`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ vin: "VIN1", temp: "40", mileage: "1000", state: "NORMAL" }),
  });
  assert.strictEqual(res.status, 503);
});

test("test publish endpoint also reports MQTT down", async () => {
  const res = await fetch(`${BASE}/api/publish-test`);
  assert.strictEqual(res.status, 503);
});

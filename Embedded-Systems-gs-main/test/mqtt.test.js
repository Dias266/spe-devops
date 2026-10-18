// MQTT integration test for the IoT control unit.
//
// Starts an in-process MQTT broker (aedes) and the real control unit pointed at it,
// then checks the two directions the rest of the system depends on:
//
//   Java MAS  --HTTP POST /api/publish-telemetry-->  control unit  --MQTT vehicle/telemetry-->  broker
//   ESP32     --MQTT vehicle/telemetry-->  control unit  --stores, derives state-->  MQTT vehicle/state + HTTP API
//
// No Docker, Mosquitto, Arduino or ESP32 is needed, so it runs the same locally and in CircleCI.
const { test, before, after } = require("node:test");
const assert = require("node:assert");
const net = require("node:net");
const { spawn } = require("node:child_process");
const path = require("node:path");
const { once } = require("node:events");
const mqtt = require("mqtt");
const { Aedes } = require("aedes");

const HTTP_PORT = 3903;
const BASE = `http://127.0.0.1:${HTTP_PORT}`;

let broker;
let brokerServer;
let brokerUrl;
let unit;
let probe; // test MQTT client that plays the ESP32 and listens to everything

// Collects every message the probe sees, so a test can wait for a specific one.
const received = [];
function waitForMessage(topic, predicate = () => true, timeoutMs = 5000) {
  const match = (m) => m.topic === topic && predicate(m.payload);
  const already = received.find(match);
  if (already) return Promise.resolve(already.payload);
  return new Promise((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new Error(`no message on ${topic} within ${timeoutMs} ms`)),
      timeoutMs,
    );
    const onMessage = (t, payload) => {
      if (match({ topic: t, payload: payload.toString() })) {
        clearTimeout(timer);
        probe.off("message", onMessage);
        resolve(payload.toString());
      }
    };
    probe.on("message", onMessage);
  });
}

// Resolves once a client has subscribed to the given topic on the broker.
function waitForSubscription(topic, timeoutMs = 10000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new Error(`nobody subscribed to ${topic}`)),
      timeoutMs,
    );
    const onSubscribe = (subscriptions) => {
      if (subscriptions.some((s) => s.topic === topic)) {
        clearTimeout(timer);
        broker.off("subscribe", onSubscribe);
        resolve();
      }
    };
    broker.on("subscribe", onSubscribe);
  });
}

async function latest() {
  const res = await fetch(`${BASE}/api/telemetry/latest`);
  return res.json();
}

// The control unit handles MQTT messages asynchronously; poll the API until it catches up.
async function eventually(check, timeoutMs = 5000) {
  const deadline = Date.now() + timeoutMs;
  let lastError;
  while (Date.now() < deadline) {
    try {
      return await check();
    } catch (err) {
      lastError = err;
      await new Promise((r) => setTimeout(r, 50));
    }
  }
  throw lastError;
}

before(async () => {
  broker = await Aedes.createBroker();
  brokerServer = net.createServer(broker.handle);
  brokerServer.listen(0, "127.0.0.1");
  await once(brokerServer, "listening");
  brokerUrl = `mqtt://127.0.0.1:${brokerServer.address().port}`;

  const subscribed = waitForSubscription("vehicle/telemetry");
  unit = spawn(process.execPath, [path.join(__dirname, "..", "control-unit-iot.js")], {
    env: {
      ...process.env,
      HTTP_PORT: String(HTTP_PORT),
      SERIAL_PORT: "/dev/no-arduino-in-ci",
      BROKER_URL: brokerUrl,
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
  unit.on("exit", (code) => {
    if (code !== 0 && code !== null) console.error(`control unit exited with ${code}`);
  });
  await subscribed;

  probe = mqtt.connect(brokerUrl);
  await once(probe, "connect");
  probe.on("message", (topic, payload) => received.push({ topic, payload: payload.toString() }));
  await probe.subscribeAsync("vehicle/#");
});

after(async () => {
  if (probe) await probe.endAsync();
  if (unit && unit.exitCode === null) {
    const exited = once(unit, "exit");
    unit.kill("SIGTERM");
    const [code] = await exited;
    assert.strictEqual(code, 0, "control unit should exit cleanly on SIGTERM");
  }
  await new Promise((resolve) => broker.close(resolve));
  await new Promise((resolve) => brokerServer.close(resolve));
});

test("control unit connects to the broker and subscribes to telemetry and state", () => {
  // Reaching this point means before() saw the subscription on vehicle/telemetry.
  assert.ok(broker.connectedClients >= 2, "control unit and probe should both be connected");
});

test("Java MAS payload sent over HTTP is published on vehicle/telemetry", async () => {
  // Exactly what fleet_coordinator_agent.asl builds in +service_completed_Mqtt and
  // FleetMASEnvironment forwards to POST /api/publish-telemetry.
  const javaPayload = { vin: "vehicle_agent1", temp: "22", mileage: "2000", state: "NORMAL" };

  const res = await fetch(`${BASE}/api/publish-telemetry`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(javaPayload),
  });
  assert.strictEqual(res.status, 200);
  const body = await res.json();
  assert.strictEqual(body.success, true);

  const packet = await waitForMessage("vehicle/telemetry", (p) => p.includes("VIN:vehicle_agent1"));
  assert.match(packet, /^VIN:vehicle_agent1\|TEMP:22\|MILEAGE:2000\|STATE:NORMAL\|TIMESTAMP:\d+\|SIG:\w+$/);
  assert.strictEqual(packet, body.packet, "HTTP response should echo the packet that went out");
});

test("published telemetry comes back through the subscription and is stored", async () => {
  const body = await eventually(async () => {
    const b = await latest();
    assert.strictEqual(b.data && b.data.vin, "vehicle_agent1");
    return b;
  });
  assert.strictEqual(body.data.temp, "22");
  assert.strictEqual(body.data.mileage, "2000");
  assert.ok(body.data.receivedAt, "stored entry should be time-stamped");
  assert.strictEqual(body.state, "NORMAL");
});

test("telemetry from a vehicle (ESP32) over MQTT raises WARNING and publishes vehicle/state", async () => {
  const packet = `VIN:1HGCM82633A123456|TEMP:35|MILEAGE:4100|STATE:NORMAL|TIMESTAMP:${Date.now()}|SIG:abcd1234`;
  await probe.publishAsync("vehicle/telemetry", packet);

  const state = await waitForMessage("vehicle/state", (p) => p === "WARNING");
  assert.strictEqual(state, "WARNING");

  const body = await eventually(async () => {
    const b = await latest();
    assert.strictEqual(b.data.vin, "1HGCM82633A123456");
    return b;
  });
  assert.strictEqual(body.state, "WARNING");
});

test("critical temperature publishes CRITICAL on vehicle/state", async () => {
  const packet = `VIN:1HGCM82633A123456|TEMP:45|MILEAGE:4200|STATE:NORMAL|TIMESTAMP:${Date.now()}|SIG:abcd1234`;
  await probe.publishAsync("vehicle/telemetry", packet);

  await waitForMessage("vehicle/state", (p) => p === "CRITICAL");
  const body = await eventually(async () => {
    const b = await latest();
    assert.strictEqual(b.state, "CRITICAL");
    return b;
  });
  assert.strictEqual(body.data.temp, "45");
});

test("history and statistics reflect every telemetry message received over MQTT", async () => {
  const history = await (await fetch(`${BASE}/api/telemetry/history`)).json();
  assert.deepStrictEqual(
    history.data.map((t) => t.temp),
    ["22", "35", "45"],
  );

  const stats = await (await fetch(`${BASE}/api/telemetry/statistics`)).json();
  assert.strictEqual(stats.stats.count, 3);
  assert.strictEqual(stats.stats.min, 22);
  assert.strictEqual(stats.stats.max, 45);
  assert.strictEqual(stats.stats.avg, (22 + 35 + 45) / 3);
});

test("test publish endpoint works once the broker is up", async () => {
  const res = await fetch(`${BASE}/api/publish-test`);
  assert.strictEqual(res.status, 200);
  await waitForMessage("vehicle/telemetry", (p) => p.startsWith("VIN:TEST-VIN-123|"));
});

# IoT control unit: MQTT telemetry bridge + HTTP API on port 3002.
FROM node:20-bookworm-slim
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY control-unit-iot.js ./
ENV NODE_ENV=production \
    BROKER_URL=mqtt://mosquitto:1883 \
    SERIAL_PORT=/dev/ttyUSB0
EXPOSE 3002
USER node
CMD ["node", "control-unit-iot.js"]

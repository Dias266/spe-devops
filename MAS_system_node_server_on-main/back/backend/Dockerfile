# Fleet dashboard backend (Express) on port 3000.
FROM node:20-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY server.js final_predictions_log.txt ./
ENV NODE_ENV=production
EXPOSE 3000
USER node
CMD ["node", "server.js"]

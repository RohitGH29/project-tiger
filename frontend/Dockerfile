FROM node:20-alpine AS build

WORKDIR /app

COPY . .
RUN if [ -f package-lock.json ]; then npm ci; else npm install --no-audit --no-fund; fi \
    && npm run build

FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

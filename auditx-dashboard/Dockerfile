# Build stage
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install --silent
COPY public/ public/
COPY src/ src/
ARG REACT_APP_REPORT_URL=http://localhost:8086
ARG REACT_APP_INGESTION_URL=http://localhost:8081
ARG REACT_APP_KIBANA_URL=http://localhost:5601
ENV REACT_APP_REPORT_URL=$REACT_APP_REPORT_URL
ENV REACT_APP_INGESTION_URL=$REACT_APP_INGESTION_URL
ENV REACT_APP_KIBANA_URL=$REACT_APP_KIBANA_URL
RUN npm run build

# Runtime stage — nginx serves the built React app
FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]

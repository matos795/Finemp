FROM node:24-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts

COPY . .
RUN npm run build

FROM nginx/nginx-unprivileged:stable-alpine AS runtime

ENV PORT=8080 \
    NGINX_ENVSUBST_FILTER='^(PORT)

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --chmod=755 docker-entrypoint.d/10-finemp-env.sh /docker-entrypoint.d/10-finemp-env.sh

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]


COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --chmod=755 docker-entrypoint.d/10-finemp-env.sh /docker-entrypoint.d/10-finemp-env.sh

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]

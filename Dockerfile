FROM maven:3.9-eclipse-temurin-8

RUN apt-get update && apt-get install -y ant unzip ca-certificates curl gnupg \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_20.x nodistro main" > /etc/apt/sources.list.d/nodesource.list \
    && apt-get update && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# ProGuard (preverify / release shrink)
ENV PROGUARD_VERSION=7.4.2
RUN curl -fsSL "https://github.com/Guardsquare/proguard/releases/download/v${PROGUARD_VERSION}/proguard-${PROGUARD_VERSION}.zip" -o /tmp/proguard.zip \
    && unzip /tmp/proguard.zip -d /tmp/ \
    && mkdir -p /opt/proguard \
    && mv /tmp/proguard-${PROGUARD_VERSION}/lib/proguard.jar /opt/proguard/ \
    && rm -rf /tmp/proguard*

# Install entrypoint under /app/docker so `docker compose` bind-mount (.:/app) picks up host edits
# without rebuilding the image. Fallback copy under /usr/local/bin for non-mounted runs.
COPY docker/athena2me-entrypoint.sh /app/docker/athena2me-entrypoint.sh
COPY docker/athena2me-entrypoint.sh /usr/local/bin/athena2me-entrypoint.sh
RUN sed -i 's/\r$//' /app/docker/athena2me-entrypoint.sh /usr/local/bin/athena2me-entrypoint.sh \
    && chmod +x /app/docker/athena2me-entrypoint.sh /usr/local/bin/athena2me-entrypoint.sh

WORKDIR /app

# Prefer mounted repo script; invoke via sh (CRLF-safe)
ENTRYPOINT ["/bin/sh", "/app/docker/athena2me-entrypoint.sh"]
CMD ["all"]

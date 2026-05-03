FROM maven:3.9-eclipse-temurin-8

RUN apt-get update && apt-get install -y ant unzip && rm -rf /var/lib/apt/lists/*

# Set up ProGuard
ENV PROGUARD_VERSION=7.4.2
RUN curl -fsSL "https://github.com/Guardsquare/proguard/releases/download/v${PROGUARD_VERSION}/proguard-${PROGUARD_VERSION}.zip" -o /tmp/proguard.zip \
    && unzip /tmp/proguard.zip -d /tmp/ \
    && mkdir -p /opt/proguard \
    && mv /tmp/proguard-${PROGUARD_VERSION}/lib/proguard.jar /opt/proguard/ \
    && rm -rf /tmp/proguard*

# Set working directory
WORKDIR /app

CMD ["sh", "-c", "mkdir -p tools && cp /opt/proguard/proguard.jar tools/proguard.jar && ant all"]
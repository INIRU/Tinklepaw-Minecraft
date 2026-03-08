# ─────────────────────────────────────────────
# Stage 1: Build Kotlin plugin
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build
COPY plugin/ .
RUN ./gradlew shadowJar --no-daemon

# ─────────────────────────────────────────────
# Stage 2: Minecraft server
# ─────────────────────────────────────────────
FROM itzg/minecraft-server:latest

# Copy built plugin to staging (not /data — that's a volume and gets shadowed)
COPY --from=builder /build/build/libs/nyaru-plugin.jar /local-plugin/nyaru-plugin.jar

# Wrapper: copy plugin into /data/plugins before itzg starts
RUN printf '#!/bin/bash\nmkdir -p /data/plugins\ncp -f /local-plugin/nyaru-plugin.jar /data/plugins/nyaru-plugin.jar\nexec /start "$@"\n' \
    > /mc-start.sh && chmod +x /mc-start.sh

ENTRYPOINT ["/mc-start.sh"]

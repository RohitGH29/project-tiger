FROM jenkins/jenkins:lts-jdk17

USER root

# Install required packages including docker CLI
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        docker-cli \
        python3 \
        python3-pip \
        python3-venv \
        nodejs \
        npm \
    && rm -rf /var/lib/apt/lists/* \
    && python3 --version \
    && pip3 --version \
    && node --version \
    && npm --version \
    && docker --version

# Add docker group inside container and add jenkins user
ARG DOCKER_GID=0
RUN groupadd -for -g ${DOCKER_GID} docker \
    && usermod -aG docker jenkins

# Switch back to jenkins user
USER jenkins
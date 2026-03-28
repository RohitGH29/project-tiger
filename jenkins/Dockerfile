# Jenkins controller with Docker client, Python/pip, and Node/npm (Debian packages).
# Build:  docker build -t project-jenkins:local -f jenkins/Dockerfile jenkins
# Run:    see compose.yaml in this folder (socket + optional group_add for permissions).

FROM jenkins/jenkins:lts-jdk17

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        docker.io \
        python3 \
        python3-pip \
        python3-venv \
        nodejs \
        npm \
    && rm -rf /var/lib/apt/lists/* \
    && python3 --version \
    && pip3 --version \
    && node --version \
    && npm --version

USER jenkins

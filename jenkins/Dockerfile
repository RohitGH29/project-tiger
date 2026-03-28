# Jenkins controller with Docker *client* only (talks to host daemon via mounted socket).
# Build:  docker build -t project-jenkins:local -f jenkins/Dockerfile jenkins
# Run:    see compose.yaml in this folder (socket + optional group_add for permissions).

FROM jenkins/jenkins:lts-jdk17

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends docker.io \
    && rm -rf /var/lib/apt/lists/*

USER jenkins

# Jenkins talks to the HOST Docker engine only:
#   - Install docker CLI in this image (below).
#   - At run time, mount the host socket: /var/run/docker.sock (see compose.yaml).
#   - Pipelines run `docker build` / `docker run` against that daemon — not a separate engine inside Jenkins.
#
# Build:  docker compose -f jenkins/compose.yaml build
# Run:   docker compose -f jenkins/compose.yaml up -d

FROM jenkins/jenkins:lts-jdk17

USER root
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

USER jenkins

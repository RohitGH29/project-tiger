Same Docker Socket Permission Issue — New Machine
This is the same problem from the start of our conversation. The DOCKER_GID on this x86_64 machine is different from what's baked into the image.
Step 1 — Check the socket GID on x86_64 machine
bashstat -c '%g' /var/run/docker.sock
Step 2 — Rebuild the image with correct GID
bashexport DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)
echo "DOCKER_GID=$DOCKER_GID"

cd ~/docker
docker compose build --no-cache --build-arg DOCKER_GID=$DOCKER_GID
Step 3 — Restart Jenkins
bashdocker compose down
docker compose up -d

# Verify jenkins user has docker group
docker exec pipeline-jenkins-1 id jenkins
Step 4 — Also update your docker-compose.yml to pass the GID at runtime too:
yamlservices:
  jenkins:
    build:
      context: .
      dockerfile: Dockerfile
      args:
        DOCKER_GID: "${DOCKER_GID:-999}"
    group_add:
      - "${DOCKER_GID:-999}"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - jenkins_home:/var/jenkins_home
Then always start with:
bashexport DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)
docker compose up -d

This is why the multi-arch build + volume restore approach is better long term — the DOCKER_GID needs to match the host machine the container runs on, not where it was built.

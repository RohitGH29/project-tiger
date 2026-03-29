# Jenkins + Docker Troubleshooting — Command Reference

## 1. Docker Socket Permission Fix (DOCKER_GID Mismatch)

```bash
# Get the socket GID on host
stat -c '%g' /var/run/docker.sock

# Export and rebuild Jenkins image with correct GID
export DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)
docker compose -f jenkins/compose.yaml build --no-cache
docker compose -f jenkins/compose.yaml up -d

# Verify jenkins user has docker group
docker exec pipeline-jenkins-1 id jenkins
docker exec pipeline-jenkins-1 docker info
```

---

## 2. Install Docker + Docker Compose + Buildx on Amazon Linux 2023 (x86_64)

```bash
# Install Docker
sudo dnf install -y docker
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker $USER

# Install Docker Compose V2 plugin
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Install Docker Buildx plugin
BUILDX_VERSION=$(curl -s https://api.github.com/repos/docker/buildx/releases/latest \
  | grep '"tag_name"' | cut -d'"' -f4)
sudo curl -SL "https://github.com/docker/buildx/releases/download/${BUILDX_VERSION}/buildx-${BUILDX_VERSION}.linux-amd64" \
  -o /usr/local/lib/docker/cli-plugins/docker-buildx
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx

# Verify
docker --version
docker compose version
docker buildx version

# Apply group change without re-login
newgrp docker
```

---

## 3. Jenkins Data Backup (from Running Container)

```bash
# Backup directly from running container
docker exec pipeline-jenkins-1 tar czf - -C /var/jenkins_home . \
  > jenkins-backup.tar.gz

# Verify size (should be 100MB+)
du -sh jenkins-backup.tar.gz

# SCP to another server
scp -i your-key.pem jenkins-backup.tar.gz ec2-user@<target-ip>:/home/ec2-user/
```

---

## 4. Restore Jenkins Data on New Server

```bash
# Check exact volume name
docker volume ls | grep jenkins

# Stop Jenkins first
docker compose down

# Remove empty volume and recreate
docker volume rm docker_jenkins_home
docker volume create docker_jenkins_home

# Restore backup into volume
docker run --rm \
  -v docker_jenkins_home:/data \
  -v /home/ec2-user:/backup \
  alpine sh -c "tar xzf /backup/jenkins-backup.tar.gz -C /data 2>/dev/null && echo 'Restore OK'"

# Verify data restored
docker run --rm -v docker_jenkins_home:/data alpine ls /data

# Start Jenkins
docker compose up -d
docker logs -f pipeline-jenkins-1
```

---

## 5. Multi-Arch Docker Image Build (ARM64 + AMD64)

```bash
# Create multi-arch builder
docker buildx create --name multiarch-builder --use
docker buildx inspect --bootstrap

# Build and push for both platforms
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t rohit05528181/my-jenkins:v2 \
  --push \
  -f Dockerfile \
  .

# Verify manifest has both architectures
docker buildx imagetools inspect rohit05528181/my-jenkins:v2
```

---

## 6. Disk & Memory Diagnostics

```bash
# Check disk usage
df -h
lsblk

# Check memory
free -h
top

# Check Docker disk usage
docker system df
docker system df -v

# Check disk I/O
iostat -x 1 5

# Top memory consuming containers
docker stats --no-stream

# Jenkins JVM heap
docker exec pipeline-jenkins-1 java -XX:+PrintFlagsFinal -version 2>&1 | grep HeapSize
```

---

## 7. Add Swap (Fix Memory Pressure)

```bash
# Create 2GB swap file
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# Make permanent
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Verify
free -h
```

---

## 8. Grow EBS Partition (No Reboot Needed)

```bash
# Check partition layout
lsblk

# Grow partition 1 to use full disk
sudo growpart /dev/nvme0n1 1

# Extend XFS filesystem
sudo xfs_growfs /

# Verify
df -h /
```

---

## 9. Clean Docker Disk Space

```bash
# Remove stopped containers, unused images, dangling volumes, build cache
docker system prune -a --volumes

# Check reclaimed space
df -h
```

---

## 10. Jenkins Plugin Cleanup (Fix UI Slowness)

```bash
# Count installed plugins
docker exec pipeline-jenkins-1 sh -c \
  "ls /var/jenkins_home/plugins/*.jpi 2>/dev/null | wc -l"

# List all plugins
docker exec pipeline-jenkins-1 sh -c \
  "ls /var/jenkins_home/plugins/*.jpi | sed 's|.*/||;s|\.jpi||' | sort"

# Find disabled plugins
docker exec pipeline-jenkins-1 sh -c \
  "find /var/jenkins_home/plugins -name '*.disabled'"

# Delete disabled plugins
docker exec pipeline-jenkins-1 sh -c \
  "find /var/jenkins_home/plugins -name '*.disabled' -delete && echo 'Done'"

# Remove unused plugins (adjust list as needed)
docker exec pipeline-jenkins-1 sh -c "
  cd /var/jenkins_home/plugins && rm -rf \
    ant ant.jpi \
    gradle gradle.jpi \
    ldap ldap.jpi \
    ssh-slaves ssh-slaves.jpi \
    matrix-project matrix-project.jpi \
    dark-theme dark-theme.jpi \
    theme-manager theme-manager.jpi \
    email-ext email-ext.jpi \
    mailer mailer.jpi \
    jakarta-mail-api jakarta-mail-api.jpi \
    jakarta-activation-api jakarta-activation-api.jpi \
    javax-activation-api javax-activation-api.jpi \
    jaxb jaxb.jpi \
  && echo 'Done'
"

# Restart Jenkins after plugin changes
docker compose restart jenkins

# Check logs for errors after restart
docker logs pipeline-jenkins-1 2>&1 | grep -i "error\|failed\|exception" | head -20
```

---

## 11. Jenkins CLI Setup

```bash
# Download CLI jar from Jenkins
docker exec pipeline-jenkins-1 \
  curl -o /tmp/jenkins-cli.jar http://localhost:8080/jnlpJars/jenkins-cli.jar

# List plugins via CLI (use API token from Manage Jenkins → Users → API Token)
docker exec pipeline-jenkins-1 \
  java -jar /tmp/jenkins-cli.jar \
  -s http://localhost:8080 \
  -auth admin:<api-token> \
  list-plugins | sort
```

---

## 12. Quick Health Check Commands

```bash
# Container status
docker ps -a

# Container resource usage
docker stats --no-stream

# Jenkins logs
docker logs -f pipeline-jenkins-1

# Inspect volume mounts
docker inspect pipeline-jenkins-1 | grep -A5 'Mounts'

# Check jenkins user groups
docker exec pipeline-jenkins-1 id jenkins

# Check disk inside container
docker exec pipeline-jenkins-1 sh -c "du -sh /var/jenkins_home/*" 2>/dev/null | sort -rh | head -10
```
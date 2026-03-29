# Step 1 — Create a multi-arch builder
docker buildx create --name multiarch-builder --use
docker buildx inspect --bootstrap

# Step 2 — Build & push for both platforms in one shot
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t rohit05528181/my-jenkins:v1 \
  --push \
  -f Dockerfile \
  .
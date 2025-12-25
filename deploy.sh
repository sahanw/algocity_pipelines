#!/bin/bash
set -e

# Configuration
SERVICE_NAME="algocity-pipelines"
VERSION="${1:-dev}"
DOCKER_REGISTRY="wickysd"
IMAGE_NAME="${DOCKER_REGISTRY}/${SERVICE_NAME}"
FULL_IMAGE="${IMAGE_NAME}:${VERSION}"

# Repository paths
CHARTS_REPO="${HOME}/Repository/algocity_charts"
VALUES_REPO="${HOME}/Repository/algocity_values"

# Helm configuration
CHART_PATH="${CHARTS_REPO}/charts/algocity-pipelines"
VALUES_FILE="${VALUES_REPO}/algocity_pipelines/values/application_values.yaml"

# K8s configuration
K8S_NAMESPACE="default"
K3S_CLUSTER="k3s-default"

echo "=================================================="
echo "Deploying ${SERVICE_NAME}:${VERSION} to K3s"
echo "=================================================="

# Step 1: Build Docker image
echo ""
echo "📦 Building Docker image..."
docker build -t "${FULL_IMAGE}" .
docker tag "${FULL_IMAGE}" "${IMAGE_NAME}:latest"
echo "✅ Image built: ${FULL_IMAGE}"

# Step 2: Push to Docker Hub
echo ""
echo "📤 Pushing to Docker Hub..."
docker push "${FULL_IMAGE}"
docker push "${IMAGE_NAME}:latest"
echo "✅ Image pushed to Docker Hub"

# Step 3: Build Helm dependencies
echo ""
echo "🔧 Building Helm dependencies..."
cd "${CHART_PATH}"
helm dependency build
echo "✅ Dependencies built"

# Step 4: Deploy with Helm
echo ""
echo "🚀 Deploying with Helm..."
helm upgrade --install "${SERVICE_NAME}" "${CHART_PATH}" \
    -f "${VALUES_FILE}" \
    --set image.repository="${IMAGE_NAME}" \
    --set image.tag="${VERSION}" \
    --set image.pullPolicy=Always \
    --set securityContext.readOnlyRootFilesystem=false \
    --namespace "${K8S_NAMESPACE}" \
    --create-namespace \
    --wait \
    --timeout 5m

echo "✅ Deployment successful!"


# Step 5: Check status
echo ""
echo "📊 Deployment Status:"
echo "---"
kubectl get deployment "${SERVICE_NAME}" -n "${K8S_NAMESPACE}"
echo ""
echo "📊 Pods:"
echo "---"
kubectl get pods -n "${K8S_NAMESPACE}" -l "app=${SERVICE_NAME}"

echo ""
echo "=================================================="
echo "✅ Deployment complete!"
echo "=================================================="
echo ""
echo "To check logs:"
echo "  kubectl logs -n ${K8S_NAMESPACE} -l app=${SERVICE_NAME} --tail=50 -f"
echo ""
echo "To check service:"
echo "  kubectl get svc -n ${K8S_NAMESPACE} ${SERVICE_NAME}"
echo ""

# Algocity Pipelines

A Jenkins-based CI/CD service for the AlgoCity platform.

## Overview

This repository contains:
- **Dockerfile**: Jenkins container with Docker, kubectl, Helm, and k3d pre-installed
- **Jenkinsfile**: Pipeline definition for building and deploying services to K3s
- **deploy.sh**: Standalone deployment script for local testing

## Prerequisites

- **Docker**: For building images
- **K3s/K3d**: Local Kubernetes cluster
- **Helm 3**: For deploying charts
- **kubectl**: For interacting with K3s

### Local K3s Setup

If you don't have k3s running locally:

```bash
# Install k3d
curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash

# Create a local k3s cluster with registry
k3d cluster create k3s-default --registry-create localhost:5000
```

## Repository Structure

This service uses three repositories:

1. **algocity_pipelines** (this repo): The Jenkins service itself
2. **algocity_charts**: Helm charts for deploying services
3. **algocity_values**: Service-specific values files

All three should be cloned to `~/Repository/`:

```bash
cd ~/Repository
git clone git@github.com:sahanw/algocity_pipelines.git
git clone git@github.com:sahanw/algocity_charts.git
git clone git@github.com:sahanw/algocity_values.git
```

## Local Deployment

To deploy this service locally to k3s:

```bash
cd ~/Repository/algocity_pipelines
./deploy.sh
```

This will:
1. Build the Docker image
2. Import it to your k3s cluster
3. Deploy using Helm with values from `algocity_values`
4. Show deployment status

### Custom Version

```bash
./deploy.sh v1.2.3
```

## Jenkins Pipeline

The Jenkinsfile defines a three-stage pipeline:

1. **Build Docker Image**: Builds and tags the service image
2. **Import to K3s**: Imports the image to the k3s cluster
3. **Deploy with Helm**: Deploys using Helm with proper values

### Pipeline Configuration

The pipeline uses:
- **Chart**: `~/Repository/algocity_charts/examples/sample-service`
- **Values**: `~/Repository/algocity_values/algocity_pipelines/values/application_values.yaml`
- **Namespace**: `default` (configurable)
- **Registry**: `localhost:5000` (local registry)

## Helm Deployment Details

The deployment uses the `sample-service` chart from `algocity_charts`, which includes:
- Deployment with configurable replicas
- Service for internal communication
- Ingress (if enabled in values)
- HPA (Horizontal Pod Autoscaler)
- PDB (Pod Disruption Budget)

Values are layered:
1. Base values from `application_values.yaml`
2. Environment-specific overrides (e.g., `prod/environment_values.yaml`)
3. Datacenter-specific overrides (e.g., `prod/dc1.yaml`)

For local k3s deployment, only base values are used.

## Checking Deployment

```bash
# Check deployment
kubectl get deployment algocity-pipelines -n default

# Check pods
kubectl get pods -n default -l app=algocity-pipelines

# View logs
kubectl logs -n default -l app=algocity-pipelines --tail=50 -f

# Check service
kubectl get svc -n default algocity-pipelines
```

## Troubleshooting

### Image Pull Errors

If you see `ImagePullBackOff`:
- Ensure the image was imported to k3s: `docker exec k3s-default-server-0 crictl images | grep algocity`
- Try re-running `./deploy.sh`

### Health Check Failures

The service expects these endpoints:
- `/healthz`: Liveness probe
- `/ready`: Readiness probe

If your application doesn't have these endpoints, update the values file to change the probe paths or disable probes.

### Helm Dependency Issues

If Helm complains about missing dependencies:
```bash
cd ~/Repository/algocity_charts/examples/sample-service
helm dependency build
```

## Development

To modify the Jenkins configuration:
1. Edit the `Dockerfile` to add/remove tools
2. Rebuild the Jenkins image
3. Redeploy Jenkins to your cluster

To modify the deployment pipeline:
1. Edit the `Jenkinsfile`
2. Commit and push changes
3. Jenkins will pick up the new pipeline definition

## Related Repositories

- [algocity_charts](https://github.com/sahanw/algocity_charts): Helm charts
- [algocity_values](https://github.com/sahanw/algocity_values): Service values

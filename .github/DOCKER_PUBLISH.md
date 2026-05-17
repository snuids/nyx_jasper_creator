# GitHub Actions - Docker Publishing Setup

This repository includes a GitHub Actions workflow that automatically builds and pushes Docker images to DockerHub.

## Required Secrets

You need to configure the following secrets in your GitHub repository:

### Setting up DockerHub Secrets

1. **Go to your GitHub repository** → Settings → Secrets and variables → Actions

2. **Create the following secrets:**

   - **`DOCKERHUB_USERNAME`**: Your DockerHub username
   - **`DOCKERHUB_TOKEN`**: Your DockerHub access token (not your password)

### Creating a DockerHub Access Token

1. Log in to [DockerHub](https://hub.docker.com/)
2. Click on your username → Account Settings
3. Go to **Security** → **New Access Token**
4. Give it a description (e.g., "GitHub Actions")
5. Set permissions to **Read & Write**
6. Click **Generate**
7. Copy the token (you won't be able to see it again!)
8. Add this token as `DOCKERHUB_TOKEN` secret in GitHub

## Workflow Triggers

The workflow runs automatically on:

- **Push to `main` or `master` branch**: Builds and pushes with `latest` tag
- **Push tags starting with `v`**: Builds and pushes with version tags (e.g., `v1.0.0`)
- **Pull requests**: Builds only (doesn't push)

## Docker Image Tags

The workflow creates multiple tags for each build:

- `latest` - Latest build from the default branch
- `main` or `master` - Branch name
- `v1.2.3` - Full version (for tagged releases)
- `v1.2` - Minor version
- `v1` - Major version
- `main-abc1234` - Branch name with short commit SHA

## Example: Creating a Release

To create a versioned release:

```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

This will build and push:
- `yourname/nyx-jasper-creator:v1.0.0`
- `yourname/nyx-jasper-creator:v1.0`
- `yourname/nyx-jasper-creator:v1`
- `yourname/nyx-jasper-creator:latest`

## Multi-Architecture Support

The workflow builds images for:
- `linux/amd64` (x86_64)
- `linux/arm64` (Apple Silicon, ARM servers)

## Checking Build Status

- Go to your repository → Actions tab
- Click on the workflow run to see the build logs
- The Docker image digest will be displayed at the end

## Pulling the Image

Once pushed, anyone can pull your image:

```bash
docker pull yourname/nyx-jasper-creator:latest
docker pull yourname/nyx-jasper-creator:v1.0.0
```

## Customizing the Image Name

To change the Docker image name, edit the workflow file `.github/workflows/docker-publish.yml`:

```yaml
env:
  DOCKER_IMAGE: your-dockerhub-username/your-image-name
```

Or use organization/repository name:

```yaml
env:
  DOCKER_IMAGE: ${{ github.repository }}
```

This would create: `github-username/nyx_jasper_creator`

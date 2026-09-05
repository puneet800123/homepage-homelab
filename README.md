# homepage-homelab

A personal DevOps/SRE portfolio that doubles as a live dashboard for a self-hosted
Kubernetes (K3s) homelab.

- **Backend** — Java 21 Spring Boot REST API. Serves profile data, aggregates live
  cluster metrics (by querying Prometheus), and proxies self-hosted app APIs so their
  keys stay server-side.
- **Frontend** — plain HTML + CSS + vanilla JS, served as static files, polling the
  backend JSON endpoints.
- **Manifests** — Kubernetes Deployment/Service/ConfigMap/Secret (+ optional Ingress).
- **Docs** — how to build the container image on a K3s node without installing Docker.

## Layout

```
homepage-homelab/
├── backend/     # Spring Boot (Maven, Java 21)
├── frontend/    # HTML/CSS/JS static site
├── manifests/   # Kubernetes manifests
└── docs/        # build-without-docker guide
```

## API (v1)

| Endpoint | Description |
|----------|-------------|
| `GET /api/profile` | Name, title, summary, links (resume, cover letter, LinkedIn, GitHub), certifications |
| `GET /api/cluster/metrics` | CPU %, memory, network I/O, CPU temp, disk usage (SSD + 2× HDD) via Prometheus |
| `GET /api/apps` | Aggregated status + key stats for each configured self-hosted app |
| `GET /api/apps/{name}` | Per-app detail |
| `GET /actuator/health` | Health probe |

## Status

v1 — base scaffold. Profile is config-driven; cluster metrics query Prometheus;
app proxy is wired but disabled until API keys are supplied. Resume/cover-letter
links and app API keys are `CHANGE_ME` placeholders.

## Quick start (local)

```bash
cd backend
./mvnw spring-boot:run
# open http://localhost:8080
```

## Build & deploy

See [docs/BUILD.md](docs/BUILD.md) for building the image on a K3s node without Docker,
and `manifests/` for deployment.

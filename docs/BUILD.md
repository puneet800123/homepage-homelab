# Building the image on a K3s node without Docker

Installing Docker on a K3s node is disruptive — Docker ships its own containerd
and can clash with the one K3s manages. This project therefore builds the image
with a **daemonless / containerd-native** builder and loads it straight into the
image store K3s already uses (the `k8s.io` containerd namespace), so **no registry
is required**.

The node already has: `k3s`, `ctr`, and the K3s containerd socket at
`/run/k3s/containerd/containerd.sock`. It does **not** have a builder yet.

---

## Option A — nerdctl + BuildKit (recommended)

`nerdctl` is a Docker-compatible CLI for containerd. The **`nerdctl-full`** bundle
includes BuildKit (`buildkitd`/`buildctl`), so nothing touches the K3s containerd
config.

### 1. Install the nerdctl full bundle

```bash
NERDCTL_VERSION=1.7.6
curl -fsSLO "https://github.com/containerd/nerdctl/releases/download/v${NERDCTL_VERSION}/nerdctl-full-${NERDCTL_VERSION}-linux-amd64.tar.gz"
sudo tar Cxzvvf /usr/local "nerdctl-full-${NERDCTL_VERSION}-linux-amd64.tar.gz"
```

### 2. Point nerdctl/BuildKit at the K3s containerd socket

```bash
export CONTAINERD_ADDRESS=/run/k3s/containerd/containerd.sock
export CONTAINERD_NAMESPACE=k8s.io      # the namespace K3s runs images from
```

Start BuildKit against the K3s containerd (run once; leave it running, e.g. via a
`systemd` unit or `nohup`):

```bash
sudo buildkitd \
  --containerd-worker=true \
  --containerd-worker-namespace=k8s.io \
  --oci-worker=false \
  --addr unix:///run/buildkit/buildkitd.sock &
```

### 3. Build into the K3s image store

From the repo root (the build context must contain both `backend/` and `frontend/`):

```bash
sudo -E nerdctl --address /run/k3s/containerd/containerd.sock \
  --namespace k8s.io \
  build -t homepage-homelab:0.1.0 .
```

Verify K3s can see the image:

```bash
sudo k3s ctr -n k8s.io images ls | grep homepage-homelab
```

Because the image lives in the `k8s.io` namespace and the Deployment sets
`imagePullPolicy: IfNotPresent`, the pod runs the local image with no registry.

---

## Option B — Kaniko (in-cluster, nothing installed on the node)

Kaniko builds images inside a pod — no daemon, no node packages. It needs the build
context and a place to push. Simplest is to push to a small in-cluster registry, or
export to the containerd image store via a mounted socket. Sketch:

```bash
# Make the build context available to the pod (e.g. via a PVC/hostPath that holds
# a clone of this repo), then run a kaniko Job pointed at Dockerfile + context,
# pushing to your registry (or --no-push --tarPath and import with ctr).
```

Use Kaniko only if you cannot install nerdctl on the node.

---

## Option C — build elsewhere, import a tarball

On any machine with Docker/nerdctl:

```bash
nerdctl build -t homepage-homelab:0.1.0 .
nerdctl save homepage-homelab:0.1.0 -o homepage-homelab.tar
scp homepage-homelab.tar <node>:/tmp/
```

On the K3s node, import into the `k8s.io` namespace:

```bash
sudo k3s ctr -n k8s.io images import /tmp/homepage-homelab.tar
```

---

## Deploy

Once the image is in the K3s image store:

```bash
kubectl apply -f manifests/homepage-homelab-configmap.yaml
kubectl apply -f manifests/homepage-homelab-secret.yaml      # fill in CHANGE_ME first
kubectl apply -f manifests/homepage-homelab-deployment.yaml
kubectl apply -f manifests/homepage-homelab-service.yaml
# optional:
kubectl apply -f manifests/homepage-homelab-ingress.yaml
```

Find the NodePort and open it:

```bash
kubectl get svc homepage-homelab -n default
# http://<node-ip>:<nodePort>
```

## Rebuild / bump version

Bump the tag in `manifests/homepage-homelab-deployment.yaml` and rebuild with the
new tag (e.g. `homepage-homelab:0.1.1`), then `kubectl rollout restart deployment/homepage-homelab`.
Using a fresh tag each build avoids the "same tag, old image cached" trap.

FROM fedora:31
LABEL maintainer="PRODYNA Cloud-Native Team"

ENV KUBECTL_VERSION=v1.15.2
ENV KUSTOMIZE_VERSION=2.0.3
ENV HELM_VERSION=v2.14.3

    # kubectl
RUN curl -L https://storage.googleapis.com/kubernetes-release/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl -o /usr/local/bin/kubectl && \
    chmod +x /usr/local/bin/kubectl && \
    # kustomize
    curl -L https://github.com/kubernetes-sigs/kustomize/releases/download/v${KUSTOMIZE_VERSION}/kustomize_${KUSTOMIZE_VERSION}_linux_amd64 -o /usr/local/bin/kustomize && \
    chmod +x /usr/local/bin/kustomize && \
    # Helm
    curl -LO https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz && \
    tar -xzf helm-${HELM_VERSION}-linux-amd64.tar.gz && \
    mv ./linux-amd64/helm /usr/local/bin/helm && \
    rm -rf ./linux-amd64/ && \
    rm helm-${HELM_VERSION}-linux-amd64.tar.gz

RUN yum -y install skopeo maven git && \
    yum clean all && \
    rm -rf /var/cache/yum
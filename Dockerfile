FROM jenkins/jenkins:lts-jdk21

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"

USER root

ARG HELM_VERSION="v3.12.0"

# Install basic tooling that Jenkins pipelines often rely on.
RUN apt-get update \
    && apt-get install -y --no-install-recommends git curl docker.io \
    && rm -rf /var/lib/apt/lists/*

# Force install static docker binary to ensure it works
RUN curl -fsSLO https://download.docker.com/linux/static/stable/x86_64/docker-26.1.3.tgz \
    && tar xzvf docker-26.1.3.tgz --strip 1 -C /usr/local/bin docker/docker \
    && rm docker-26.1.3.tgz

RUN usermod -aG docker jenkins

RUN git config --global core.sshCommand 'ssh -o StrictHostKeyChecking=no'

# kubectl is not packaged with the minimal base, so download the binary.
RUN curl -fsSLo /usr/local/bin/kubectl https://dl.k8s.io/release/v1.34.1/bin/linux/amd64/kubectl \
    && chmod +x /usr/local/bin/kubectl

# Download Helm so pipelines can template charts.
RUN curl -fsSL -o /tmp/helm.tar.gz https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz \
    && mkdir -p /tmp/helm && tar -xzf /tmp/helm.tar.gz -C /tmp/helm \
    && mv /tmp/helm/linux-amd64/helm /usr/local/bin/helm \
    && chmod +x /usr/local/bin/helm \
    && chmod +x /usr/local/bin/helm \
    && rm -rf /tmp/helm /tmp/helm.tar.gz

# Install k3d to allow importing images into local k3s clusters
RUN curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash

# Pre-install pipeline and workflow plugins to bootstrap CI jobs faster.
RUN jenkins-plugin-cli --plugins \
    pipeline-model-api \
    pipeline-model-extensions \
    pipeline-stage-view \
    docker-workflow \
    blueocean \
    pipeline-utility-steps \
    credentials-binding \
    github

# Install Jenkins plugins including JCasC
RUN jenkins-plugin-cli --plugins \
    pipeline-model-api \
    pipeline-model-extensions \
    workflow-aggregator \
    git \
    job-dsl \
    configuration-as-code

# Copy JCasC configuration
COPY jenkins/casc_configs/ /var/jenkins_home/casc_configs/
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc_configs/jenkins.yaml

# Setup SSH directory
RUN mkdir -p /var/jenkins_home/.ssh && \
    chown jenkins:jenkins /var/jenkins_home/.ssh && \
    chmod 700 /var/jenkins_home/.ssh

USER jenkins

EXPOSE 8080 50000

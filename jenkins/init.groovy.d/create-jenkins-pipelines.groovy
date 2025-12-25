import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition

def instance = Jenkins.getInstanceOrNull()
if (instance == null) {
    return
}

// Create the new algocity-pipelines job
def newJobName = "algocity-pipelines"
def newPipelineScript = '''
pipeline {
    agent any

    environment {
        SERVICE_NAME = 'algocity_pipelines'
        VERSION = '1.0.0'
        VALUES_FILE = '../values/algocity_pipelines/values/application_values.yaml'
        CHARTS_REPO = "${WORKSPACE}/charts"
        MANIFEST_FILE = 'k8s-manifest.yaml'
        K8S_NAMESPACE = 'default'
    }

    stages {
        stage('Copy Source') {
            steps {
                script {
                    // Clean workspace first to avoid permission issues with overwriting read-only git objects
                    sh "find . -maxdepth 1 -not -name '.' -not -name '..' -exec rm -rf {} + || true"
                    sh "cp -r /var/jenkins_home/source_code/. ."
                }
            }
        }

        stage('Checkout Charts') {
            steps {
                dir('charts') {
                    git branch: 'main', url: 'git@github.com:sahanw/algocity_charts.git'
                }
                dir('values') {
                    git branch: 'main', url: 'git@github.com:sahanw/algocity_values.git'
                }
            }
        }

        stage('Build and Push Image') {
            steps {
                script {
                    echo "Building Docker image..."
                    sh "docker build -t wickysd/algocity:${VERSION} ."
                    
                    echo "Importing image to k3d cluster (Fix for local dev)..."
                    // Try to import into default k3s cluster, ignore error if cluster name differs
                    sh "k3d image import wickysd/algocity:${VERSION} -c k3s-default || echo 'k3d import failed, ignoring...'"

                    echo "Pushing image to Docker Hub (Skipped for local test)..."
                    // sh "docker push wickysd/algocity:${VERSION}"
                }
            }
        }

        stage('Render Helm Templates') {
            steps {
                script {
                    def dashServiceName = env.SERVICE_NAME.replace('_', '-')
                    echo "Rendering Helm templates for ${env.SERVICE_NAME} version ${VERSION}"
                    sh """
                        cd ${CHARTS_REPO}
                        helm dependency build ./examples/sample-service
                        helm template ${dashServiceName} ./examples/sample-service -f ${VALUES_FILE} \\
                            --set serviceAccount.create=true \\
                            --set image.repository=wickysd/algocity \\
                            --set image.tag=${VERSION} \\
                            --set securityContext.readOnlyRootFilesystem=false \\
                            --set livenessProbe.httpGet.path=/login \\
                            --set readinessProbe.httpGet.path=/login \\
                            --set startupProbe.httpGet.path=/login \\
                            --set extraVolumes[0].name=tmp-volume \\
                            --set extraVolumes[0].emptyDir={} \\
                            --set extraVolumeMounts[0].name=tmp-volume \\
                            --set extraVolumeMounts[0].mountPath=/tmp \\
                            > ${WORKSPACE}/${MANIFEST_FILE}
                    """
                    echo "Manifest rendered to ${MANIFEST_FILE}"
                    sh "cat ${WORKSPACE}/${MANIFEST_FILE}"
                }
            }
        }

        stage('Deploy to K3s') {
            steps {
                script {
                    def dashServiceName = env.SERVICE_NAME.replace('_', '-')
                    echo "Deploying ${SERVICE_NAME} to K3s cluster"
                    // Fix kubeconfig for Docker desktop access
                    // The volume for ~/.kube is mounted at /var/jenkins_home/.kube
                    sh """
                        cp /var/jenkins_home/.kube/config ${WORKSPACE}/kubeconfig
                        sed -i 's/0.0.0.0/host.docker.internal/g' ${WORKSPACE}/kubeconfig
                        sed -i 's/127.0.0.1/host.docker.internal/g' ${WORKSPACE}/kubeconfig
                        sed -i 's/localhost/host.docker.internal/g' ${WORKSPACE}/kubeconfig
                    """
                    
                    withEnv(["KUBECONFIG=${WORKSPACE}/kubeconfig"]) {
                        // Create ServiceAccount if it doesn't exist (workaround for chart issue)
                        sh "kubectl create sa ${dashServiceName} -n ${K8S_NAMESPACE} --insecure-skip-tls-verify=true || true"
                        
                        sh "kubectl apply -f ${WORKSPACE}/${MANIFEST_FILE} -n ${K8S_NAMESPACE} --insecure-skip-tls-verify=true"
                        
                        // Restart deployment to ensure new image is pulled (if tag is latest)
                        sh "kubectl rollout restart deployment/${dashServiceName} -n ${K8S_NAMESPACE} --insecure-skip-tls-verify=true"
                        
                        echo "Deployment successful"
                        echo "Checking deployment status..."
                        sh "kubectl rollout status deployment/${dashServiceName} -n ${K8S_NAMESPACE} --timeout=2m --insecure-skip-tls-verify=true"
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline completed successfully!"
            echo "Service ${SERVICE_NAME} deployed to K3s"
        }
        failure {
            echo "Pipeline failed. Check logs for details."
        }
        always {
            echo "Cleaning up workspace..."
            archiveArtifacts artifacts: "${MANIFEST_FILE}", allowEmptyArchive: true
        }
    }
}
'''

WorkflowJob newJob = instance.getItem(newJobName) as WorkflowJob
if (newJob == null) {
    newJob = new WorkflowJob(instance, newJobName)
    instance.add(newJob, newJobName)
}
newJob.setDefinition(new CpsFlowDefinition(newPipelineScript, true))
newJob.setDescription("Deploy algocity_pipelines service using Helm templates to k3s cluster")
newJob.save()

println("Created job: algocity-pipelines")

// Keep the old algocity-helm-deploy job for reference
def oldJobName = "algocity-helm-deploy"
def oldPipelineScript = '''
pipeline {
    agent any
    options {
        timestamps()
    }
    parameters {
        string(name: "CHARTS_REPO_URL",
               defaultValue: "https://github.com/sahanw/algocity_charts.git",
               description: "GitHub repository that holds the Helm charts and libraries.")
        string(name: "CHARTS_REF",
               defaultValue: "main",
               description: "Branch/tag/commit on the charts repo to render.")
        string(name: "VALUES_REPO_URL",
               defaultValue: "https://github.com/sahanw/algocity_values.git",
               description: "GitHub repository containing service values and cluster metadata.")
        string(name: "VALUES_REF",
               defaultValue: "main",
               description: "Branch/tag/commit on the values repo that defines the clusters.")
        choice(name: "SERVICE_NAME",
               choices: ["algocity_pathfinder", "algocity_pipelines"],
               description: "Which service-specific value set will be merged.")
        choice(name: "ENVIRONMENT",
               choices: ["prod", "stage", "qa", "dev"],
               description: "Environment whose overrides should be applied.")
        string(name: "TARGET_DATACENTERS",
               defaultValue: "dc1,dc2",
               description: "Comma-separated datacenters to target; use \\"all\\" to deploy to every configured dc.")
        string(name: "HELM_CHART_PATH",
               defaultValue: "examples/sample-service",
               description: "Relative path inside the charts repo pointing at the service chart.")
        string(name: "HELM_RELEASE_NAME",
               defaultValue: "algocity-sample-service",
               description: "Release name that Helm uses while templating.")
        string(name: "CLUSTERS_FILE",
               defaultValue: "clusters.yaml",
               description: "YAML file inside the values repository that lists kube clusters.")
        string(name: "MANIFEST_DIR",
               defaultValue: "manifests",
               description: "Directory where rendered manifests are written before deployment.")
    }
    stages {
        stage("Clean workspace") {
            steps {
                deleteDir()
            }
        }
        stage("Checkout repositories") {
            steps {
                dir("charts") {
                    git url: params.CHARTS_REPO_URL, branch: params.CHARTS_REF
                }
                dir("values") {
                    git url: params.VALUES_REPO_URL, branch: params.VALUES_REF
                }
            }
        }
        stage("Resolve chart metadata") {
            steps {
                script {
                    def chart = readYaml file: "charts/charts/algocity-common/Chart.yaml"
                    env.COMMON_CHART_VERSION = chart?.version ?: "unknown"
                    echo "Rendering charts with algocity-common ${env.COMMON_CHART_VERSION}"
                }
            }
        }
        stage("Render manifests and deploy") {
            steps {
                script {
                    def clusters = readYaml file: "values/${params.CLUSTERS_FILE}"
                    if (!clusters?.environments) {
                        error("Cluster metadata is missing or malformed in ${params.CLUSTERS_FILE}")
                    }
                    def environmentConfig = clusters.environments[params.ENVIRONMENT]
                    if (!environmentConfig) {
                        error("Environment ${params.ENVIRONMENT} is not defined in ${params.CLUSTERS_FILE}")
                    }
                    def requestedDatacenters = params.TARGET_DATACENTERS.tokenize(",")*.trim().findAll { it }
                    if (requestedDatacenters.contains("all")) {
                        requestedDatacenters = environmentConfig.datacenters.keySet() as List
                    }
                    requestedDatacenters = requestedDatacenters.unique()
                    if (!requestedDatacenters) {
                        error("No datacenters were selected for deployment")
                    }
                    requestedDatacenters.each { dc ->
                        def datacenterConfig = environmentConfig.datacenters[dc]
                        if (!datacenterConfig) {
                            error("Datacenter ${dc} is not configured under ${params.ENVIRONMENT} in ${params.CLUSTERS_FILE}")
                        }
                        if (!datacenterConfig.kubeconfigCredentialId) {
                            error("kubeconfigCredentialId is missing for ${params.ENVIRONMENT}/${dc} in ${params.CLUSTERS_FILE}")
                        }
                        def namespace = datacenterConfig.namespace ?: params.ENVIRONMENT
                        def manifestPath = "${params.MANIFEST_DIR}/${params.SERVICE_NAME}-${params.ENVIRONMENT}-${dc}.yaml"
                        def valueFiles = []
                        def appendIfPresent = { path ->
                            if (fileExists(path)) {
                                valueFiles << path
                            }
                        }
                        appendIfPresent("charts/values/application_values.yaml")
                        appendIfPresent("charts/values/${params.ENVIRONMENT}/environment_values.yaml")
                        appendIfPresent("charts/values/${params.ENVIRONMENT}/${dc}.yaml")
                        def serviceValuesBase = "values/${params.SERVICE_NAME}/values"
                        appendIfPresent("${serviceValuesBase}/application_values.yaml")
                        appendIfPresent("${serviceValuesBase}/${params.ENVIRONMENT}/environment_values.yaml")
                        appendIfPresent("${serviceValuesBase}/${params.ENVIRONMENT}/${dc}.yaml")
                        if (!valueFiles) {
                            error("Unable to find any values files for ${params.SERVICE_NAME}; ensure the chart and values repositories expose at least application_values.yaml")
                        }
                        def valueFlags = valueFiles.collect { "--values '${it}'" }.join(" ")
                        sh """
                            set -eux
                            mkdir -p ${params.MANIFEST_DIR}
                            helm template ${params.HELM_RELEASE_NAME} charts/${params.HELM_CHART_PATH} ${valueFlags} \\
                                --namespace ${namespace} \\
                                --set commonLabels.environment=${params.ENVIRONMENT} \\
                                --set commonLabels.datacenter=${dc} \\
                                --set commonLabels.service=${params.SERVICE_NAME} \\
                                --set commonLabels.chartVersion=${env.COMMON_CHART_VERSION} \\
                                > ${manifestPath}
                        """
                        echo "Rendered ${manifestPath} with ${valueFiles}"
                        withCredentials([file(credentialsId: datacenterConfig.kubeconfigCredentialId, variable: "KUBECONFIG_FILE")]) {
                            sh """
                                set -eux
                                KUBECONFIG=${KUBECONFIG_FILE} kubectl --namespace ${namespace} apply -f ${manifestPath}
                            """
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            cleanWs()
        }
    }
}
'''

WorkflowJob oldJob = instance.getItem(oldJobName) as WorkflowJob
if (oldJob == null) {
    oldJob = new WorkflowJob(instance, oldJobName)
    instance.add(oldJob, oldJobName)
}
oldJob.setDefinition(new CpsFlowDefinition(oldPipelineScript, true))
oldJob.setDescription("Use the AlgoCity charts and values repositories to render manifests and deploy each configured datacenter.")
oldJob.save()

println("Created job: algocity-helm-deploy")

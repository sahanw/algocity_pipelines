pipeline {
    agent any

    environment {
        SERVICE_NAME = 'algocity-pipelines'
        VERSION = "${env.BUILD_NUMBER ?: '1.0.0'}"
        DOCKER_REGISTRY = 'wickysd'
        IMAGE_NAME = "${DOCKER_REGISTRY}/${SERVICE_NAME}"
        
        // GitHub repositories
        CHARTS_REPO_URL = 'https://github.com/sahanw/algocity_charts.git'
        VALUES_REPO_URL = 'https://github.com/sahanw/algocity_values.git'
        
        // Helm configuration (will use workspace paths after checkout)
        CHART_PATH = "${WORKSPACE}/algocity_charts/charts/algocity-pipelines"
        VALUES_FILE = "${WORKSPACE}/algocity_values/algocity_pipelines/values/application_values.yaml"
        
        // K8s configuration
        K8S_NAMESPACE = 'default'
    }

    stages {
        stage('Checkout Repositories') {
            steps {
                script {
                    echo "Cloning algocity_charts from GitHub..."
                    dir('algocity_charts') {
                        git branch: 'main', url: "${CHARTS_REPO_URL}", credentialsId: 'github-token'
                    }
                    
                    echo "Cloning algocity_values from GitHub..."
                    dir('algocity_values') {
                        git branch: 'main', url: "${VALUES_REPO_URL}", credentialsId: 'github-token'
                    }
                    
                    echo "Repositories cloned successfully"
                }
            }
        }
        stage('Build Docker Image') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'docker-hub-creds', passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME')]) {
                        def fullImageName = "${IMAGE_NAME}:${VERSION}"
                        echo "Logging into Docker Hub..."
                        sh "echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin"
                        
                        echo "Building Docker image: ${fullImageName}"
                        sh "docker build -t ${fullImageName} ."
                        
                        // Also tag as latest
                        sh "docker tag ${fullImageName} ${IMAGE_NAME}:latest"
                        
                        echo "Pushing to Docker Hub..."
                        sh "docker push ${fullImageName}"
                        sh "docker push ${IMAGE_NAME}:latest"
                        
                        echo "Image built and pushed successfully"
                        sh "docker logout"
                    }
                }
            }
        }

        stage('Deploy with Helm') {
            steps {
                script {
                    def fullImageName = "${IMAGE_NAME}:${VERSION}"
                    
                    echo "Building Helm dependencies..."
                    sh "cd ${CHART_PATH} && helm dependency build"
                    
                    echo "Deploying ${SERVICE_NAME} to K3s using Helm..."
                    
                    // Fix kubeconfig for Docker desktop access
                    sh """
                        mkdir -p ${WORKSPACE}/.kube
                        cp /var/jenkins_home/.kube/config ${WORKSPACE}/.kube/config
                        sed -i 's/0.0.0.0/host.docker.internal/g' ${WORKSPACE}/.kube/config
                        sed -i 's/127.0.0.1/host.docker.internal/g' ${WORKSPACE}/.kube/config
                    """
                    
                    withEnv(["KUBECONFIG=${WORKSPACE}/.kube/config"]) {
                        // Deploy using Helm
                        sh """
                            helm upgrade --install ${SERVICE_NAME} ${CHART_PATH} \
                                -f ${VALUES_FILE} \
                                --set image.repository=${IMAGE_NAME} \
                                --set image.tag=${VERSION} \
                                --set image.pullPolicy=Always \
                                --set securityContext.readOnlyRootFilesystem=false \
                                --namespace ${K8S_NAMESPACE} \
                                --create-namespace \
                                --wait \
                                --timeout 5m \
                                --insecure-skip-tls-verify
                        """
                        
                        echo "Deployment successful!"
                        echo "Checking deployment status..."
                        
                        sh """
                            kubectl get deployment ${SERVICE_NAME} -n ${K8S_NAMESPACE} --insecure-skip-tls-verify
                            kubectl get pods -n ${K8S_NAMESPACE} -l app=${SERVICE_NAME} --insecure-skip-tls-verify
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo "✅ Pipeline completed successfully!"
            echo "Service ${SERVICE_NAME}:${VERSION} deployed to K3s"
        }
        failure {
            echo "❌ Pipeline failed. Check logs for details."
        }
        always {
            echo "Cleaning up workspace..."
            sh "rm -rf ${WORKSPACE}/.kube"
        }
    }
}

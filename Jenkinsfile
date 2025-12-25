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
                    
                    echo "Pushing image to Docker Hub..."
                    sh "docker push wickysd/algocity:${VERSION}"
                }
            }
        }

        stage('Render Helm Templates') {
            steps {
                script {
                    def dashServiceName = SERVICE_NAME.replace('_', '-')
                    echo "Rendering Helm templates for ${SERVICE_NAME} version ${VERSION}"
                    sh """
                        cd ${CHARTS_REPO}
                        helm dependency build ./examples/sample-service
                        helm template ${dashServiceName} ./examples/sample-service -f ${VALUES_FILE} --set serviceAccount.create=true --set image.repository=wickysd/algocity --set image.tag=${VERSION} > ${WORKSPACE}/${MANIFEST_FILE}
                    """
                    echo "Manifest rendered to ${MANIFEST_FILE}"
                    sh "cat ${WORKSPACE}/${MANIFEST_FILE}"
                }
            }
        }

        stage('Deploy to K3s') {
            steps {
                script {
                    def dashServiceName = SERVICE_NAME.replace('_', '-')
                    echo "Deploying ${SERVICE_NAME} to K3s cluster"
                    // Fix kubeconfig for Docker desktop access
                    sh """
                        cp /var/jenkins_home/.kube/config ${WORKSPACE}/kubeconfig
                        sed -i 's/0.0.0.0/host.docker.internal/g' ${WORKSPACE}/kubeconfig
                        sed -i 's/127.0.0.1/host.docker.internal/g' ${WORKSPACE}/kubeconfig
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

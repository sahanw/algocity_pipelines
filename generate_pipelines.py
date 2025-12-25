#!/usr/bin/env python3
"""
Pipeline Generator
Automatically generates Jenkins pipeline configurations from YAML definitions.
Reads application YAML files and creates corresponding Jenkinsfiles.
"""

import os
import yaml
from pathlib import Path

# Repository configuration
CHARTS_REPO_URL = "https://github.com/sahanw/algocity_charts.git"
VALUES_REPO_URL = "https://github.com/sahanw/algocity_values.git"
DOCKER_REGISTRY = "wickysd"

def generate_jenkinsfile(app_name):
    """Generate Jenkinsfile content for an application"""
    
    service_name = app_name.replace('_', '-')
    
    jenkinsfile = f"""pipeline {{
    agent any

    environment {{
        SERVICE_NAME = '{service_name}'
        VERSION = "${{env.BUILD_NUMBER ?: '1.0.0'}}"
        DOCKER_REGISTRY = '{DOCKER_REGISTRY}'
        IMAGE_NAME = "${{DOCKER_REGISTRY}}/${{SERVICE_NAME}}"
        
        // GitHub repositories
        CHARTS_REPO_URL = '{CHARTS_REPO_URL}'
        VALUES_REPO_URL = '{VALUES_REPO_URL}'
        
        // Helm configuration (will use workspace paths after checkout)
        CHART_PATH = "${{WORKSPACE}}/algocity_charts/charts/${{SERVICE_NAME}}"
        VALUES_FILE = "${{WORKSPACE}}/algocity_values/{app_name}/values/application_values.yaml"
        
        // K8s configuration
        K8S_NAMESPACE = 'default'
    }}

    stages {{
        stage('Checkout Repositories') {{
            steps {{
                script {{
                    echo "Cloning algocity_charts from GitHub..."
                    dir('algocity_charts') {{
                        git branch: 'main', url: "${{CHARTS_REPO_URL}}"
                    }}
                    
                    echo "Cloning algocity_values from GitHub..."
                    dir('algocity_values') {{
                        git branch: 'main', url: "${{VALUES_REPO_URL}}"
                    }}
                    
                    echo "Repositories cloned successfully"
                }}
            }}
        }}

        stage('Build Docker Image') {{
            steps {{
                script {{
                    def fullImageName = "${{IMAGE_NAME}}:${{VERSION}}"
                    echo "Building Docker image: ${{fullImageName}}"
                    sh "docker build -t ${{fullImageName}} ."
                    
                    // Also tag as latest
                    sh "docker tag ${{fullImageName}} ${{IMAGE_NAME}}:latest"
                    
                    echo "Pushing to Docker Hub..."
                    sh "docker push ${{fullImageName}}"
                    sh "docker push ${{IMAGE_NAME}}:latest"
                    
                    echo "Image built and pushed successfully"
                }}
            }}
        }}

        stage('Deploy with Helm') {{
            steps {{
                script {{
                    def fullImageName = "${{IMAGE_NAME}}:${{VERSION}}"
                    
                    echo "Building Helm dependencies..."
                    sh "cd ${{CHART_PATH}} && helm dependency build"
                    
                    echo "Deploying ${{SERVICE_NAME}} to K3s using Helm..."
                    
                    // Render manifest
                    sh \"\"\"
                        cd ${{CHART_PATH}} && helm template ${{SERVICE_NAME}} . \\
                            -f ${{VALUES_FILE}} \\
                            > /tmp/${{SERVICE_NAME}}-manifest.yaml
                    \"\"\"
                    
                    // Apply to K8s
                    sh "kubectl apply -f /tmp/${{SERVICE_NAME}}-manifest.yaml -n ${{K8S_NAMESPACE}}"
                    
                    echo "Deployment successful!"
                    echo "Checking deployment status..."
                    
                    sh \"\"\"
                        kubectl get deployment ${{SERVICE_NAME}} -n ${{K8S_NAMESPACE}}
                        kubectl get pods -n ${{K8S_NAMESPACE}} -l app.kubernetes.io/name=${{SERVICE_NAME}}
                    \"\"\"
                }}
            }}
        }}
    }}

    post {{
        success {{
            echo "✅ Pipeline completed successfully!"
            echo "Service ${{SERVICE_NAME}}:${{VERSION}} deployed to K3s"
        }}
        failure {{
            echo "❌ Pipeline failed. Check logs for details."
        }}
        always {{
            echo "Cleaning up workspace..."
            sh "rm -f /tmp/${{SERVICE_NAME}}-manifest.yaml"
        }}
    }}
}}
"""
    return jenkinsfile

def main():
    """Main function to generate pipelines"""
    applications_dir = Path(__file__).parent / "applications"
    
    if not applications_dir.exists():
        print(f"Applications directory not found: {{applications_dir}}")
        return
    
    # Process each YAML file in applications directory
    for yaml_file in applications_dir.glob("*.yaml"):
        with open(yaml_file, 'r') as f:
            config = yaml.safe_load(f)
        
        app_name = config.get('application', {}).get('name')
        if not app_name:
            print(f"Skipping {{yaml_file.name}}: no application name found")
            continue
        
        print(f"Generating Jenkinsfile for {{app_name}}...")
        
        # Generate Jenkinsfile content
        jenkinsfile_content = generate_jenkinsfile(app_name.replace('-', '_'))
        
        # Write to generated directory
        output_dir = Path(__file__).parent / "generated" / app_name
        output_dir.mkdir(parents=True, exist_ok=True)
        
        jenkinsfile_path = output_dir / "Jenkinsfile"
        with open(jenkinsfile_path, 'w') as f:
            f.write(jenkinsfile_content)
        
        print(f"✅ Generated: {{jenkinsfile_path}}")

if __name__ == "__main__":
    main()

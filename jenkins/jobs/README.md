# Jenkins Job DSL Configuration

This directory contains Jenkins Job DSL scripts that automatically create pipeline jobs.

## How it works

1. The `create-pipelines.groovy` script reads YAML files from the `applications/` directory
2. For each application YAML file, it creates a corresponding Jenkins pipeline job
3. The pipeline job points to the application's GitHub repository and uses its Jenkinsfile

## Usage

In Jenkins:
1. Create a "Seed Job" using the Job DSL plugin
2. Point it to this repository
3. Set the DSL script path to: `jenkins/jobs/create-pipelines.groovy`
4. Run the seed job to auto-create all application pipelines

## Adding a new application

1. Create a new YAML file in `applications/` directory:
   ```yaml
   repository: https://github.com/sahanw/your-app.git
   template: templates/common-pipeline.yaml
   ```
2. Commit and push
3. Re-run the seed job in Jenkins
4. Your new pipeline will be automatically created!

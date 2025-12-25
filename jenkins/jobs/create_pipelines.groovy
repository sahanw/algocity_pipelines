// Job DSL script to create pipeline jobs for AlgoCity applications
// This script is executed by the seed-job to auto-generate Jenkins pipelines

// Define applications with their repository URLs
def applications = [
    [
        name: 'algocity-pipelines',
        repoUrl: 'https://github.com/sahanw/algocity_pipelines.git'
    ],
    [
        name: 'algocity-pathfinder',
        repoUrl: 'https://github.com/sahanw/algocity_pathfinder.git'
    ]
]

// Create a pipeline job for each application
applications.each { app ->
    pipelineJob(app.name) {
        description("Auto-generated pipeline for ${app.name}")
        
        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(app.repoUrl)
                            credentials('github-token')
                        }
                        branch('*/main')
                    }
                }
                scriptPath('Jenkinsfile')
            }
        }
        
        triggers {
            scm('H/5 * * * *')  // Poll SCM every 5 minutes
        }
    }
}

println "Successfully created ${applications.size()} pipeline jobs"

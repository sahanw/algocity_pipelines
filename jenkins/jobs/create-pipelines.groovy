// Jenkins Job DSL Script
// Auto-generates pipeline jobs from YAML definitions

import groovy.yaml.YamlSlurper

def yamlSlurper = new YamlSlurper()
def applicationsDir = new File('/var/jenkins_home/workspace/applications')

if (!applicationsDir.exists()) {
    println "Applications directory not found, cloning repository..."
    def cloneCmd = "git clone https://github.com/sahanw/algocity_pipelines.git /tmp/algocity_pipelines"
    cloneCmd.execute().waitFor()
    applicationsDir = new File('/tmp/algocity_pipelines/applications')
}

// Process each YAML file in applications directory
applicationsDir.eachFileMatch(~/.*\.yaml$/) { file ->
    def config = yamlSlurper.parse(file)
    def appName = file.name.replaceAll('\\.yaml$', '')
    def repoUrl = config.repository
    
    println "Creating pipeline job for: ${appName}"
    
    pipelineJob(appName) {
        description("Auto-generated pipeline for ${appName}")
        
        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(repoUrl)
                            credentials('github-credentials')
                        }
                        branch('*/main')
                    }
                }
                scriptPath('Jenkinsfile')
            }
        }
        
        triggers {
            scm('H/5 * * * *') // Poll SCM every 5 minutes
        }
    }
}

println "Pipeline jobs created successfully!"

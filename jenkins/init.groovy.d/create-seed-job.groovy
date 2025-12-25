import jenkins.model.Jenkins
import hudson.model.FreeStyleProject
import javaposse.jobdsl.plugin.*
import hudson.plugins.git.GitSCM
import hudson.plugins.git.BranchSpec

// Auto-create seed job that generates pipelines from YAML files
def jenkins = Jenkins.instance

// Check if seed job already exists
def seedJobName = "seed-job"
def seedJob = jenkins.getItem(seedJobName)

if (seedJob == null) {
    println "Creating seed job..."
    
    // Create freestyle project
    seedJob = jenkins.createProject(FreeStyleProject, seedJobName)
    seedJob.setDescription("Auto-generated seed job that creates pipelines from applications/*.yaml files")
    
    // Configure Git SCM
    def scm = new GitSCM("https://github.com/sahanw/algocity_pipelines.git")
    scm.branches = [new BranchSpec("*/main")]
    seedJob.setScm(scm)
    
    // Add Job DSL build step
    def jobDslBuildStep = new ExecuteDslScripts()
    jobDslBuildStep.setTargets("jenkins/jobs/create-pipelines.groovy")
    jobDslBuildStep.setRemovedJobAction(RemovedJobAction.DELETE)
    jobDslBuildStep.setRemovedViewAction(RemovedViewAction.DELETE)
    jobDslBuildStep.setLookupStrategy(LookupStrategy.SEED_JOB)
    
    seedJob.buildersList.add(jobDslBuildStep)
    seedJob.save()
    
    println "Seed job created successfully!"
    
    // Trigger the seed job to create all pipelines
    println "Triggering seed job to create pipelines..."
    seedJob.scheduleBuild2(0)
    
    println "Pipelines will be created automatically!"
} else {
    println "Seed job already exists, skipping creation"
}

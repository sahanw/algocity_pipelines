import jenkins.model.Jenkins
import hudson.model.FreeStyleProject
import javaposse.jobdsl.plugin.*
import hudson.plugins.git.GitSCM
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.extensions.impl.CloneOption
import hudson.plugins.git.UserRemoteConfig

// Auto-create seed job that generates pipelines from YAML files
def jenkins = Jenkins.instance

// Check if seed job already exists
def seedJobName = "seed-job"
def seedJob = jenkins.getItem(seedJobName)

if (seedJob != null) {
    println "Seed job already exists, deleting and recreating..."
    seedJob.delete()
}

println "Creating seed job..."

// Create freestyle project
seedJob = jenkins.createProject(FreeStyleProject, seedJobName)
seedJob.setDescription("Auto-generated seed job that creates pipelines from applications/*.yaml files")

// Configure Git SCM without credentials (public repo)
// Use UserRemoteConfig to explicitly set no credentials
def userRemoteConfig = new UserRemoteConfig(
    "https://github.com/sahanw/algocity_pipelines.git",  // url
    "",                                                    // name
    "",                                                    // refspec
    null                                                   // credentialsId (null = no credentials)
)

def scm = new GitSCM([userRemoteConfig])
scm.branches = [new BranchSpec("*/main")]

// Add clone options to make it shallow and faster
def cloneOption = new CloneOption(true, true, null, 1)
scm.extensions.add(cloneOption)

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

#!/usr/bin/groovy

/*
 * Main build script for Maven-based FOLIO projects
 *
 * Configurable parameters: 
 *
 * doDocker:  Build, test, and publish Docker image via 'buildJavaDocker' (Default: 'no'/false)
 * mvnDeploy: Deploy built artifacts to Maven repository (Default: 'no'/false)
 * publishModDescriptor:  POST generated module descriptor to FOLIO registry (Default: 'no'/false)
 * publishPreviewMD:  POST generated module descriptor to Preview registry (Default: 'no'/false)
 * publishAPI: Publish API RAML documentation.  (Default: 'no'/false)
 * runLintRamlCop: Run 'raml-cop' on back-end modules that have declared RAML in api.yml (Default: 'no'/false)
*/
 


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def foliociLib = new org.folio.foliociCommands()

  // Lint RAML for RAMLCop.  default is false
  def doLintRamlCop = config.runLintRamlCop ?: false
  if (doLintRamlCop ==~ /(?i)(Y|YES|T|TRUE)/) { doLintRamlCop = true } 
  if (doLintRamlCop ==~ /(?i)(N|NO|F|FALSE)/) { doLintRamlCop = false } 

  // publish maven artifacts to Maven repo.  Default is false
  def mvnDeploy = config.mvnDeploy ?: false
  if (mvnDeploy ==~ /(?i)(Y|YES|T|TRUE)/) { mvnDeploy = true }
  if (mvnDeploy ==~ /(?i)(N|NO|F|FALSE)/) { mvnDeploy = false }

  // publish mod descriptor to folio-registry. Default is false
  def publishModDescriptor = config.publishModDescriptor ?: false
  if (publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) { publishModDescriptor = true }
  if (publishModDescriptor ==~ /(?i)(N|NO|F|FALSE)/) { publishModDescriptor = false }

  // publish preview mod descriptor to folio-registry. Default is false
  def publishPreviewMD = config.publishPreview ?: false
  if (publishPreviewMD ==~ /(?i)(Y|YES|T|TRUE)/) { publishPreviewMD = true }
  if (publishPreviewMD ==~ /(?i)(N|NO|F|FALSE)/) { publishPreviewMD = false }

  // publish API documentation to foliodocs. Default is false
  def publishAPI = config.publishAPI ?: false
  if (publishAPI ==~ /(?i)(Y|YES|T|TRUE)/) { publishAPI = true }
  if (publishAPI ==~ /(?i)(N|NO|F|FALSE)/) { publishAPI = false }

  // deploy module to Kubernetes. Default is false
  def doKubeDeploy = config.doKubeDeploy ?: false
  if (doKubeDeploy ==~ /(?i)(Y|YES|T|TRUE)/) { doKubeDeploy = true }
  if (doKubeDeploy ==~ /(?i)(N|NO|F|FALSE)/) { doKubeDeploy = false }

  // location of Maven MD
  def modDescriptor =  'target/ModuleDescriptor.json'



  def buildNode = config.buildNode ?: 'jenkins-slave-all'

  properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '',
                                          artifactNumToKeepStr: '15',
                                          daysToKeepStr: '',
                                          numToKeepStr: '30'))])


  node(buildNode) {
    timeout(60) {

      try {
        stage('Checkout') {
          deleteDir()
          currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
          sendNotifications 'STARTED'

          checkout([
                 $class: 'GitSCM',
                 branches: scm.branches,
                 extensions: scm.extensions + [[$class: 'SubmoduleOption',
                                                       disableSubmodules: false,
                                                       parentCredentials: false,
                                                       recursiveSubmodules: true,
                                                       reference: '',
                                                       trackingSubmodules: false]],
                 userRemoteConfigs: scm.userRemoteConfigs
          ])

          echo "Checked out branch: $env.BRANCH_NAME"
        }

        stage('Set Environment') {
          setEnvMvn()
        }

        if (doLintRamlCop) {
          stage('Lint raml-cop') {
            runLintRamlCop()
          }
        }

        stage('Maven Build') {
          echo "Building Maven artifact: ${env.name} Version: ${env.version}"
          withMaven(jdk: 'openjdk-8-jenkins-slave-all',  
                    maven: 'maven3-jenkins-slave-all',  
                    mavenSettingsConfig: 'folioci-maven-settings') {
    
            // Check to see if we have snapshot deps in release
            if (env.isRelease) {
              def snapshotDeps = foliociLib.checkMvnReleaseDeps() 
              if (snapshotDeps) { 
                echo "$snapshotDeps"
                error('Snapshot dependencies found in release')
              }
            }
            sh 'mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install ' \
                + 'org.jacoco:jacoco-maven-plugin:report'
            if ( fileExists(modDescriptor) ) {
              foliociLib.updateModDescriptor(modDescriptor)
            }
          }
        }

        // Run Sonarqube
        stage('SonarQube Analysis') {
          sonarqubeMvn() 
        }

        if ( env.isRelease && fileExists(modDescriptor) ) {
          stage('Dependency Check') {
            okapiModDepCheck(modDescriptor)
          }
        }
        
        // Docker stuff
        if (config.doDocker) {
          stage('Docker Build') {
            echo "Building Docker image for $env.name:$env.version" 
            config.doDocker.delegate = this
            config.doDocker.resolveStrategy = Closure.DELEGATE_FIRST
	    config.doDocker.call()
          }
        } 

        // master branch or tagged releases
        if (( env.BRANCH_NAME == 'master' ) || ( env.isRelease )) {

          // publish MD must come before maven deploy
          if (publishModDescriptor) {
            stage('Publish Module Descriptor') {
              echo "Publishing Module Descriptor to FOLIO registry"
              postModuleDescriptor(modDescriptor) 
            }
          }
          if (mvnDeploy) {
            stage('Maven Deploy') {
              echo "Deploying artifacts to Maven repository"
              withMaven(jdk: 'openjdk-8-jenkins-slave-all', 
                      maven: 'maven3-jenkins-slave-all', 
                      mavenSettingsConfig: 'folioci-maven-settings') {
                sh 'mvn -DskipTests deploy'
              }
            }
          }
          if (publishAPI) {
            stage('Publish API Docs') {
              echo "Publishing API docs"
              sh "python3 /usr/local/bin/generate_api_docs.py -r $env.projectName -l info -o folio-api-docs"
              withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', 
                   accessKeyVariable: 'AWS_ACCESS_KEY_ID', 
                   credentialsId: 'jenkins-aws', 
                   secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                sh 'aws s3 sync folio-api-docs s3://foliodocs/api'
              }
            }
          }
          if (doKubeDeploy) {
            stage('Kubernetes Deploy') {
              echo "Deploying to kubernetes cluster"
              kubeDeploy('folio-default',
                         "[{" +
                            "\"name\" : \"${env.name}\"," +
                            "\"version\" : \"${env.version}\"," +
                            "\"deploy\":true" +
                         "}]")
            }
          }
        //} else if (env.CHANGE_ID && publishPreview) {
        // don't force PR for test
        } else if (publishPreviewMD) {
          stage('Publish Preview Module Descriptor') {
            echo "Publishing preview module descriptor to CI preview okapi"
            postPreivewMD() 
          } 
          stage('Kubernetes Preview Deploy') {
            echo "kube deploy goes here"
          }
        }


        if (doLintRamlCop) {
          stage('Lint raml schema') {
            runLintRamlSchema()
          }
        }
      } // end try
      catch (Exception err) {
        currentBuild.result = 'FAILED'
        println(err.getMessage());
        echo "Build Result: $currentBuild.result"
        throw err
      }
      finally {
        sendNotifications currentBuild.result
      }
    } //end timeout
  } // end node
} 


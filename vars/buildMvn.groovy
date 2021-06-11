#!/usr/bin/groovy

/*
 * Main build script for Maven-based FOLIO projects
 *
 * Configurable parameters:
 *
 * doDocker:  Build, test, and publish Docker image via 'buildJavaDocker' (Default: 'no'/false)
 * mvnDeploy: Deploy built artifacts to Maven repository (Default: 'no'/false)
 * publishModDescriptor:  POST generated module descriptor to FOLIO registry (Default: 'no'/false)
 * publishPreview: publish preview image to preview CI environment (Default: 'no'/false)
 * publishAPI: Publish API RAML documentation. Deprecated, use doApiDoc. (Default: 'no'/false)
 * runLintRamlCop: Run 'raml-cop' on back-end modules that have declared RAML in api.yml file. Deprecated, use doApiLint. (Default: 'no'/false)
 * doApiLint: Assess API description files (RAML OAS) (Default: false)
 * doApiDoc: Generate and publish documentation from API description files. (RAML OAS) (Default: false)
 * doUploadApidocs: Publish build-generated API documentation (Default: false)
*/



def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def foliociLib = new org.folio.foliociCommands()

  // Lint RAML for RAMLCop.  default is false
  // Deprecated: Replaced by doApiLint
  def doLintRamlCop = config.runLintRamlCop ?: false
  if (doLintRamlCop ==~ /(?i)(Y|YES|T|TRUE)/) { doLintRamlCop = true }
  if (doLintRamlCop ==~ /(?i)(N|NO|F|FALSE)/) { doLintRamlCop = false }
  // API lint and API doc
  def doApiLint = config.doApiLint ?: false
  if (doApiLint ==~ /(?i)(Y|YES|T|TRUE)/) { doApiLint = true }
  if (doApiLint ==~ /(?i)(N|NO|F|FALSE)/) { doApiLint = false }
  def doApiDoc = config.doApiDoc ?: false
  if (doApiDoc ==~ /(?i)(Y|YES|T|TRUE)/) { doApiDoc = true }
  if (doApiDoc ==~ /(?i)(N|NO|F|FALSE)/) { doApiDoc = false }
  def doUploadApidocs = config.doUploadApidocs ?: false
  if (doUploadApidocs ==~ /(?i)(Y|YES|T|TRUE)/) { doUploadApidocs = true }
  if (doUploadApidocs ==~ /(?i)(N|NO|F|FALSE)/) { doUploadApidocs = false }
  def apiTypes = config.apiTypes ?: ''
  def apiDirectories = config.apiDirectories ?: ''
  def apiExcludes = config.apiExcludes ?: ''

  // publish maven artifacts to Maven repo.  Default is false
  def mvnDeploy = config.mvnDeploy ?: false
  if (mvnDeploy ==~ /(?i)(Y|YES|T|TRUE)/) { mvnDeploy = true }
  if (mvnDeploy ==~ /(?i)(N|NO|F|FALSE)/) { mvnDeploy = false }

  // publish mod descriptor to folio-registry. Default is false
  def publishModDescriptor = config.publishModDescriptor ?: false
  if (publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) { publishModDescriptor = true }
  if (publishModDescriptor ==~ /(?i)(N|NO|F|FALSE)/) { publishModDescriptor = false }

  // publish preview mod descriptor to folio-registry. Default is false
  def publishPreview = config.publishPreview ?: false
  // disable for now --malc
  if (publishPreview ==~ /(?i)(Y|YES|T|TRUE)/) { publishPreview = false }
  if (publishPreview ==~ /(?i)(N|NO|F|FALSE)/) { publishPreview = false }

  // publish API documentation to foliodocs. Default is false
  // Deprecated, use doApiDoc.
  def publishAPI = config.publishAPI ?: false
  if (publishAPI ==~ /(?i)(Y|YES|T|TRUE)/) { publishAPI = true }
  if (publishAPI ==~ /(?i)(N|NO|F|FALSE)/) { publishAPI = false }

  // build debian package. Default is false
  def buildDeb = config.buildDeb ?: false
  if (buildDeb ==~ /(?i)(Y|YES|T|TRUE)/) { buildDeb = true }
  if (buildDeb ==~ /(?i)(N|NO|F|FALSE)/) { buildDeb = false }

  // deploy module to Kubernetes. Default is false
  def doKubeDeploy = config.doKubeDeploy ?: false
  // set to false globally for now. --malc
  if (doKubeDeploy ==~ /(?i)(Y|YES|T|TRUE)/) { doKubeDeploy = false }
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
          if ( env.BRANCH_IS_PRIMARY ) {
            echo "Branch is primary: true"
          }
        }

        stage('Set Environment') {
          setEnvMvn()
        }

        if (doLintRamlCop) {
          stage('Lint raml-cop') {
            runLintRamlCop()
          }
        }

        if (doApiLint) {
          stage('API lint') {
            runApiLint(apiTypes, apiDirectories, apiExcludes)
          }
        }

        stage('Maven Build') {
          echo "Building Maven artifact: ${env.name} Version: ${env.version}"
          withMaven(jdk: "${env.javaInstall}",
                    maven: "maven3-jenkins-slave-all",
                    mavenSettingsConfig: "folioci-maven-settings") {

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

        // Run Sonarqube,
        // but not on jenkins-slave-all as Sonarqube no longer supports Java 8
        if (buildNode != 'jenkins-slave-all') {
          stage('SonarQube Analysis') {
            sonarqubeMvn()
          }
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

        // main branch or tagged releases
        if (( env.BRANCH_IS_PRIMARY ) || ( env.isRelease )) {

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
              withMaven(jdk: "${env.javaInstall}",
                      maven: "maven3-jenkins-slave-all",
                      mavenSettingsConfig: "folioci-maven-settings") {
                sh 'mvn -DskipTests clean deploy'
              }
            }
          }
          if (doUploadApidocs) {
            stage('Upload build-time API docs') {
              runUploadApidocs('mvn')
            }
          }
          if (publishAPI) {
            // Deprecated, use doApiDoc
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
          if (doApiDoc) {
            stage('Generate API docs') {
              runApiDoc(apiTypes, apiDirectories, apiExcludes)
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
        } 

        if (env.CHANGE_ID && publishPreview) {
          stage('Publish Preview Module Descriptor') {
            echo "Publishing preview module descriptor to CI preview okapi"
            postPreviewMD()
          }
          stage('Kubernetes Deploy') {
            def previewId = "${env.bareVersion}.${env.CHANGE_ID}.${env.BUILD_NUMBER}"
            echo "Deploying to kubernetes cluster"
            kubeDeploy('folio-preview',
                       "[{" +
                          "\"name\" : \"${env.name}\"," +
                          "\"version\" : \"${previewId}\"," +
                          "\"deploy\":true" +
                       "}]", "http://okapi-preview:9130")
          }
        }

        if (env.isRelease && buildDeb) {
          stage('Build Debian package') {
            build job: 'Automation/build-debian-package',
                        parameters: [string(name: 'GIT_RELEASE_TAG', value: "${env.BRANCH_NAME}"), string(name: 'GIT_REPO_URL', value: "${env.projUrl}")]
          }
        }

        if (doLintRamlCop) {
          stage('Lint raml schema') {
            runLintRamlSchema()
          }
        }

        if (doApiLint) {
          stage('API schema lint') {
            runApiSchemaLint(apiDirectories, apiExcludes)
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


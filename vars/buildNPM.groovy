#!/usr/bin/env groovy

/*
 * Main build script for NPM-based FOLIO projects
 *
 * Configurable parameters: 
 *
 * doDocker:  Build, test, and publish Docker image via 'buildDocker' (Default: 'no')
 * runLint: Run ESLint via 'yarn lint' (Default: 'no')
 * runTest: Run unit tests via 'yarn test' (Default: 'no')
 * runTestOptions:  Extra opts to pass to 'yarn test'
 * runScripts: A Map of optional script commands and script arguments.  (Default: [:])
 * runRegression: Run UI regression module tests for PRs - 'yes' or 'no' (Default: 'no') 
 * runSonarqube: Run the Sonarqube scanner and generate reports on sonarcloud.io (Default: 'no']
 * regressionDebugMode:  Enable extra debug logging in regression tests (Default: false)
 * npmDeploy: Publish NPM artifacts to NPM repository (Default: 'yes')
 * publishModDescriptor:  POST generated module descriptor to FOLIO registry (Default: 'no')
 * modDescriptor: path to standalone Module Descriptor file (Optional)
 * publishApi: Publish API/RAML documentation.  (Default: 'no')
 * buildNode: label of jenkin's slave build node to use
*/


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def foliociLib = new org.folio.foliociCommands()
  
  // default is to deploy to npm repo when branch is master
  def npmDeploy = config.npmDeploy ?: true
  if (npmDeploy ==~ /(?i)(Y|YES|T|TRUE)/) { npmDeploy = true }
  if (npmDeploy ==~ /(?i)(N|NO|F|FALSE)/) { npmDeploy = false }

  // publish API documentation to foliodocs
  def publishAPI = config.publishAPI ?: false
  if (publishAPI ==~ /(?i)(Y|YES|T|TRUE)/) { publishAPI = true }
  if (publishAPI ==~ /(?i)(N|NO|F|FALSE)/) { publishAPI = false }

  // publish mod descriptor to folio-registry
  def publishModDescriptor = config.publishModDescriptor ?: false
  if (publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) { publishModDescriptor = true }
  if (publishModDescriptor ==~ /(?i)(N|NO|F|FALSE)/) { publishModDescriptor = false }
  
  // default is don't run regression tests for PRs
  def runRegression = config.runRegression ?: false
  if (runRegression ==~ /(?i)(Y|YES|T|TRUE)/) { runRegression = true }
  if (runRegression ==~ /(?i)(N|NO|F|FALSE)/) { runRegression = false }

  // enable debugging logging on regression tests 
  def regressionDebugMode = config.regressionDebugMode ?: false

  // run 'yarn lint'
  def runLint = config.runLint ?: false
  if (runLint ==~ /(?i)(Y|YES|T|TRUE)/) { runLint = true }
  if (runLint ==~ /(?i)(N|NO|F|FALSE)/) { runLint = false }

  // run 'yarn test'
  def runTest = config.runTest ?: false
  if (runTest ==~ /(?i)(Y|YES|T|TRUE)/) { runTest = true }
  if (runTest ==~ /(?i)(N|NO|F|FALSE)/) { runTest = false }

  // default runTestOptions
  def runTestOptions = config.runTestOptions ?: ''

  // default runSonarqube 
  def runSonarqube = config.runSonarqube ?: false

  // default mod descriptor
  def modDescriptor = config.modDescriptor ?: ''

  // default Stripes platform.  Empty Map
  def Map stripesPlatform = config.stripesPlatform ?: [:]

  // run NPM script.  An empty Map
  def Map runScripts = config.runScripts ?: [:]

  // use the smaller nodejs build node since most 
  // Nodejs builds are Stripes.
  def buildNode = config.buildNode ?: 'jenkins-slave-all'


  properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '', 
                                          artifactNumToKeepStr: '30', 
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
                                                       trackingSubmodules: false],
                                               [$class: 'RelativeTargetDirectory',
                                                       relativeTargetDir: 'project' ]],
                 userRemoteConfigs: scm.userRemoteConfigs
          ])

          echo "Checked out branch: $env.BRANCH_NAME"
        }

        dir("${env.WORKSPACE}/project") {
          stage('Set Environment') {
            setEnvNPM()
          }

          withCredentials([string(credentialsId: 'jenkins-npm-folioci',variable: 'NPM_TOKEN')]) {
            withNPM(npmrcConfig: env.npmConfig) {
              stage('NPM Install') {
                sh 'yarn install' 
                sh 'yarn list --pattern @folio'
              }

              if (runLint) {
                runLintNPM()
              } 

              if (runTest) {
                runTestNPM(runTestOptions)
              }

              // Stage 'Run NPM scripts'
              if (runScripts.size() >= 1) { 
                runScripts.each { scriptName,scriptArgs -> runNPMScript(scriptName,scriptArgs) }
              }

              // Run Sonarqube scanner       
              if (runSonarqube) {
                stage('Run Sonarqube') {
                  sonarqubeScanNPM() 
                }
              }
         
              stage('Generate Module Descriptor') { 
                // really meant to cover non-Stripes module cases. e.g mod-graphql
                if (modDescriptor) {       
                  if (env.snapshot) {
                    // update the version to the snapshot version
                    echo "Update Module Descriptor version to snapshot version"
                    foliociLib.updateModDescriptorId(modDescriptor)
                  }
                  foliociLib.updateModDescriptor(modDescriptor)
                }
                // Stripes modules
                else {
                  echo "Generating Stripes module descriptor from package.json"
                  sh "mkdir -p artifacts/md"
                  sh "stripes mod descriptor --full --strict | jq '.[]' " +
                     "> artifacts/md/${env.simpleName}.json"
                  modDescriptor = "${env.WORKSPACE}/project/artifacts/md/${env.simpleName}.json"
                }
              } 

              if (( env.BRANCH_NAME == 'master' ) ||  ( env.isRelease )) {
                if (npmDeploy) {
                  stage('NPM Publish') {
                    // do some clean up before publishing package
                    sh 'rm -rf node_modules artifacts ci'
               
                    // npm is more flexible than yarn for this stage. 
                    echo "Deploying NPM packages to Nexus repository"
                    sh 'npm publish'
                  }
                }
              }

            }  // end withNPM
            // remove .npmrc put in directory by withNPM
            sh 'rm -f .npmrc'
          }  // end WithCred    

          if (config.doDocker) {
            stage('Docker Build') {
              echo "Building Docker image for $env.name:$env.version" 
              config.doDocker.delegate = this
              config.doDocker.resolveStrategy = Closure.DELEGATE_FIRST
              config.doDocker.call()
            }
          } 

          if (( env.BRANCH_NAME == 'master' ) || ( env.isRelease )) {
            if (publishModDescriptor) {
              // We assume that MDs are included in package.json
              stage('Publish Module Descriptor') {
                echo "Publishing Module Descriptor to FOLIO registry"
                postModuleDescriptor(modDescriptor) 
              }
            }

            if (publishAPI) {
              stage('Publish API Docs') {
                echo "Publishing API docs"
                sh "python3 /usr/local/bin/generate_api_docs.py -r $env.name -l info -o folio-api-docs"
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                     accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                     credentialsId: 'jenkins-aws',
                     secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                  sh 'aws s3 sync folio-api-docs s3://foliodocs/api'
                }
              }
            }
          } 
        } // end dir

        // actions specific to PRs
        if (env.CHANGE_ID) {

          // ensure tenant id is unique
          // def tenant = "${env.BRANCH_NAME}_${env.BUILD_NUMBER}"
          def tenant = "pr_${env.CHANGE_ID}_${env.BUILD_NUMBER}"
          tenant = foliociLib.replaceHyphen(tenant)

          if (stripesPlatform != null) { 
            dir("${env.WORKSPACE}/$stripesPlatform.repo") {
              stage('Build Stripes Platform') {
                git branch: stripesPlatform.branch, 
                    url: "https://github.com/folio-org/${stripesPlatform.repo}"
                buildStripesPlatformPr(env.okapiUrl,tenant) 
              }
              stage('Okapi Dependency Check') {
                def myMD = readFile("artifacts/md/${env.folioName}-${env.version}.json")
               
                def md2Install = libraryResource('org/folio/md2install.sh')
                writeFile file: 'md2install.sh', text: md2Install
                sh 'chmod +x md2install.sh'

                def stripesInstall = sh(returnStdout: true,
                                        script: './md2install.sh ./ModuleDescriptors')
                sh 'rm -f ./md2install.sh'

                okapiDepCheck(tenant,myMD,stripesInstall) 
              }
            }             
          }

          if (runRegression) { 
            stage('Bootstrap Tenant') { 
              def tenantStatus = deployTenant("$okapiUrl","$tenant") 
              env.tenantStatus = tenantStatus
            }
     
            if (env.tenantStatus != '0') {
              echo "Tenant Bootstrap Status: $env.tenantStatus"
              echo "Problem deploying tenant. Skipping UI Regression testing."
            }
            else { 
              dir("${env.WORKSPACE}") { 
                stage('Run UI Integration Tests') { 
                  def testOpts = [ tenant: tenant,
                                   folioUser: tenant + '_admin',
                                   folioPassword: 'admin',
                                   okapiUrl: okapiUrl ]
 
                    runIntegrationTests(testOpts,regressionDebugMode)
                }
              }
            }
          }  
        }  // env CHANGE_ID
      }  // end try
      catch (Exception err) {
        currentBuild.result = 'FAILED'
        println(err.getMessage());
        echo "Build Result: $currentBuild.result"
        throw err
      }
      finally {
        // publish junit tests if available
        junit allowEmptyResults: true, testResults: 'artifacts/runTest/*.xml'

        // publish lcov coverage html reports if available
        publishHTML([allowMissing: true, alwaysLinkToLastBuild: false,
                    keepAll: true, reportDir: 'artifacts/coverage/lcov-report',
                    reportFiles: 'index.html',
                    reportName: 'LCov Coverage Report',
                    reportTitles: 'LCov Coverage Report'])


        sendNotifications currentBuild.result
      }
    } // end timeout
  } // end node
    
} 


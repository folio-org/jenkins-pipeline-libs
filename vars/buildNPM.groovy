#!/usr/bin/env groovy

/*
 * Main build script for NPM-based FOLIO projects
 *
 * Configurable parameters: 
 *
 * buildNode: label of jenkin's slave build node to use
 * doDocker:  Build, test, and publish Docker image via 'buildDocker' (Default: 'no')
 * modDescriptor: path to standalone Module Descriptor file (Optional)
 * npmDeploy: Publish NPM artifacts to NPM repository (Default: 'yes')
 * publishApi: Publish API/RAML documentation.  (Default: 'no')
 * publishModDescriptor:  POST generated module descriptor to FOLIO registry (Default: 'no')
 * regressionDebugMode:  Enable extra debug logging in regression tests (Default: false)
 * runDupeCheck: check stripes framework for duplicate stripes-* dependencies. (Default: false)
 * runLint: Run ESLint via 'yarn lint' (Default: 'no')
 * runRegression (DISABLED) : Run UI regression module tests for PRs - 'yes' or 'no' (Default: 'no') 
 * runScripts: A "collection" of script commands and script arguments.  (Default: [])
 * runSonarqube: Run the Sonarqube scanner and generate reports on sonarcloud.io (Default: 'no')
 * runTest: Run unit tests via 'yarn test' (Default: 'no')
 * runTestOptions:  Extra opts to pass to 'yarn test'
 * stripesPlatform (DISABLED): Map consisting of modules's stripes platform and branch (Default: []) 
*/


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def foliociLib = new org.folio.foliociCommands()
  
  // default is to deploy to npm repo when branch is master
  // Note: this doesn't really work.  If set to boolean false, 
  // then the default boolean true will be set.  Set as string
  // 'false' or 'no'
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

  // default runDupeCheck
  def runDupeCheck = config.runDupeCheck ?: false

  // default mod descriptor
  def modDescriptor = config.modDescriptor ?: ''

  // default Stripes platform.  Empty Map
  // disabled for now
  def Map stripesPlatform = config.stripesPlatform ?: [:]

  // run NPM script.  An empty Map
  def runScripts = config.runScripts ?: []

  // use the smaller nodejs build node since most 
  // Nodejs builds are Stripes.
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
                                                       trackingSubmodules: false],
                                               [$class: 'RelativeTargetDirectory',
                                                       relativeTargetDir: 'project' ]],
                 userRemoteConfigs: scm.userRemoteConfigs
          ])

          echo "Checked out branch: $env.BRANCH_NAME"
        }

        dir("${env.WORKSPACE}/project") {
          stage('Set Environment') {
            if (npmDeploy) {
              echo "npmDeploy is true"
            }
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

              if (runDupeCheck) {
                checkStripesDupes('yarn.lock')
              }

              // Stage 'Run NPM scripts' - as parallel jobs
              // Hints: https://issues.jenkins-ci.org/browse/JENKINS-38268
              if (runScripts.size() >= 1) { 
                def scriptJobs = [:]
                runScripts.each { 
                  it.each { 
                    scriptName, scriptArgs -> scriptJobs[it] = { runNPMScript(scriptName,scriptArgs) }
                  }
                }
                parallel scriptJobs
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
                     "> artifacts/md/${env.folioName}.json"
                  modDescriptor = "${env.WORKSPACE}/project/artifacts/md/${env.folioName}.json"
                }
              } 

              // interface dep check.  releases only for now.
              if (env.isRelease) {
                stage('Dependency Check') {
                  echo "Checking mod descriptor dependencies"
                  okapiModDepCheck(modDescriptor)
                }
              }


              if (( env.BRANCH_NAME == 'master' ) ||  ( env.isRelease )) {
                if (publishModDescriptor) {
                  // We assume that MDs are included in package.json
                  stage('Publish Module Descriptor') {
                    echo "Publishing Module Descriptor to FOLIO registry"
                    postModuleDescriptor(modDescriptor)
                  }
                }
                if (npmDeploy) {
                  stage('NPM Publish') {
                    // do some clean up before publishing package
                    // .gitignore should cover 'artifacts'
                    // sh 'rm -rf node_modules artifacts ci'
                    sh 'rm -rf node_modules ci'
               
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
          // post MD to okapiUrl if PR. FOLIO-1948
          if ((env.CHANGE_ID) && (stripesPlatform != null)) { 
            // get okapi token. Post MD to okapiUrl
            writeFile file: 'getOkapiToken.sh', text: libraryResource('org/folio/getOkapiToken.sh')
            sh 'chmod +x getOkapiToken.sh'
            env.OKAPI_TOKEN = sh(returnStdout: true,
                 script: "./getOkapiToken.sh -o $env.okapiUrl -t supertenant -u super_admin -p admin").trim()

            def modDescriptorVar = readFile modDescriptor
             
            httpRequest acceptType: 'APPLICATION_JSON_UTF8', 
                        contentType: 'APPLICATION_JSON_UTF8', 
                        customHeaders: [[maskValue: true, name: 'X-Okapi-Token', value: env.OKAPI_TOKEN], 
                                 [maskValue: false, name: 'X-Okapi-Tenant', value: 'supertenant']], 
                        httpMode: 'POST', 
                        url: env.okapiUrl + '/_/proxy/modules',
                        consoleLogResponseBody: true,
                        requestBody: modDescriptorVar
             
          }
          
        } // end dir

        // actions specific to PRs
        if (env.CHANGE_ID) {
         
          // ensure tenant id is unique
          // def tenant = "${env.BRANCH_NAME}_${env.BUILD_NUMBER}"
          def tenant = "${env.projectName}_${env.CHANGE_ID}_${env.BUILD_NUMBER}"
          tenant = foliociLib.replaceHyphen(tenant)
          def stripesHost = "${env.projectName}-${env.CHANGE_ID}"
        
          if (stripesPlatform != null) { 
            dir("${env.WORKSPACE}/$stripesPlatform.repo") {
              stage('Build Stripes Platform') {
                git branch: stripesPlatform.branch, 
                    url: "https://github.com/folio-org/${stripesPlatform.repo}"
                buildStripesPlatformPr(env.okapiUrl,tenant)  
                // update stripes-install.json 
                sh "sed -i 's/${env.folioName}-[0-9.]\\+/${env.folioName}-${env.version}/' stripes-install.json"

              }
            }
            stage('Deploy Tenant') {
              dir("${env.WORKSPACE}/$stripesPlatform.repo") {
                // create tenant
                def tenantJson = "{\"id\":\"${tenant}\"}"
                httpRequest acceptType: 'APPLICATION_JSON_UTF8', 
                            contentType: 'APPLICATION_JSON_UTF8', 
                            customHeaders: [[maskValue: true, name: 'X-Okapi-Token', value: env.OKAPI_TOKEN],
                                            [maskValue: false, name: 'X-Okapi-Tenant', value: 'supertenant']],
                            consoleLogResponseBody: true,
                            httpMode: 'POST',
                            requestBody: tenantJson, 
                            url: env.okapiUrl + '/_/proxy/tenants'

                // enable okapi module for tenant
                httpRequest acceptType: 'APPLICATION_JSON_UTF8', 
                            contentType: 'APPLICATION_JSON_UTF8', 
                            customHeaders: [[maskValue: true, name: 'X-Okapi-Token', value: env.OKAPI_TOKEN],
                                            [maskValue: false, name: 'X-Okapi-Tenant', value: 'supertenant']],
                            consoleLogResponseBody: true,
                            httpMode: 'POST',
                            requestBody: '{"id":"okapi"}', 
                            url: env.okapiUrl + '/_/proxy/tenants/' + tenant + '/modules'

                // enable backend modules for tenant.  deploy if needed 
                def okapiInstallJson = readFile './okapi-install.json'
                httpRequest acceptType: 'APPLICATION_JSON_UTF8',
                            contentType: 'APPLICATION_JSON_UTF8',
                            customHeaders: [[maskValue: true, name: 'X-Okapi-Token', value: env.OKAPI_TOKEN],
                                            [maskValue: false, name: 'X-Okapi-Tenant', value: 'supertenant']],
                            httpMode: 'POST',
                            url: env.okapiUrl + '/_/proxy/tenants/' + tenant + '/install?deploy=true&tenantParameters=loadReference%3Dtrue%2CloadSample%3Dtrue',
                            consoleLogResponseBody: true,
                            requestBody: okapiInstallJson

                // enable frontend modules for tenant.  deploy if needed 
                def stripesInstallJson = readFile './stripes-install.json'
                httpRequest acceptType: 'APPLICATION_JSON_UTF8',
                            contentType: 'APPLICATION_JSON_UTF8',
                            customHeaders: [[maskValue: true, name: 'X-Okapi-Token', value: env.OKAPI_TOKEN],
                                            [maskValue: false, name: 'X-Okapi-Tenant', value: 'supertenant']],
                            httpMode: 'POST',
                            url: env.okapiUrl + '/_/proxy/tenants/' + tenant + '/install?tenantParameters=loadReference%3Dtrue%2CloadSample%3Dtrue',
                            consoleLogResponseBody: true,
                            requestBody: stripesInstallJson
                
              }
              dir("${env.WORKSPACE}/folio-infrastructure") { 
                checkout([$class: 'GitSCM', branches: [[name: '*/master']],
                          doGenerateSubmoduleConfigurations: false,
                          extensions: [[$class: 'SubmoduleOption',
                                     disableSubmodules: false,
                                     parentCredentials: false,
                                     recursiveSubmodules: true,
                                     reference: '',
                                     trackingSubmodules: true]],
                          submoduleCfg: [],
                          userRemoteConfigs: [[credentialsId: 'folio-jenkins-github-token',
                                             url: 'https://github.com/folio-org/folio-infrastructure']]])
              }
              dir("${env.WORKSPACE}/folio-infrastructure/CI/ansible") { 
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                                  accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                  credentialsId: 'jenkins-aws',
                                  secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {

                  ansiblePlaybook (credentialsId: '11657186-f4d4-4099-ab72-2a32e023cced',
                                   installation: 'Ansible',
                                   inventory: 'inventory',
                                   playbook: 'folioci-pr.yml',
                                   sudoUser: null, 
                                   vaultCredentialsId: 'ansible-vault-pass',
                                   extraVars: [okapi_url: "$env.okapiUrl", 
                                               tenant: "$tenant"])

                }
              } 
            } // end 'deploy tenant'

            // run UI module integration tests
            if (runRegression) {
              dir("${env.WORKSPACE}/project") {
                stage('Run UI Integration Tests') {
 
                  // need to run 'yarn install' again
                  sh 'yarn install'

                  def testOpts = [ tenant: tenant,
                                   folioUser: tenant + '_admin',
                                   folioPassword: 'admin',
                                   okapiUrl: env.okapiUrl ]

                  def testStatus = runIntegrationTests(testOpts,regressionDebugMode)
                  if (testStatus == 'FAILED') {
                    // don't fail. just report for now. 
                    echo "There are UI integration test failures" 
                  }
                }
              }
            }

            stage('Deploy Stripes Bundle') { 
              // get host where stripes is running
              withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                                  accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                  credentialsId: 'jenkins-aws',
                                  secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {

                dir("${env.WORKSPACE}/folio-infrastructure/CI/ansible") {
                  ansiblePlaybook (credentialsId: '11657186-f4d4-4099-ab72-2a32e023cced',
                                   disableHostKeyChecking: true,
                                   installation: 'Ansible',
                                   inventory: 'inventory',
                                   playbook: 'stripes-s3.yml',
                                   sudoUser: null,
                                   vaultCredentialsId: 'ansible-vault-pass',
                                   extraVars: [ s3_bucket: "$stripesHost",
                                                s3_tags: "{\"Pr\":\"${env.projectName}-${env.CHANGE_ID}\"}",
                                                stripes_path: "${env.WORKSPACE}/${stripesPlatform.repo}/output" ])
                }
              } // end withCredentials
              def githubSummary = "Bundle deployed for tenant, ${tenant}," + 
                                  "to https://${stripesHost}.s3.amazonaws.com/index.html - " + 
                                  "Additional information at https://dev.folio.org/guides/branch-preview/"
              @NonCPS
              def comment = pullRequest.comment(githubSummary)
            } // end stage

          } // end if 
        }  // env CHANGE_ID

      }  // end try
      catch (Exception err) {
        currentBuild.result = 'FAILED'
        println(err.getMessage());
        echo "Build Result: $currentBuild.result"
        throw err
      }
      finally {
        dir("${env.WORKSPACE}") {
          // publish junit tests if available
          junit allowEmptyResults: true, testResults: 'project/artifacts/runTest/*.xml'

          // publish lcov coverage html reports if available
          publishHTML([allowMissing: true, alwaysLinkToLastBuild: false,
                       keepAll: true, reportDir: 'project/artifacts/coverage/lcov-report',
                       reportFiles: 'index.html',
                       reportName: 'LCov Coverage Report',
                       reportTitles: 'LCov Coverage Report'])
        }
        sendNotifications currentBuild.result
      }
    } // end timeout
  } // end node
    
} 


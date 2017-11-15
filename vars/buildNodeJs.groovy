#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def foliociLib = new org.folio.foliociCommands()

  node('jenkins-slave-all') {

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

         echo "Checked out $env.BRANCH_NAME"
      }

      stage('Prep') {
        // right now, all builds are snapshots
        def Boolean snapshot = true

        if (snapshot == true) {
          npmSnapshotVersion()
        }

        def Map simpleNameVerMap = npmSimpleNameVersion('package.json')          
        simpleNameVerMap.each { key, value ->
          env.simpleName = key
          env.version = value
        }
        echo "Package Simplfied Name: $env.simpleName"
        echo "Package Version: $env.version"

        // project name is the GitHub repo name and is typically
        // different from mod name specified in package.json
        env.project_name = getProjName()
        echo "Project Name: $env.project_name"
      }
 
      withCredentials([string(credentialsId: 'jenkins-npm-folioci',variable: 'NPM_TOKEN')]) {
        withNPM(npmrcConfig: 'jenkins-npm-folioci') {
          stage('NPM Build') {
          // We should probably use the --production flag at some pointfor releases
            sh 'yarn install' 
          }

          if (config.runLint ==~ /(?i)(Y|YES|T|TRUE)/) {
            stage('ESLint') {
              echo "Running ESLint..."
              def lintStatus = sh(returnStatus:true, script: 'yarn lint 2>/dev/null 1> lint.output')
              echo "Lint Status: $lintStatus"
              if (lintStatus != 0) {
                def lintReport =  readFile('lint.output')
                if (env.CHANGE_ID) {
                  // Requires https://github.com/jenkinsci/pipeline-github-plugin
                  // comment is response to API request in case we ever need it.
                  def comment = pullRequest.comment(lintReport)
                  echo "$comment"
                }
                else {
                  echo "$lintReport"
                }
              }
              else {
                echo "No lint errors found"
              }
            }
          }

          if (config.runTest ==~ /(?i)(Y|YES|T|TRUE)/) {
            stage('Unit Tests') {
              echo "Running unit tests..."
              sh 'yarn test'
            }
          }

          if ( env.BRANCH_NAME == 'master' ) {
            stage('NPM Deploy') {
              // npm is more flexible than yarn for this stage. 
              echo "Deploying NPM packages to Nexus repository"
                sh 'npm publish'
            }
          }

        }  // end withNPM
      }  // end WithCred    

      if (config.doDocker) {
        env_name = env.project_name
        stage('Docker Build') {
          echo "Building Docker image for $env.name:$env.version" 
          config.doDocker.delegate = this
          config.doDocker.resolveStrategy = Closure.DELEGATE_FIRST
          config.doDocker.call()
        }
      } 

      if ( env.BRANCH_NAME == 'master' ) {
        if (config.publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) {
          // We assume that MDs are included in package.json
          stage('Publish Module Descriptor') {
            echo "Publishing Module Descriptor to FOLIO registry"
            sh 'git clone https://github.com/folio-org/stripes-core'
            sh 'stripes-core/util/package2md.js --strict package.json > ModuleDescriptor.json'
            def modDescriptor = 'ModuleDescriptor.json'

            postModuleDescriptor(modDescriptor,env.simpleName,env.version) 
          }
        }
      } 

    }  // end try
    catch (Exception err) {
      currentBuild.result = 'FAILED'
      println(err.getMessage());
      echo "Build Result: $currentBuild.result"
      throw err
    }
    finally {
      sendNotifications currentBuild.result
    }

  } // end node
    
} 


#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node {

    try {
      stage('Checkout') {
        deleteDir()
        currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
        // sendNotifications 'STARTED'

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
         echo "Git URL: $env.GIT_REPO_URL"
         echo "Git Commit: $env.GIT_COMMIT"
         echo "Git Branch: $env.GIT_BRANCH"
         echo "Git Revision: $env.GIT_REVISION"
         echo "Change ID: $env.CHANGE_ID"
         echo "Change URL: $env.CHANGE_URL"
         echo "SCM branches: $scm.branches"
         echo "SCM userRemote: $scm.userRemoteConfigs"
         def scmUrl = scm.userRemoteConfigs[0].getUrl()
         echo "SCM URL: $scmUrl"
         def scmName = scm.userRemoteConfigs[0].getName()
         echo "getName: $scmName"
      }

    } // end try
    catch (Exception err) {
      currentBuild.result = 'FAILED'
      println(err.getMessage());
      echo "Build Result: $currentBuild.result"
      throw err
    
    }
    finally {
      // sendNotifications currentBuild.result
      
    }
  } //end node
    
} 


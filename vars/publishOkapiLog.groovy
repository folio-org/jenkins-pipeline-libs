#!/usr/bin/env groovy


/*
 * Collect the Okapi container log so that we can publish
 * it with CI job.   
 * 
 */

def call(String host) {
   writeFile file: 'collect-okapi-container-log.sh', 
             text: libraryResource('org/folio/collect-okapi-container-log.sh')

   sh 'chmod +x collect-okapi-container-log.sh'
   withCredentials([sshUserPrivateKey(credentialsId: '11657186-f4d4-4099-ab72-2a32e023cced', keyFileVariable: 'SSH_KEY')]) {
      sh "scp -i $SSH_KEY -o StrictHostKeyChecking=no collect-okapi-container-log.sh ubuntu@${host}:."
      echo "Collecting Okapi container log..."
      sh "ssh -i $SSH_KEY -o StrictHostKeyChecking=no ubuntu@${host} ./collect-okapi-container-log.sh"
      sh "scp -i $SSH_KEY -o StrictHostKeyChecking=no  ubuntu@${host}:./okapi.log.bz2 ."
   }
   publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
                keepAll: true, reportDir: '.',
                reportFiles: 'okapi.log.bz2',
                reportName: "Okapi Log",
                reportTitles: "Okapi Log"])
}


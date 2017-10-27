#!/usr/bin/env groovy

def call(String dockerImage, String checkCmd, String runArgs) {  
   
   def timeout = '2s'
   def retries = 2
   def cidFile = sh(returnStdout: true, script: 'date +%N').trim()
   def maxStartupWait = 40
   def health = ''

   try {
     echo "Testing $dockerImage image. Starting container.."
     echo "docker run -d --health-timeout=${timeout} --health-retries=${retries}  --health-cmd='${checkCmd}' --cidfile $cidFile $dockerImage $runArgs"
     //dockerRunStatus = sh(returnStatus: true, script: "docker run -d --health-timeout=${timeout} --health-retries=${retries} --health-cmd='${checkCmd}' --cidfile $cidFile $dockerImage $runArgs").trim()
      sh """
      docker run -d --health-timeout=${timeout} --health-retries=${retries} \
             --health-cmd='${checkCmd}' --cidfile $cidFile $dockerImage $runArgs || exit 1
      """

     for (i = 0; i <maxStartupWait; i++) {
       health = sh(returnStdout: true, script: 'docker inspect `cat "$cidFile"` | jq -r \".[].State.Health.Status\"').trim()
       echo "Current Status: $health"
       if (health == 'starting') {
         sleep 1
       }
       else {
         break
       } 
     }
   }
   catch(Exception err) {
       health = 'error'
       throw err
   }
   finally {
     sh "docker stop `cat $cidFile` || exit 0"
     sh "docker rm  `cat $cidFile` || exit 0"
     return health
   }
} 
   


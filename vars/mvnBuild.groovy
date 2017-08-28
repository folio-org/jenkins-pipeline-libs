#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()


  withMaven(jdk: 'OpenJDK 8 on Ubuntu Docker Slave Node',
            maven: 'Maven on Ubuntu Docker Slave Node',
            options: [junitPublisher(disabled: false,
            ignoreAttachments: false),
            artifactsPublisher(disabled: false)]) {

    sh 'mvn integration-test'
    }
  }

}
 
  

  

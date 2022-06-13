pipeline {
    agent any

    stages {
        stage('sonarqube quality test') {
            agent{
              docker{
                image "openjdk:11"
              }
            }
            steps {
                script{
                    withSonarQubeEnv(credentialsId: 'sonar_tocken') {
                        sh 'mvn clean verify sonar:sonar' 
                    }
                
                }
            }
        }
   }
}
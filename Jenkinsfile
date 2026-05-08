node {
    // checkout do código-fonte
    stage('Checkout') {
        checkout scm
    }
    // Build do projeto
    stage('Build') {
        echo 'Deploy da Vinheria'
    }
    // Deploy do projeto
    stage('Deploy') {
        sh "mkdir -p /tmp/vinheria; cp -r $WORKSPACE/* /tmp/vinheria/"
    }
}
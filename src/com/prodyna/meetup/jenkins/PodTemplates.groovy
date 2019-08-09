package com.prodyna.meetup.jenkins

public void maven(label, body) {
    podTemplate(label: label, serviceAccount: 'jenkins', yaml: """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: build
    image: fassmus/meetup-build-slave:v1.1.1
    command: ['cat']
    tty: true
    resources:
      limits:
        memory: "512Mi"
        cpu: "1"
      requests:
        memory: "128Mi"
        cpu: "0.1"
    volumeMounts:
      - name: jenkins-docker-cfg
        mountPath: /root/.docker
  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug-539ddefcae3fd6b411a95982a830d987f4214251
    imagePullPolicy: Always
    command:
    - /busybox/cat
    tty: true
    volumeMounts:
      - name: jenkins-docker-cfg
        mountPath: /kaniko/.docker
  volumes:
  - name: jenkins-docker-cfg
    projected:
      sources:
      - secret:
          name: regcred
          items:
            - key: .dockerconfigjson
              path: config.json
"""
    ) {
        body.call()
    }
}

return this
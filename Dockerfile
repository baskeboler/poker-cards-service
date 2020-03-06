FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/poker-app-0.0.1-SNAPSHOT-standalone.jar /poker-app/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/poker-app/app.jar"]

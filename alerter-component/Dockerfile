FROM amazoncorretto:11
COPY ./build/libs/alerter-component-*.jar /app.jar
EXPOSE 8080
HEALTHCHECK --interval=5m --timeout=3s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-jar","/app.jar"]

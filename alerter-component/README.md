# about
backend component that uses dynamoDB to communicate with ifttt integration

# dev
## run against local components

```
#start dynamodb local, create tables and streams, and run the api lambdas
(cd ../stelios-ifttt-service && make local-run)
#run postman for local endpoint


#after setting up a dummy account 'fake'
export AWS_PROFILE=fake

aws dynamodb --endpoint-url http://localhost:8000 list-tables

```

in another terminal:

```
./gradlew bootRun --args=--spring.profiles.active=local
```

## run against cloud
```
AWS_REGION=eu-west-2 AWS_PROFILE=someProfile ./gradlew bootRun --args=--TABLE_NAME=table-name
```
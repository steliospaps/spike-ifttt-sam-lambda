# about
backend component that uses dynamoDB to communicate with ifttt integration

# dev

```
#start dynamodb local, create tables and streams, and run the api lambdas
(cd ../stelios-ifttt-service && make local-run)
#run postman for local endpoint


#after setting up a dummy account 'fake'
export AWS_PROFILE=fake

aws dynamodb --endpoint-url http://localhost:8000 list-tables

```

in another term:

```
./gradlew bootRun
```

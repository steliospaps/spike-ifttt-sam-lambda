# about
backend component that uses dynamoDB to communicate with ifttt integration

# dev
```
#start dynamodb local
(cd ../stelios-ifttt-service && make local-init)

#after setting up a dummy account 'fake'
export AWS_PROFILE=fake

aws dynamodb --endpoint-url http://localhost:8000 list-tables

```

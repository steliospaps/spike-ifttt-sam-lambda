#!/bin/bash

TNAME="TriggersTable"
echo deleting table
aws dynamodb delete-table --table-name ${TNAME} --endpoint-url http://localhost:8000 > /dev/null 2>&1

echo creating table
# TODO: don't use this when creating dynamo db table in aws.  Need to specify --billing-mode PAY_PER_REQUEST and no --provisioned-throughput or ProvisionedThroughput
#https://stackoverflow.com/questions/37357397/how-to-create-dynamodb-global-secondary-index-using-aws-cli
aws dynamodb create-table \
    --table-name ${TNAME} \
    --attribute-definitions AttributeName=triggerId,AttributeType=S  \
    --key-schema AttributeName=triggerId,KeyType=HASH \
    --provisioned-throughput ReadCapacityUnits=50,WriteCapacityUnits=50 \
    --endpoint-url http://localhost:8000 >/dev/null
#!/bin/bash
for i in {1..100}
do 
	aws --endpoint-url http://localhost:8000 --profile=fake dynamodb update-item \
	 --table-name TriggersTable   --key '{"PK":{"S":"92429d82a41e93048-'$i'"}}'  \
	  --update-expression 'SET triggerEvents = :i'   --expression-attribute-values \
	  file://<(echo '{ ":i" : {"S":"[{\"seqNo\": 1,\"data\": {\"instrument_name\":\"someName\",\"price\":\"10000\",\"instrument\":\"epic\",\"meta\": {\"id\": \"14b9-1fd2-acaa-5df5\",\"timestamp\": 1383597267}}}]"}}');
done

import json
import os
import boto3
from aws_xray_sdk.core import xray_recorder
from aws_xray_sdk.core import patch_all
from boto3.dynamodb.conditions import Attr, And, Key, Or
from botocore.exceptions import ClientError
import time
import calendar
# import requests

patch_all()

table_name = os.environ['DYNAMO_TABLE']
#print(os.environ)
if(os.environ.get('AWS_SAM_LOCAL','false') == 'true'):
    # the endpoint has to match the name from docker ps
    # of an image running in the docker-network supplied to start-api
    print(f"local connect to table='{table_name}'")
    table=boto3.resource('dynamodb',endpoint_url="http://dynamodb-local:8000/").Table(table_name)
else:
    print(f"normal connect to table='{table_name}'")
    table=boto3.resource('dynamodb').Table(table_name)

def iftttError(code, error):
    """lambda response on error
    Parameters
    ----------
    code: int, required
        http error code
    """
    return {
            "statusCode": code,
            "body": json.dumps({
                "errors": [
                    {
                        "message":error
                    }
                ],
            }),
        }


def lambda_handler(event, context):
    """Sample pure Lambda function

    Parameters
    ----------
    event: dict, required
        API Gateway Lambda Proxy Input Format

        Event doc: https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-input-format

    context: object, required
        Lambda Context runtime methods and attributes

        Context doc: https://docs.aws.amazon.com/lambda/latest/dg/python-context-object.html

    Returns
    ------
    API Gateway Lambda Proxy Output Format: dict

        Return doc: https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html
    """

    # try:
    #     ip = requests.get("http://checkip.amazonaws.com/")
    # except requests.RequestException as e:
    #     # Send some context about this error to Lambda Logs
    #     print(e)

    #     raise e
    print(event)
    method=event['httpMethod']
    print(f"method={method}")
    print(f"table_name={table_name}")
    myTriggerType='prev_day_change' # TODO: get from path

    
    if method == "DELETE":
        #path=event['path']
        trigger_id=event['pathParameters']['trigger_id']
        print(f"triggerId={trigger_id}")

        try:
            #see https://boto3.amazonaws.com/v1/documentation/api/latest/reference/services/dynamodb.html#DynamoDB.Table.delete_item
            response = table.update_item(
                Key={'PK':f"TR#{myTriggerType}#{trigger_id}", "SK":f"TR#{myTriggerType}#{trigger_id}"},
                UpdateExpression="SET expiresOn = :val1",
                ExpressionAttributeValues={
                    ':val1': calendar.timegm(time.gmtime()),
                },
                ConditionExpression=And(
                    And(Attr('PK').exists(),Attr('expiresOn').not_exists()),
                    Attr('triggerType').eq(myTriggerType)),
            )
        except ClientError as e:
            print(f"clientError={e}")
            if e.response['Error']['Code']=='ConditionalCheckFailedException':
                return iftttError(404,"item not found")
            raise
        print(f"response={response}")
        return {
            "statusCode": 200,
            "body":"",
        }
        
    elif method == "POST":
        body=json.loads(event['body'])
        trigger_id=body['trigger_identity']
        limit = body.get('limit',50)
        print(f"triggerId={trigger_id}")
        
        ###########
        # for a PK TR#1 with events EV#1 EV#2 EV#N it will load:
        # TR#1,TR#1
        # TR#1,EV#N
        # TR#1,EV#N-1
        # ....
        # TR#1,EV#N-(limit-1)
        response = table.query(
            KeyConditionExpression=Key("PK").eq(f"TR#{myTriggerType}#{trigger_id}")
                #.__and__(Key("SK").begins_with(f"TR#{myTriggerType}#").__or__(Key("SK").begins_with(f"EV#")))
                #parked for now but how do I filter for keys begining with X or y? (probably with a query filter?)
                #TODO: filter query on SK, how do I do that?
                ,
            ScanIndexForward=False, #the latest X events + trigger (trigger sorts after events)
            Limit=limit + 1, #+1 for Trigger row
            ProjectionExpression="SK, triggerEvent, expiresOn",
        )
        #no need to itterate as we do not expect to filter out anything
        print(f"response={response}")
        items = response["Items"]
        if 0 == len(items) \
            or (not items[0]['SK'].startswith("TR#") )\
            or 'expiresOn' in items[0]:
            #brand new 
            print(f"inserting {trigger_id}")
            if 'triggerFields' not in body:
                return iftttError(400, "triggerFields missing from request")
            triggerFields=body['triggerFields']
            #todo validate trigger fields
            try:
                response = table.put_item(
                    Item={
                        'PK':f"TR#{myTriggerType}#{trigger_id}", 
                        "SK":f"TR#{myTriggerType}#{trigger_id}",
                        'triggerId': trigger_id,
                        #hacky string way to avoid having multiple columns
                        'triggerFields': json.dumps(triggerFields),
                        'triggerType': myTriggerType,
                    },
                    ConditionExpression=Or(
                        Attr('expiresOn').exists(),#previously deleted item # TODO: in this case we 'resurect' the old events. This should not happen
                        Attr('PK').not_exists() # brand new item
                    ),
                )
            except ClientError as e:
                print(f"clientError={e}")
                if e.response['Error']['Code']=='ConditionalCheckFailedException':
                    return iftttError(404,"item not found") # 
                raise
            print("response ",response)
            triggered=[]
        else:
            events = items[1:]
            print(f"found {events} ")
            #hacky string way to avoid having multiple columns
            #TODO: change this to use  a Map? (will allow to add without overwrite)
            #events = json.loads(item.get("triggerEvents","[]"))
            triggered= []
            now=calendar.timegm(time.gmtime())
            for event in events:
                if now< event.get('expiresOn',now+1):
                    triggered.append(json.loads(event['triggerEvent']))
                
        return {
            "statusCode": 200,
            "body": json.dumps({
                "data": triggered,
                # "location": ip.text.replace("\n", "")
            }),
        }
    else :
        return iftttError(400, f"unexpected httpMethod {method}")
import json
import os
import boto3
from aws_xray_sdk.core import xray_recorder
from aws_xray_sdk.core import patch_all
from boto3.dynamodb.conditions import Attr
from botocore.exceptions import ClientError
# import requests

patch_all()

table_name = os.environ['DYNAMO_TABLE']

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

    #print(os.environ)
    if(os.environ.get('AWS_SAM_LOCAL','false') == 'true'):
        # the endpoint has to match the name from docker ps
        # of an image running in the docker-network supplied to start-api
        print(f"local connect to table='{table_name}'")
        table=boto3.resource('dynamodb',endpoint_url="http://dynamodb-local:8000/").Table(table_name)
    else:
        print(f"normal connect to table='{table_name}'")
        table=boto3.resource('dynamodb').Table(table_name)

    
    if method == "DELETE":
        #path=event['path']
        trigger_id=event['pathParameters']['trigger_id']
        print(f"triggerId={trigger_id}")

        try:
            #see https://boto3.amazonaws.com/v1/documentation/api/latest/reference/services/dynamodb.html#DynamoDB.Table.delete_item
            response = table.delete_item(
                Key={'PK':trigger_id},
                ConditionExpression=Attr('PK').eq(trigger_id),
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
        print(f"triggerId={trigger_id}")

        response = table.get_item(
            Key={'PK':trigger_id},
            ProjectionExpression="triggerEvents"
        )
        print(f"response={response}")

        if "Item" not in response:
            #brand new 
            print(f"inserting {trigger_id}")
            if 'triggerFields' not in body:
                return iftttError(400, "triggerFields missing from request")
            triggerFields=body['triggerFields']
            #todo validate trigger fields
            response = table.put_item(
                Item={
                    'PK': trigger_id,
                    #hacky string way to avoid having multiple columns
                    'triggerFields': json.dumps(triggerFields),
                },
            )
            print("response ",response)
            triggered=[]
        else:
            item=response['Item']
            print(f"found {item} ")
            #hacky string way to avoid having multiple columns
            #TODO: change this to a se Map? (will allow to add without overwrite)
            events = json.loads(item.get("triggerEvents","[]"))
            triggered= []
            for event in events:
                #TODO: implement limit (not needed now becasue I expect only up to one events)
                triggered.append(event['data'])
                
        return {
            "statusCode": 200,
            "body": json.dumps({
                "data": triggered,
                # "location": ip.text.replace("\n", "")
            }),
        }
    else :
        return iftttError(400, f"unexpected httpMethod {method}")
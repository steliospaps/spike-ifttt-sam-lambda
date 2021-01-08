import json
import os
import boto3
from aws_xray_sdk.core import xray_recorder
from aws_xray_sdk.core import patch_all
from boto3.dynamodb.conditions import Attr
from botocore.exceptions import ClientError
from boto3.dynamodb.conditions import Key
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

#TODO make this into a library?

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


    
    try:
        #see https://boto3.amazonaws.com/v1/documentation/api/latest/reference/services/dynamodb.html#DynamoDB.Table.delete_item
        response = table.query(
            KeyConditionExpression=Key("PK").eq("INSTRUMENT") & Key("SK").begins_with("EPIC#"),
            ProjectionExpression="symbol, epic",
            #Key={'PK':"INSTRUMENT", "SK"},
            #ConditionExpression=Attr('PK').eq(Attr('SK')),
        )
    except ClientError as e:
        print(f"clientError={e}")
        raise
    print(f"response={response}")
    data=[]
    for item in sorted(response['Items'], key=lambda k: k['symbol']):
        data.append({
            'label':item['symbol'],
            'value':item['epic']
            })
    return {
        "statusCode": 200,
        "body":json.dumps({'data':data}),
    }
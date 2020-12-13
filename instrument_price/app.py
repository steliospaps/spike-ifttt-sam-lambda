import json
import os
import boto3

# import requests

def iftttError(code, error):
    """lambda response on error
    Parameters
    ----------
    code: int, required
        http error code
    """
    return {
            "statusCode": 200,
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
    table_name = os.environ['DYNAMO_TABLE']
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
        path=event['path']
        trigger_id=event['pathParameters']['trigger_id']
        print(f"triggerId={trigger_id}")
    
        response = table.delete_item(
            Key={'triggerId':trigger_id}
        )
        
        print("response ",response)
        return {
            "statusCode": 200,
            "body":"",
        }
        
    elif method == "POST":
        body=json.loads(event['body'])
        triggerId=body['trigger_identity']
        print(f"triggerId={triggerId}")
        
        response = table.put_item(
            Item={
                'triggerId': triggerId,
                'epic': body['triggerFields']['epic'],
            }
        )

        triggered={
            'instrument_name':"someName",
            'price':'10000',
            'instrument':'epic',
            "meta": {
                "id": "14b9-1fd2-acaa-5df5",
                "timestamp": 1383597267
            }
        }
        return {
            "statusCode": 200,
            "body": json.dumps({
                "data": [triggered],
                # "location": ip.text.replace("\n", "")
            }),
        }
    else :
        return iftttError(400, f"unexpected httpMethod {method}")
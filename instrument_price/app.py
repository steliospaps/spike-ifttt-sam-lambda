import json

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
    
    if method == "DELETE":
        path=event['path']
        trigger_id=event['pathParameters']['proxy']
        print(f"triggerId={trigger_id}")
        return {
            "statusCode": 200,
            "body":"",
        }
    elif method == "POST":
        body=json.loads(event['body'])
        triggerId=body['trigger_identity']
        print(f"triggerId={triggerId}")
        
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
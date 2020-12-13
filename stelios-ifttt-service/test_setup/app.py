import json
import os

# import requests


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

    api_key=os.environ['API_KEY'] # header IFTTT-Service-Key
    token = event['headers'].get("Ifttt-Service-Key","")
    ##poor man's auth
    if token!=api_key:
        print(f"'{api_key}'!='{token}'")
        return {
            "statusCode": 401,
            "body":'{"errors":[{"message":"invalid token"}]}',
        }
    return {
        "statusCode": 200,
        "body":json.dumps({
            "data": {
                "samples": {
                    "triggers": {
                        "instrument_price":{
                            "epic": "epic1",
                            "direction": "OVER",
                            "price":"10,000.00"
                        }
                    }
                }
            }
        })
    }

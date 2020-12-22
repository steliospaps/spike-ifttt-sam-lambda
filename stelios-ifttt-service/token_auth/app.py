import json
import os
#see https://github.com/brysontyrrell/Serverless-Hello-World/blob/master/hello-world/TestAuthorizerFunc/lambda_function.py
def generate_policy(principal_id, effect=None, resource=None):
    auth_response = {
        'principalId': principal_id
    }

    if effect and resource:
        auth_response['policyDocument'] = {
            'Version': '2012-10-17',
            'Statement': [
                {
                    'Action': 'execute-api:Invoke',
                    'Effect': effect,
                    'Resource': resource
                }
            ]
        }

    return auth_response


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

    token = event['authorizationToken']
    method_arn = event['methodArn']
    api_key=os.environ['API_KEY']
    print(f"Client token: {token}")
    print(f"Method ARN: {method_arn}")
    print(f"API_KEY: {api_key}")
    
    if token == api_key:
        return generate_policy(token, 'Allow', method_arn)
    else:
        raise Exception('Unauthorized')
    

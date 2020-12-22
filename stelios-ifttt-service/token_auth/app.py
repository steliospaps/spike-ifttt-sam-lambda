import json
import os
import re

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

    token = event['authorizationToken']
    method_arn = event['methodArn']# arn:aws:execute-api:eu-west-2:834656303304:qjjaxuozdh/Prod/GET/ifttt/v1/status
    api_key=os.environ['API_KEY']
    print(f"Client token: {token}")
    print(f"Method ARN: {method_arn}")
    print(f"API_KEY: {api_key}")
    everything = re.sub('/[^/]+/ifttt/v1/.*','/*/ifttt/v1/*',method_arn)
    print(f"permitted arn={everything}")
    if token == api_key:
        return generate_policy(token, 'Allow', everything)
    else:
        raise Exception('Unauthorized')
    

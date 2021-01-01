import json
import os
import re
import boto3
import base64
from aws_xray_sdk.core import xray_recorder
from aws_xray_sdk.core import patch_all

from botocore.exceptions import ClientError

patch_all()

api_key=None

def get_secret():
    global api_key
    if api_key is not None:
        return api_key
    is_local=os.environ.get('AWS_SAM_LOCAL',False)

    secret_name=os.environ['API_KEY_SECRET']
    print(f"secret_name={secret_name}")
    print(f"is_local={is_local}")

    session = boto3.session.Session()
    client = session.client(
        service_name='secretsmanager')
    try:
        print("calling secrets manager")
        get_secret_value_response = client.get_secret_value(
            SecretId=secret_name
        )
        #print(f"got_response {get_secret_value_response}")
    except ClientError as e:
        #print(f"exception e={e}")
        if e.response['Error']['Code'] == 'DecryptionFailureException':
            # Secrets Manager can't decrypt the protected secret text using the provided KMS key.
            # Deal with the exception here, and/or rethrow at your discretion.
            raise e
        elif e.response['Error']['Code'] == 'InternalServiceErrorException':
            # An error occurred on the server side.
            # Deal with the exception here, and/or rethrow at your discretion.
            raise e
        elif e.response['Error']['Code'] == 'InvalidParameterException':
            # You provided an invalid value for a parameter.
            # Deal with the exception here, and/or rethrow at your discretion.
            raise e
        elif e.response['Error']['Code'] == 'InvalidRequestException':
            # You provided a parameter value that is not valid for the current state of the resource.
            # Deal with the exception here, and/or rethrow at your discretion.
            raise e
        elif e.response['Error']['Code'] == 'ResourceNotFoundException':
            # We can't find the resource that you asked for.
            # Deal with the exception here, and/or rethrow at your discretion.
            raise e
    else:
        # Decrypts secret using the associated KMS CMK.
        # Depending on whether the secret is a string or binary, one of these fields will be populated.
        #print("in else")
        if 'SecretString' in get_secret_value_response:
            secret = get_secret_value_response['SecretString']
            api_key=json.loads(secret)["service-api-key"]
        else:
            print("ERROR: did not expect binary")
            raise Exception("bad secret")
        return api_key

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
    print(f"Client token: {token}")
    print(f"Method ARN: {method_arn}")
    api_key=get_secret()
    print(f"api_key: {api_key}")
    everything = re.sub('/[^/]+/ifttt/v1/.*','/*/ifttt/v1/*',method_arn)
    print(f"permitted arn={everything}")
    if token == api_key:
        return generate_policy(token, 'Allow', everything)
    else:
        raise Exception('Unauthorized')
    

import json
import os

import requests
import http.client as http_client
import logging
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
    if is_local:
        return secret_name
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

    
    print("env=",os.environ)
    is_local=os.environ.get('AWS_SAM_LOCAL',False)
    do_not_send=os.environ.get('DO_NOT_SEND',"False").lower() in ("true","1")

    print("event=",event)

    triggerIds=set()

    #TODO deal with prev_day_change (it will appear as a new TR#prev_day_change#<triggerId>,EV#<timestamp> record)

    for record in event.get('Records',[]):
        event_id=record['eventID']
        if record['eventName'] == "MODIFY":
            neo = record['dynamodb']['NewImage'] #should always be there for MODIFY
            old = record['dynamodb']['OldImage'] #should always be there for MODIFY
            if neo['PK']['S'].startswith("TR#"):
                if neo['PK']['S'] == neo.get("SK",{}).get("S",""):
                    if 'triggerEvents' in neo:
                        if 'triggerId' in neo:
                            if neo['triggerEvents'].get("S","") != \
                                old.get('triggerEvents',{}).get("S",""):
                                print(f"will alert for {neo['PK']}")
                                triggerIds.add(neo['triggerId']['S'])
                            else:
                                print(f"ignoring event_id={event_id} because it has no triggerEvents changes")
                        else:
                            print(f"ignoring event_id={event_id} because it has no triggerId changes")
                    else:
                        print(f"ignoring event_id={event_id} because it has no triggerEvents")
                else:
                    print(f"ignoring event_id={event_id} because PK!=SK")
            else:
                print(f"ignoring event_id={event_id} because PK does not start with 'TR#'")
        else:
            print(f"ignoring event_id={event_id} because it is not MODIFY")

    toSend=[]
    for tr in triggerIds:
        toSend.append({
            'trigger_identity':tr
        })
    if len(toSend)>0:
        #print(f"will alert trigger_id={trigger_id} event_id={event_id} api_key={api_key}");
        #IFTTT-Service-Key: WlWFGKXFsXBaFMt8yZ7aLOafdqo7mAhY
        #https://realtime.ifttt.com/v1/notifications

        api_key=get_secret()

        body={"data":toSend}
        bodyStr=json.dumps(body)
        if is_local or do_not_send:
            print(f"would have sent alerts body={bodyStr} event_id={event_id} api_key={api_key}")
        else:
    #verbose logging
            if True:
                #https://stackoverflow.com/questions/10588644/how-can-i-see-the-entire-http-request-thats-being-sent-by-my-python-application
                http_client.HTTPConnection.debuglevel = 1
                # You must initialize logging, otherwise you'll not see debug output.
                logging.basicConfig()
                logging.getLogger().setLevel(logging.DEBUG)
                requests_log = logging.getLogger("requests.packages.urllib3")
                requests_log.setLevel(logging.DEBUG)
                requests_log.propagate = True
            print(f"sending alerts body={bodyStr} event_id={event_id} api_key={api_key}")
            res = requests.post(url="https://realtime.ifttt.com/v1/notifications",
            data=bodyStr,
            headers={
                'IFTTT-Service-Key': api_key,
                'Content-Type': 'application/json',
                'X-Request-ID': event_id,
                'Accept': 'application/json',
                'Accept-Charset': 'utf-8',
                'Accept-Encoding': 'gzip, deflate',
            })
            print(f"result={res}")
    else:
        print("nothing to send")
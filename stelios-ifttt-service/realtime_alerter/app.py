import json
import os

import requests


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
    api_key=os.environ['API_KEY']
    is_local=os.environ.get('AWS_SAM_LOCAL',False)
    print("event=",event)

    toSend=[]
    for record in event.get('Records',[]):
        event_id=record['eventID']
        if record['eventName'] == "MODIFY":
            neo = record['dynamodb']['NewImage'] #should always be there for MODIFY
            old = record['dynamodb']['OldImage'] #should always be there for MODIFY
            if 'triggerEvents' in neo:
                if neo['triggerEvents'].get("S","") != \
                    old.get('triggerEvents',{}).get("S",""):
                    print(f"will alert for {neo['triggerId']}")
                    toSend.append({
                        'trigger_identity':neo['triggerId']['S']
                    })
                else:
                    print(f"ignoring event_id={event_id} because it has no triggerEvents changes")
            else:
                print(f"ignoring event_id={event_id} because it has no triggerEvents")
        else:
            print(f"ignoring event_id={event_id} because it is not MODIFY")

    if len(toSend)>0:
        #print(f"will alert trigger_id={trigger_id} event_id={event_id} api_key={api_key}");
        #IFTTT-Service-Key: WlWFGKXFsXBaFMt8yZ7aLOafdqo7mAhY
        #https://realtime.ifttt.com/v1/notifications
        body={"data":toSend}
        if is_local:
            print(f"would have sent alert trigger_id={body} event_id={event_id} api_key={api_key}")
        else:
            print(f"sending alert trigger_id={body} event_id={event_id} api_key={api_key}")
            res = requests.post(url="https://realtime.ifttt.com/v1/notifications",
            data=body,
            headers={
                'IFTTT-Service-Key': api_key,
                'Content-Type': 'application/json',
                'X-Request-ID': event_id,
            })
            print(f"result={res}")
    else:
        print("nothing to send")
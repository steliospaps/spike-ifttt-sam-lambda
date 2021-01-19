# architecture
![architecture](./doc/architecture.png)
# dynamoDB Schema

Note: The TTL field is named expiresOn.

| entity | PK | SK | other fields
|---|---|---|---
|trigger|TR#\<triggerType\>#\<triggerID\>|(same as PK)|triggerId:S, <br> triggerFields:S(stringifiedJson), <br> triggerEvents:S(StringifiedJson), <br> triggerType(instrument_price\|prev_day_change), <br> expiresOn(if trigger deleted: only for triggerType=prev_day_change)
|trigger event|TR#<triggerType\>#\<triggerID\>|EV#\<eventId(timestamp)\>|triggerEvent:S(StringifiedJson),<br> expiresOn (1 day)
|instrument|INSTRUMENT|EPIC#\<epic\>|epic:S, description:S, symbol:S

An introduction to dynamoDB can be found [here](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Introduction.html)

Partition and sort key best practices can be found [here](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/bp-sort-keys.html)

# requirements
you need:
- copilot cli
- aws cli
- git
- mvn
- java11+
- make
- sam cli
- docker
- python3 (and pip)

## Windows (wsl)
Assuming git and make installed:

```
sudo apt install maven docker.io docker-compose awscli python3-pip
```
Follow instructions [to install sam cli](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install-linux.html) in particular about installing brew

```
brew install aws/tap/aws-sam-cli
brew install aws/tap/copilot-cli
```
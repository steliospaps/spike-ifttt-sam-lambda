
# about
![architecture](./doc/architecture.png)
# dynamoDB Schema
| entity | PK | SK | other fields
|---|---|---|---
|trigger|TR#\<triggerID\>|TR#\<triggerID\>|triggerId:S, triggerFields:S(stringifiedJson), triggerEvents:S(StringifiedJson)
|insrtrument|INSTRUMENT|EPIC#\<epic\>|epic:S, description:S, symbol:S

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
sudo apt install docker.io docker-compose awscli python3-pip
```
Follow instructions (to install sam cli)[https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install-linux.html]

```
cd stelios-fttt-service
make local-run
```
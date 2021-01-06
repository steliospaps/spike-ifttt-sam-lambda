
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

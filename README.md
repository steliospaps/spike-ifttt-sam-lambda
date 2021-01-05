
# about
![architecture](./doc/architecture.png)
# dynamoDB Schema (proposal)
| entity | PK | SK | other fields
|---|---|---|---
|trigger|TR#\<triggerID\>|TR#\<triggerID\>|triggerId:S, triggerFields:S(stringifiedJson), triggerEvents:S(StringifiedJson)
|insrtrument|INSTRUMENT|EPIC#\<epic\>|epic:S, name:S,

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

#https://gist.github.com/prwhite/8168133
# Add the following 'help' target to your Makefile
# And add help text after each target name starting with '\#\#'
 
help:           ## Show this help.
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'

.PHONY: clean 

clean: ## clean all generated artifacts and destroy local infrastructure
	for i in alerter-component stelios-ifttt-service; do (cd $$i && make clean);done 

# TODO: add a target to deploy everything. (perhaps init a deployment file and then deploy?)
# 		  inputs:
#			    Prefix:
#						affects: 
#              - copilot app (how it looks like everything is hardcoded in copilot files, no way to pass things during initialisation)
#              - sam stack name (this has to be fed back to the copilot app)
#              - name of secrets 
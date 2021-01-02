#https://gist.github.com/prwhite/8168133
# Add the following 'help' target to your Makefile
# And add help text after each target name starting with '\#\#'
 
help:           ## Show this help.
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'

.PHONY: clean 

clean: ## clean all generated artifacts and destroy local infrastructure
	for i in alerter-component stelios-ifttt-service; do (cd $$i && make clean);done 
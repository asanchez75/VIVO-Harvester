#!/bin/bash

# Copyright (c) 2010-2011 Christopher Haines, Dale Scheppler, Nicholas Skaggs, Stephen V. Williams, James Pence, Michael Barbieri.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the new BSD license
# which accompanies this distribution, and is available at
# http://www.opensource.org/licenses/bsd-license.html
# 
# Contributors:
#     Christopher Haines, Dale Scheppler, Nicholas Skaggs, Stephen V. Williams, James Pence, Michael Barbieri - initial API and implementation

# set to the directory where the harvester was installed or unpacked
# HARVESTER_INSTALL_DIR is set to the location of the installed harvester
#       If the deb file was used to install the harvester then the
#       directory should be set to /usr/share/vivo/harvester which is the
#       current location associated with the deb installation.
#       Since it is also possible the harvester was installed by
#       uncompressing the tar.gz the setting is available to be changed
#       and should agree with the installation location
export HARVESTER_INSTALL_DIR=/usr/share/vivo/harvester
export HARVEST_NAME=example-courses
export DATE=`date +%Y-%m-%d'T'%T`

# Add harvester binaries to path for execution
# The tools within this script refer to binaries supplied within the harvester
#       Since they can be located in another directory their path should be
#       included within the classpath and the path environment variables.
export PATH=$PATH:$HARVESTER_INSTALL_DIR/bin
export CLASSPATH=$CLASSPATH:$HARVESTER_INSTALL_DIR/bin/harvester.jar:$HARVESTER_INSTALL_DIR/bin/dependency/*
export CLASSPATH=$CLASSPATH:$HARVESTER_INSTALL_DIR/build/harvester.jar:$HARVESTER_INSTALL_DIR/build/dependency/*
rm -rf analytics.txt


echo "Total number of Courses in VIVO:  " `harvester-jenaconnect -j vivo.model.xml -q "SELECT  count(?URI)  WHERE  {?URI <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>  <http://vivo.ufl.edu/ontology/vivo-ufl/Course>. }" | sed -n '4,4p'`  >> analytics.txt
echo "Total number of CoursesSecton in VIVO:  " `harvester-jenaconnect -j vivo.model.xml -q "SELECT  count(?URI)  WHERE  {?URI <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>  <http://vivo.ufl.edu/ontology/vivo-ufl/CourseSection>. }" | sed -n '4,4p'`  >> analytics.txt
echo "Total number of people in VIVO:  " `harvester-jenaconnect -j vivo.model.xml -q "SELECT count(?person) where { ?person <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://xmlns.com/foaf/0.1/Person>. }" | sed -n '4,4p'`  >> analytics.txt
echo "Total number of people with UFIDs:  " `harvester-jenaconnect -j vivo.model.xml -q "SELECT  count(?URI) WHERE { ?URI  <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://xmlns.com/foaf/0.1/Person>. ?URI <http://vivo.ufl.edu/ontology/vivo-ufl/ufid> ?UFID . }" | sed -n '4,4p'` >> analytics.txt
echo "Total number of people without UFIDs:  " `harvester-jenaconnect -j vivo.model.xml -q "SELECT  count(?u) WHERE { ?u <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://xmlns.com/foaf/0.1/Person> . OPTIONAL {?u <http://vivo.ufl.edu/ontology/vivo-ufl/ufid> ?y . } FILTER (!bound(?y)) }" | sed -n '4,4p'` >> analytics.txt

